package dev.trackdash.sources

import android.content.Context
import dev.trackdash.core.obd.ObdDiscovery
import dev.trackdash.core.obd.ObdDiscoveryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Stationary setup probe. A cancelled/failed scan never retains the Bluetooth connection. */
suspend fun discoverObdVehicle(context: Context, address: String, bleCx: Boolean): ObdDiscoveryResult = withContext(Dispatchers.IO) {
    withTimeout(120_000) {
        ObdDiscovery.connectAndScan {
            if (bleCx) CxBleTransport.connect(context, address) else RfcommTransport.connect(context, address)
        }
    }
}
