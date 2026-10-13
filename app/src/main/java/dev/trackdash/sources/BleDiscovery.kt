package dev.trackdash.sources

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class BleAdapter(val name: String, val address: String)
@SuppressLint("MissingPermission")
fun discoverCxAdapters(context: Context) = callbackFlow {
    val scanner = context.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner ?: error("Enable Bluetooth first")
    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            // UUIDs are not unique proof of model; expose only explicitly named CX devices.
            if (name.startsWith("OBDLink CX", ignoreCase = true)) trySend(BleAdapter(name, result.device.address))
        }
        override fun onScanFailed(errorCode: Int) { close(IllegalStateException("BLE scan failed ($errorCode)")) }
    }
    scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
    awaitClose { scanner.stopScan(callback) }
}
