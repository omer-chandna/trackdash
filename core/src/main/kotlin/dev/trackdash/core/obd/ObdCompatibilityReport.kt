package dev.trackdash.core.obd

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Explicitly exported scan evidence, not a hardware qualification or a guarantee of protection. */
@Serializable
data class ObdCompatibilityReport(
    val schemaVersion: Int = 1,
    val qualification: String = "USER_REPORTED_NOT_VERIFIED",
    val observedAtMillis: Long,
    val transport: String,
    val protocolFamily: String,
    val selectedEcu: String?,
    val ecus: List<ObdEcuCapabilities>,
    val oilPressureMonitored: Boolean = false,
    val vehicleDescription: String = "",
    val adapterDescription: String = "",
)

object ObdCompatibilityReports {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    /** No VIN, address, profile identifiers, raw adapter replies, GPS, notes, or telemetry values. */
    fun encode(discovery: ObdDiscoveryResult, observedAtMillis: Long, bleCx: Boolean,
        vehicleDescription: String = "", adapterDescription: String = ""): String {
        val protocol = discovery.protocol.uppercase()
        val family = when {
            "15765" in protocol || "CAN" in protocol -> "ISO_15765_CAN"
            "9141" in protocol -> "ISO_9141"
            "14230" in protocol || "KWP" in protocol -> "ISO_14230_KWP"
            "J1850" in protocol -> "SAE_J1850"
            else -> "UNKNOWN"
        }
        return json.encodeToString(ObdCompatibilityReport(observedAtMillis = observedAtMillis,
            transport = if (bleCx) "OBDLINK_CX_BLE_EXPERIMENTAL" else "BLUETOOTH_CLASSIC_RFCOMM",
            protocolFamily = family, selectedEcu = discovery.selectedEcu, ecus = discovery.ecus,
            vehicleDescription = vehicleDescription.trim().take(120), adapterDescription = adapterDescription.trim().take(120)))
    }
}
