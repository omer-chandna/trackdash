package dev.trackdash.recording

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Stable-size values keep rapidly changing telemetry from moving the surrounding controls. */
data class RallyReading(
    val label: String,
    val value: String,
    val unit: String = "",
    val critical: Boolean = false,
    val tag: String = "tile-$label",
    val progress: Float? = null,
)

@Composable
fun RallyHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = Color.Black) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 600.dp
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        color = Color.White, maxLines = 1)
                    if (wide) actions()
                }
                if (!wide) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}

@Composable
fun RallyNumber(value: String, modifier: Modifier = Modifier, maximumSp: Int = 64,
    color: Color = Color.White, tag: String? = null) {
    BasicText(
        text = value,
        modifier = if (tag == null) modifier else modifier.testTag(tag),
        style = TextStyle(color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum", letterSpacing = (-1).sp),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = maximumSp.sp, stepSize = 2.sp),
    )
}

@Composable
fun RallyMetric(reading: RallyReading, modifier: Modifier = Modifier, maximumSp: Int = 64) {
    Surface(
        modifier = modifier,
        color = if (reading.critical) Color(0xFF500009) else Color(0xFF0C0C0C),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(if (reading.critical) 2.dp else 1.dp,
            if (reading.critical) MaterialTheme.colorScheme.error else Color(0xFF414141)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(reading.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFE0E0E0), maxLines = 1)
                if (reading.unit.isNotEmpty()) Text(reading.unit, style = MaterialTheme.typography.labelMedium, color = Color(0xFFE0E0E0))
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                RallyNumber(reading.value, Modifier.fillMaxWidth(), maximumSp = maximumSp, tag = reading.tag)
            }
            reading.progress?.let { progress ->
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color.White, trackColor = Color(0xFF414141))
            }
        }
    }
}

@Composable
fun RallyGaugeGrid(readings: List<RallyReading>, columns: Int, modifier: Modifier = Modifier,
    fitHeight: Boolean = false, maximumSp: Int = 64) {
    if (readings.isEmpty()) return
    val rows = readings.chunked(columns)
    BoxWithConstraints(modifier) {
        // Keep the values large when a short window, large text, or several warning banners consume space.
        val fits = fitHeight && maxHeight >= (rows.size * 92 + (rows.size - 1) * 6).dp
        val gridModifier = if (fitHeight && !fits) Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            else Modifier.fillMaxWidth().then(if (fits) Modifier.fillMaxHeight() else Modifier)
        Column(gridModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Row(if (fits) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(112.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { RallyMetric(it, Modifier.weight(1f).fillMaxHeight(), maximumSp) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
