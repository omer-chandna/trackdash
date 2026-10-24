package dev.trackdash.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography

val TrackDashBackground = Color.Black
val TrackDashPanel = Color(0xFF101010)
val TrackDashElevated = Color(0xFF1A1A1A)
val TrackDashBorder = Color(0xFF414141)
val TrackDashWhite = Color.White
val TrackDashAmber = Color(0xFFFFC94A)
val TrackDashCritical = Color(0xFFFF4D5E)
val TrackDashMuted = Color(0xFFCECECE)

private val MotorsportColors = darkColorScheme(
    primary = TrackDashWhite,
    onPrimary = Color.Black,
    secondary = TrackDashAmber,
    onSecondary = Color(0xFF2B2100),
    background = TrackDashBackground,
    onBackground = Color.White,
    surface = TrackDashPanel,
    onSurface = Color.White,
    surfaceVariant = TrackDashElevated,
    onSurfaceVariant = TrackDashMuted,
    surfaceContainer = TrackDashPanel,
    surfaceContainerHigh = TrackDashElevated,
    outline = TrackDashBorder,
    outlineVariant = TrackDashBorder.copy(alpha = 0.65f),
    secondaryContainer = Color(0xFF303030),
    onSecondaryContainer = Color.White,
    error = TrackDashCritical,
    onError = Color.White,
    errorContainer = Color(0xFF47171D),
    onErrorContainer = Color(0xFFFFDADC),
)

private val TrackDashShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val TrackDashTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
)

@Composable
fun TrackDashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MotorsportColors,
        typography = TrackDashTypography,
        shapes = TrackDashShapes,
        content = content,
    )
}
