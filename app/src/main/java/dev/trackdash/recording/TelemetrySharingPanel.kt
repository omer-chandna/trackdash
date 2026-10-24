package dev.trackdash.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.trackdash.core.sharing.validateSharingEndpoint
import dev.trackdash.core.sharing.validateSharingAuthorization
import dev.trackdash.ui.TrackDashPanel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TelemetrySharingPanel() {
    val context = LocalContext.current
    val preferences = remember(context) { TelemetrySharingPreferences(context) }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var endpoint by remember { mutableStateOf("") }
    // Never save the authorization field into instance-state bundles.
    var authorization by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(preferences) {
        runCatching { preferences.settings.first() }.fold(onSuccess = {
            enabled = it.enabled; endpoint = it.endpoint; authorization = it.authorization
        }, onFailure = { status = "Sharing settings could not be unlocked. Re-enter and save them." })
        loaded = true
    }
    val endpointValid = endpoint.isBlank() && !enabled || runCatching { validateSharingEndpoint(endpoint) }.isSuccess
    val authorizationValid = runCatching { validateSharingAuthorization(authorization) }.isSuccess
    TrackDashPanel {
        Text("Optional telemetry sharing", style = MaterialTheme.typography.titleLarge)
        Text("Off by default. Send sensor values only to your own HTTPS receiver at up to 1 Hz. " +
            "No location, VIN, notes, session IDs or saved recordings are sent. " +
            "The receiver can still see your network address; choose one you trust.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Share during new recordings", Modifier.weight(1f))
            Switch(enabled, { enabled = it; status = "" }, enabled = loaded && !saving)
        }
        OutlinedTextField(endpoint, { endpoint = it.trim(); status = "" }, modifier = Modifier.fillMaxWidth(),
            label = { Text("HTTPS receiver URL") }, singleLine = true, enabled = loaded && !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = !endpointValid,
            supportingText = { Text("HTTPS only. No URL credentials, query parameters or redirects.") })
        OutlinedTextField(authorization, { authorization = it; status = "" }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Authorization header (optional)") }, singleLine = true, enabled = loaded && !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(), isError = !authorizationValid,
            supportingText = { Text("For example, Bearer followed by your receiver token. Stored encrypted on this device.") })
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                saving = true
                scope.launch {
                    status = runCatching { preferences.save(SharingSettings(enabled, endpoint, authorization)) }
                        .fold(onSuccess = { "Sharing settings saved. Changes apply to the next recording." },
                            onFailure = { "Sharing settings could not be saved. Check the URL and authorization value." })
                    saving = false
                }
            }, enabled = loaded && !saving && endpointValid && authorizationValid) { Text("Save sharing settings") }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
            Text("Delivery is best-effort: failures and full queues are counted, never retried or allowed to stop local recording.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
