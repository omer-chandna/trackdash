package dev.trackdash.sources

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import dev.trackdash.core.obd.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID

/** OBDLink CX only. UUIDs and acknowledged writes follow the vendor's CX adapter notes. */
@SuppressLint("MissingPermission")
class CxBleTransport private constructor() : ElmTransport {
    private var gatt: BluetoothGatt? = null
    private lateinit var write: BluetoothGattCharacteristic
    private val events = Channel<String>(8)
    private val incoming = Channel<ByteArray>(64)
    private val mutex = Mutex()
    private fun fail(message: String) {
        val error = IOException(message)
        events.close(error); incoming.close(error)
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) fail("BLE disconnected ($status)")
            else if (newState == BluetoothProfile.STATE_CONNECTED) events.trySend("connected")
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) events.trySend("services") else fail("BLE service discovery failed ($status)")
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) events.trySend("subscribed") else fail("BLE subscription/pairing failed ($status)")
        }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) events.trySend("written") else fail("BLE write failed ($status)")
        }
        @Deprecated("Used on Android 10–12")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) receive(characteristic.uuid, characteristic.value)
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) { receive(characteristic.uuid, value) }
        private fun receive(uuid: UUID, value: ByteArray) {
            if (uuid == NOTIFY && incoming.trySend(value.copyOf()).isFailure) { fail("BLE receive buffer overflow"); gatt?.disconnect() }
        }
    }
    private suspend fun expect(event: String) { check(events.receive() == event) { "Unexpected BLE operation order" } }
    override suspend fun exchange(command: String, timeoutMillis: Long): String = mutex.withLock {
        ElmCommands.requireReadOnly(command)
        try { withTimeout(timeoutMillis) {
            val connection = requireNotNull(gatt)
            // Diagnostic commands fit the default MTU's 20-byte acknowledged-write payload.
            val bytes = (command + "\r").toByteArray(Charsets.US_ASCII)
            require(bytes.size <= 20)
            val accepted = if (Build.VERSION.SDK_INT >= 33) connection.writeCharacteristic(write, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
                else { write.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; write.value = bytes; connection.writeCharacteristic(write) }
            check(accepted) { "BLE write not accepted" }
            expect("written")
            val buffer = ElmResponseBuffer()
            var response: String? = null
            while (response == null) response = buffer.accept(incoming.receive())
            response
        } } catch (timeout: TimeoutCancellationException) { close(); throw IOException("BLE response timed out", timeout) }
        catch (error: Exception) { close(); throw error }
    }
    override fun close() {
        fail("BLE connection closed")
        gatt?.let { it.disconnect(); it.close() }; gatt = null
    }
    companion object {
        private fun uuid(value: String) = UUID.fromString("0000$value-0000-1000-8000-00805f9b34fb")
        val SERVICE: UUID = uuid("fff0")
        private val NOTIFY = uuid("fff1")
        private val WRITE = uuid("fff2")
        private val CCCD = uuid("2902")
        suspend fun connect(context: Context, address: String): CxBleTransport {
            val result = CxBleTransport()
            try { withTimeout(30_000) {
                val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: error("Bluetooth unavailable")
                check(adapter.isEnabled) { "Enable Bluetooth first" }
                result.gatt = adapter.getRemoteDevice(address).connectGatt(context.applicationContext, false, result.callback, BluetoothDevice.TRANSPORT_LE)
                result.expect("connected")
                val gatt = requireNotNull(result.gatt)
                check(gatt.discoverServices()); result.expect("services")
                val service = requireNotNull(gatt.getService(SERVICE)) { "Not an OBDLink CX UART service" }
                result.write = requireNotNull(service.getCharacteristic(WRITE))
                check(result.write.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) { "Acknowledged BLE writes unavailable" }
                val notification = requireNotNull(service.getCharacteristic(NOTIFY))
                check(gatt.setCharacteristicNotification(notification, true))
                val descriptor = requireNotNull(notification.getDescriptor(CCCD))
                val accepted = if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                    else { descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; gatt.writeDescriptor(descriptor) }
                check(accepted); result.expect("subscribed")
            }; return result }
            catch (timeout: TimeoutCancellationException) { result.close(); throw IOException("BLE connection/pairing timed out", timeout) }
            catch (error: Exception) { result.close(); throw error }
        }
    }
}
