package dev.trackdash.core.obd

import dev.trackdash.core.SensorId
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/** Advertised support and a successful read are deliberately different facts. */
@Serializable
enum class ObdSensorAvailability { AVAILABLE, UNAVAILABLE, UNSUPPORTED, UNKNOWN }

@Serializable
data class ObdSensorCapability(val sensor: SensorId, val pid: Int, val availability: ObdSensorAvailability)

@Serializable
data class ObdEcuCapabilities(
    val ecu: String,
    val supportedPids: Set<Int>,
    val supportedPages: Set<Int>,
    val sensors: List<ObdSensorCapability>,
) {
    val availableSensors: Set<SensorId> get() = sensors.filter { it.availability == ObdSensorAvailability.AVAILABLE }.map { it.sensor }.toSet()
    val supportedSensors: Set<SensorId> get() = sensors.filter { it.pid in supportedPids }.map { it.sensor }.toSet()
}

@Serializable
data class ObdDiscoveryResult(
    val selectedEcu: String?,
    val ecus: List<ObdEcuCapabilities>,
    val protocol: String,
    val firmware: String,
    val vin: String? = null,
    val vinStatus: String = "Unavailable / unsupported framing",
) {
    val selected: ObdEcuCapabilities? get() = ecus.firstOrNull { it.ecu == selectedEcu }
}

@Serializable
data class CachedObdCapabilities(val version: Int = 1, val checkedAtMillis: Long, val discovery: ObdDiscoveryResult)

/** Missing VIN is unknown, not a match. A known mismatch must never rewrite a profile's identity. */
fun vehicleIdentityMismatch(savedVin: String?, detectedVin: String?): Boolean =
    !savedVin.isNullOrBlank() && !detectedVin.isNullOrBlank() && !savedVin.trim().equals(detectedVin.trim(), ignoreCase = true)

/** Local profile cache only; never treated as current monitoring readiness. */
object ObdCapabilityCache {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(discovery: ObdDiscoveryResult, checkedAtMillis: Long = System.currentTimeMillis()): String =
        json.encodeToString(CachedObdCapabilities(checkedAtMillis = checkedAtMillis, discovery = discovery.copy(vin = null)))
    fun decode(value: String): CachedObdCapabilities? = runCatching {
        json.decodeFromString<CachedObdCapabilities>(value).takeIf { it.version == 1 }
    }.getOrNull()
}

object ObdDiscovery {
    /** Owns the transport, including failure/cancellation paths. Recording uses inspect on its open transport. */
    suspend fun connectAndScan(connect: suspend () -> ElmTransport): ObdDiscoveryResult {
        val transport = connect()
        return try { inspect(transport) } finally { transport.close() }
    }

    suspend fun inspect(transport: ElmTransport): ObdDiscoveryResult {
        val protocol = ElmProtocol(transport)
        protocol.initialize()
        val firmware = optional { transport.exchange("ATI").trim().take(160) } ?: "Unavailable"
        val protocolName = optional { transport.exchange("ATDP").trim().take(160) } ?: "Unavailable"
        val report = protocol.discoverCapabilities()
        require(report.supported.isNotEmpty()) { "No responding OBD-II ECU. Check ignition and adapter, then retry." }
        val readings = mutableMapOf<Pair<String, Int>, ObdSensorAvailability>()
        // One broadcast per PID, not one per ECU: preserve response attribution and keep setup bounded.
        StandardPids.all.filter { pid -> report.supported.values.any { pid.code in it } }.forEach { pid ->
            val result = runCatching { protocol.readAll(pid) }
            result.exceptionOrNull()?.let { if (it is CancellationException || it is IOException) throw it }
            report.supported.filterValues { pid.code in it }.keys.forEach { ecu ->
                readings[ecu to pid.code] = when {
                    result.isFailure -> ObdSensorAvailability.UNKNOWN
                    result.getOrThrow()[ecu]?.isFinite() == true -> ObdSensorAvailability.AVAILABLE
                    else -> ObdSensorAvailability.UNAVAILABLE
                }
            }
        }
        val ecus = report.supported.toSortedMap().map { (ecu, supported) ->
            val pages = report.pages.getValue(ecu)
            ObdEcuCapabilities(ecu, supported, pages, StandardPids.all.map { pid ->
                val page = (pid.code - 1) / 32 * 32
                ObdSensorCapability(pid.sensor, pid.code, when {
                    pid.code in supported -> readings.getValue(ecu to pid.code)
                    page in pages -> ObdSensorAvailability.UNSUPPORTED
                    // A missing continuation bit proves later pages unsupported; a missing reply does not.
                    (0 until page step 32).any { prior -> prior in pages && prior + 32 !in supported } -> ObdSensorAvailability.UNSUPPORTED
                    else -> ObdSensorAvailability.UNKNOWN
                })
            })
        }
        val selected = ecus.filter { it.availableSensors.isNotEmpty() }.sortedWith(
            compareByDescending<ObdEcuCapabilities> { SensorId.ENGINE_RPM in it.availableSensors }
                .thenByDescending { SensorId.COOLANT_TEMPERATURE in it.availableSensors }
                .thenByDescending { it.availableSensors.size }.thenBy { it.ecu },
        ).firstOrNull()
        val vins = optional { VinDecoder.decode(transport.exchange("0902", 8000)) }
        val vin = selected?.ecu?.let { vins?.get(it) }
        return ObdDiscoveryResult(selected?.ecu, ecus, protocolName, firmware, vin,
            if (vin == null) "Unavailable / unsupported framing" else "Retrieved")
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (transportFailure: IOException) { throw transportFailure }
        catch (_: Exception) { null }
}
