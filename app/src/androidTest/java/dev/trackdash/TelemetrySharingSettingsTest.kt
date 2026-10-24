package dev.trackdash

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.trackdash.recording.SharingSettings
import dev.trackdash.recording.TelemetrySharingPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelemetrySharingSettingsTest {
    @Test fun authorizationRoundTripsButIsNeverStoredAsPlaintext() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = context.getSharedPreferences("telemetry-sharing", Context.MODE_PRIVATE)
        val preferences = TelemetrySharingPreferences(context)
        // Return to the exact previous settings so this test cannot opt a later recording into sharing.
        val previous = preferences.settings.first()
        try {
            val settings = SharingSettings(true, "https://receiver.example/telemetry", "Bearer test-only-secret")
            preferences.save(settings)
            assertEquals(settings, preferences.settings.first())
            assertFalse(store.getString("authorization", "").orEmpty().contains("test-only-secret"))
            assertTrue(store.getString("authorization", "").orEmpty().isNotBlank())
            preferences.save(SharingSettings())
            assertEquals(SharingSettings(), preferences.settings.first())
            assertEquals("", store.getString("authorization", "missing"))
        } finally { preferences.save(previous) }
    }
}
