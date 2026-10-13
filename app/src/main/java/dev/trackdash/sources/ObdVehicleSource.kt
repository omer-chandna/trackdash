package dev.trackdash.sources

import android.content.Context
import android.os.SystemClock
import dev.trackdash.core.*
import dev.trackdash.core.obd.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ObdDiagnostics(val state: String = "Disconnected", val ecu: String = "", val supported: Set<Int> = emptySet(),
    val latencyMillis: Long = 0, val errors: Int = 0, val queries: Long = 0, val protocol: String = "",
    val vin: String? = null, val vinStatus: String = "Not requested", val firmware: String = "")

class ObdVehicleSource(private val context: Context, private val address: String, private val bleCx: Boolean = false) : VehicleSource {
    private val mutableDiagnostics = MutableStateFlow(ObdDiagnostics())
    val diagnostics = mutableDiagnostics.asStateFlow()
    override fun samples(): Flow<VehicleSample> = flow {
        var retries = 0
        while (currentCoroutineContext().isActive) {
            var transport: ElmTransport? = null
            try {
                mutableDiagnostics.value = mutableDiagnostics.value.copy(state = "Connecting")
                val connection: ElmTransport = if (bleCx) CxBleTransport.connect(context, address) else RfcommTransport.connect(context, address)
                transport = connection
                val protocol = ElmProtocol(connection)
                mutableDiagnostics.value = mutableDiagnostics.value.copy(state = "Initializing")
                protocol.initialize()
                val firmware = connection.exchange("ATI").trim()
                val protocolName = connection.exchange("ATDP").trim()
                mutableDiagnostics.value = mutableDiagnostics.value.copy(state = "Discovering vehicle")
                val capabilities = protocol.discover()
                val ecu = capabilities.keys.sorted().firstOrNull() ?: error("No responding ECU")
                val supported = capabilities.getValue(ecu)
                val active = StandardPids.all.filter { it.code in supported }
                check(active.isNotEmpty()) { "No supported dashboard PIDs" }
                val vinResult = runCatching { VinDecoder.decode(connection.exchange("0902", 8000))[ecu] }
                vinResult.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                mutableDiagnostics.value = ObdDiagnostics("Connected", ecu, supported, protocol = protocolName,
                    vin = vinResult.getOrNull(), vinStatus = if (vinResult.getOrNull() != null) "Retrieved" else "Unavailable / unsupported framing", firmware = firmware)
                val readings = SensorId.entries.associateWith { sensor ->
                    SensorReading(sensor, null, SystemClock.elapsedRealtimeNanos(), SensorSource.OBD,
                        if (active.any { it.sensor == sensor }) SensorStatus.UNAVAILABLE else SensorStatus.UNSUPPORTED)
                }.toMutableMap()
                val due = active.associateWith { 0L }.toMutableMap()
                while (currentCoroutineContext().isActive) {
                    val pid = active.minBy { due.getValue(it) }
                    delay((due.getValue(pid) - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                    val began = SystemClock.elapsedRealtime()
                    val value = protocol.read(pid, ecu)
                    if (value != null) retries = 0
                    val now = SystemClock.elapsedRealtimeNanos()
                    readings[pid.sensor] = SensorReading(pid.sensor, value, now, SensorSource.OBD,
                        if (value == null) SensorStatus.UNAVAILABLE else SensorStatus.AVAILABLE)
                    due[pid] = SystemClock.elapsedRealtime() + pid.intervalMillis
                    val latency = SystemClock.elapsedRealtime() - began
                    mutableDiagnostics.value = mutableDiagnostics.value.copy(latencyMillis = latency, queries = mutableDiagnostics.value.queries + 1)
                    val fresh = readings.values.map { if (it.status == SensorStatus.AVAILABLE && now - it.elapsedRealtimeNanos > 5_000_000_000) it.copy(status = SensorStatus.STALE) else it }
                    emit(VehicleSample(fresh, OilPressureStatus.UNAVAILABLE, null, true, SensorSource.OBD, now))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableDiagnostics.value = mutableDiagnostics.value.copy(state = "Error: ${error.message}", errors = mutableDiagnostics.value.errors + 1)
                emit(VehicleSample(emptyList(), OilPressureStatus.UNAVAILABLE, null, false, SensorSource.OBD, SystemClock.elapsedRealtimeNanos()))
                retries++
                if (retries >= 3) throw error
                delay(1000L * retries)
            } finally { transport?.close() }
        }
    }.flowOn(Dispatchers.IO)
}

