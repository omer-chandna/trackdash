package dev.trackdash.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MotorsportColors = darkColorScheme(
    primary = Color(0xFFB6F36B),
    onPrimary = Color(0xFF152400),
    background = Color(0xFF0B0E12),
    onBackground = Color(0xFFF2F5F7),
    surface = Color(0xFF151A21),
    onSurface = Color(0xFFF2F5F7),
    surfaceVariant = Color(0xFF232B35),
    onSurfaceVariant = Color(0xFFB2BDC9),
    surfaceContainer = Color(0xFF151A21),
    secondaryContainer = Color(0xFF304126),
    onSecondaryContainer = Color(0xFFB6F36B),
    error = Color(0xFFFF7878),
)

@Composable
fun TrackDashTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MotorsportColors, content = content)
}
