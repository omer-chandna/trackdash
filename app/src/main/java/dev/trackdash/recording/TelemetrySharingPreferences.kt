package dev.trackdash.recording

import android.content.Context
import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.trackdash.core.sharing.validateSharingEndpoint
import dev.trackdash.core.sharing.validateSharingAuthorization
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** Authorization is intentionally not serializable and never included in diagnostic strings. */
data class SharingSettings(val enabled: Boolean = false, val endpoint: String = "", val authorization: String = "") {
    override fun toString(): String = "SharingSettings(enabled=$enabled, endpoint=[redacted], authorization=[redacted])"
}

/** App-private settings; optional Authorization encrypted with a non-exportable Android Keystore key. */
class TelemetrySharingPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("telemetry-sharing", Context.MODE_PRIVATE)

    val settings: Flow<SharingSettings> = callbackFlow {
        fun publish() {
            try { trySend(read()) }
            catch (_: Exception) { close(IllegalStateException("Sharing settings could not be unlocked; save them again")) }
        }
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> publish() }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        publish()
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    @SuppressLint("UseKtx") // Check commit's Boolean result; the Unit-returning KTX helper would hide write failure.
    suspend fun save(settings: SharingSettings) = withContext(Dispatchers.IO) {
        if (settings.enabled || settings.endpoint.isNotBlank()) validateSharingEndpoint(settings.endpoint)
        validateSharingAuthorization(settings.authorization)
        val encrypted = if (settings.authorization.isEmpty()) "" else encrypt(settings.authorization)
        check(preferences.edit().putString("endpoint", settings.endpoint).putString("authorization", encrypted)
            .putBoolean("enabled", settings.enabled).commit()) { "Sharing settings could not be saved" }
    }

    private fun read(): SharingSettings {
        val endpoint = preferences.getString("endpoint", "").orEmpty()
        val encrypted = preferences.getString("authorization", "").orEmpty()
        return SharingSettings(preferences.getBoolean("enabled", false), endpoint,
            if (encrypted.isEmpty()) "" else decrypt(encrypted))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    private companion object { const val KEY_ALIAS = "trackdash.telemetry-sharing.authorization.v1" }
}
