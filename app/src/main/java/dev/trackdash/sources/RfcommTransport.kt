package dev.trackdash.sources

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import dev.trackdash.core.obd.ElmTransport
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.EOFException
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One socket/worker; cancellation closes the socket to unblock native reads/connect. */
class RfcommTransport private constructor(private val socket: BluetoothSocket) : ElmTransport {
    private val worker = Executors.newSingleThreadExecutor()
    private val mutex = Mutex()
    private suspend fun <T> socketCall(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { close() }
        worker.execute {
            try { val value = block(); if (continuation.isActive) continuation.resume(value) }
            catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
        }
    }
    override suspend fun exchange(command: String, timeoutMillis: Long): String = mutex.withLock {
        require(command in setOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH1", "ATSP0", "ATDP", "ATI") ||
            command.matches(Regex("01[0-9A-F]{2}")) || command == "0902") { "Read-only diagnostic commands only" }
        withTimeout(timeoutMillis) {
            socketCall {
                socket.outputStream.write((command + "\r").toByteArray(Charsets.US_ASCII))
                socket.outputStream.flush()
                val response = StringBuilder()
                while (true) {
                    val next = socket.inputStream.read()
                    if (next < 0) throw EOFException("Adapter disconnected")
                    if (next == '>'.code) break
                    check(response.length < 32_768) { "Adapter response too large" }
                    response.append(next.toChar())
                }
                response.toString()
            }
        }
    }
    override fun close() {
        try { socket.close() } catch (_: java.io.IOException) { /* Already disconnected. */ }
        finally { worker.shutdownNow() }
    }
    companion object {
        @SuppressLint("MissingPermission")
        suspend fun connect(context: Context, address: String): RfcommTransport {
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter ?: error("Bluetooth unavailable")
            check(adapter.isEnabled) { "Enable Bluetooth first" }
            val socket = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
            val transport = RfcommTransport(socket)
            try { withTimeout(10_000) { transport.socketCall { socket.connect() } }; return transport }
            catch (error: Exception) { transport.close(); throw error }
        }
    }
}
