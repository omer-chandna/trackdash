package dev.trackdash.recording

import dev.trackdash.core.SensorSource
import dev.trackdash.core.sharing.TelemetryEnvelope
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HttpTelemetrySenderTest {
    private val envelope = TelemetryEnvelope("dev.trackdash.telemetry", 1, 0, SensorSource.OBD, true, emptyList())

    private class FakeConnection(private val code: Int = 204, private val block: Boolean = false) : HttpURLConnection(URL("https://monitor.example/telemetry")) {
        val body = ByteArrayOutputStream()
        val headers = mutableMapOf<String, String>()
        val started = CountDownLatch(1)
        var disconnected = false
        var interrupted = false
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun connect() = Unit
        override fun setRequestProperty(key: String, value: String) { headers[key] = value }
        override fun getOutputStream() = body
        override fun getResponseCode(): Int {
            started.countDown()
            if (block) try { Thread.sleep(10_000) } catch (error: InterruptedException) { interrupted = true; throw error }
            return code
        }
    }

    @Test fun `HTTPS POST has bounded timeouts disables redirects and separates authorization`() = runBlocking {
        val connection = FakeConnection()
        HttpTelemetrySender("https://monitor.example/telemetry", "Bearer private-token", { connection }).send(envelope)
        assertEquals("POST", connection.requestMethod)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertEquals(2000, connection.connectTimeout)
        assertEquals(2000, connection.readTimeout)
        assertEquals("Bearer private-token", connection.headers["Authorization"])
        assertEquals("application/json; charset=utf-8", connection.headers["Content-Type"])
        assertTrue(connection.body.toString(Charsets.UTF_8.name()).contains("\"version\":1"))
        assertFalse(connection.body.toString(Charsets.UTF_8.name()).contains("private-token"))
        assertTrue(connection.disconnected)
    }

    @Test fun `redirect and server errors are rejected without surfacing URL or response content`() = runBlocking {
        for (code in listOf(302, 401, 429, 500)) {
            val connection = FakeConnection(code)
            var failure: Exception? = null
            try { HttpTelemetrySender("https://monitor.example/private-path", "Bearer secret", { connection }).send(envelope) }
            catch (error: Exception) { failure = error }
            assertEquals("Receiver rejected telemetry", failure?.message)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun `cancellation interrupts blocking network work and disconnects`() = runBlocking {
        val connection = FakeConnection(block = true)
        val job = launch { HttpTelemetrySender("https://monitor.example/telemetry", openConnection = { connection }).send(envelope) }
        yield()
        assertTrue(connection.started.await(2, TimeUnit.SECONDS))
        job.cancel()
        withTimeout(3000) { job.join() }
        assertTrue(connection.interrupted)
        assertTrue(connection.disconnected)
    }

    @Test fun `settings diagnostic string never reveals endpoint or secret`() {
        val display = SharingSettings(true, "https://monitor.example/private-path", "Bearer private-token").toString()
        assertFalse(display.contains("private-path"))
        assertFalse(display.contains("private-token"))
        assertTrue(display.contains("redacted"))
    }
}
