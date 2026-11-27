package com.openfit.app.ui

import android.content.Context
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** One stage run of the night; [startMinute] is minutes since midnight. */
data class SleepStagePoint(val startMinute: Int, val stage: Int)

/**
 * One snapshot of the Sleep page.
 *
 * When [fromWatch] is true the stage fields and [stages] come from the watch's sleep frame;
 * otherwise [total] is the phone's manual note and the stage fields stay zero. [startMinute] and
 * [endMinute] are minutes since midnight and frame the hypnogram's clock axis.
 */
data class SleepUiState(
    val total: Int,
    val deep: Int,
    val light: Int,
    val rem: Int,
    val awake: Int,
    val stayUp: Int,
    val label: String,
    val fromWatch: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val stages: List<SleepStagePoint>
)

/** The only action the Sleep page needs from the activity: open the manual duration editor. */
fun interface SleepActions {
    fun onEdit()
}

class SleepScreenController(private val actions: SleepActions) {
    private var state by mutableStateOf(
        SleepUiState(0, 0, 0, 0, 0, 0, "", false, 0, 0, emptyList())
    )

    fun update(next: SleepUiState) {
        state = next
    }

    fun createView(context: Context): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            OpenFitTheme {
                SleepScreen(state, actions)
            }
        }
    }
}

@Composable
private fun SleepScreen(state: SleepUiState, actions: SleepActions) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow("SLEEP")
        Spacer(Modifier.height(8.dp))
        SerifTitle(if (state.label.isEmpty()) "Sleep" else state.label)
        Spacer(Modifier.height(18.dp))

        HeroCard(state)

        if (state.fromWatch && state.total > 0) {
            Spacer(Modifier.height(14.dp))
            StageBreakdownCard(state)
            if (state.stages.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                HypnogramCard(state)
            }
        }

        Spacer(Modifier.height(14.dp))
        SecondaryButton(
            label = if (state.fromWatch) "Add a phone note   +" else "Add sleep duration   +",
            modifier = Modifier.fillMaxWidth(),
            onClick = actions::onEdit
        )
    }
}

@Composable
private fun HeroCard(state: SleepUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sleep",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                SourcePill(state.fromWatch)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "☾",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            if (state.total > 0) {
                val hours = state.total / 60
                val minutes = state.total % 60
                Row(verticalAlignment = Alignment.Bottom) {
                    DotNumber(text = hours.toString(), fontSize = 54.sp)
                    Text(
                        text = "H",
                        modifier = Modifier.padding(bottom = 10.dp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    DotNumber(
                        text = String.format(Locale.US, "%02d", minutes),
                        fontSize = 38.sp
                    )
                    Text(
                        text = "M",
                        modifier = Modifier.padding(bottom = 7.dp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                DotNumber(
                    text = "--",
                    fontSize = 54.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    state.total > 0 && state.fromWatch -> "Recorded by your watch."
                    state.total > 0 -> "A private note, stored only on this phone."
                    else -> "No sleep recorded yet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.total <= 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Your watch pushes last night's stages once a night is complete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SourcePill(fromWatch: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (fromWatch) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Text(
            text = if (fromWatch) "FROM WATCH" else "PHONE NOTE",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.1.sp,
            color = if (fromWatch) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** The stacked stage bar plus its legend: deep, light, REM and awake, brightest first. */
@Composable
private fun StageBreakdownCard(state: SleepUiState) {
    val parts = stageParts(state).filter { it.minutes > 0 }
    val total = parts.sumOf { it.minutes }.coerceAtLeast(1)
    val progress by animateFloatAsState(
        targetValue = if (parts.isEmpty()) 0f else 1f,
        animationSpec = tween(650),
        label = "stage-bar"
    )
    val track = trackColor()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = "STAGE BREAKDOWN",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.3.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
            ) {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(
                    color = track,
                    cornerRadius = radius
                )
                var x = 0f
                val gap = 3.dp.toPx()
                parts.forEachIndexed { index, part ->
                    val fraction = part.minutes.toFloat() / total * progress
                    val segment = (size.width * fraction - if (index < parts.size - 1) gap else 0f)
                        .coerceAtLeast(0f)
                    if (segment > 0f) {
                        drawRoundRect(
                            color = part.color,
                            topLeft = Offset(x, 0f),
                            size = Size(segment, size.height),
                            cornerRadius = radius
                        )
                        x += segment + gap
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            parts.forEachIndexed { index, part ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(part.color, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = part.label,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.1.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatDuration(part.minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${part.minutes * 100 / total}%",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** The night's shape as a stepped line, one horizontal run per stage. */
@Composable
private fun HypnogramCard(state: SleepUiState) {
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(750),
        label = "hypnogram"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "HYPNOGRAM",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "STAGES",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(14.dp))

            Hypnogram(
                stages = state.stages,
                startMinute = state.startMinute,
                endMinute = state.endMinute,
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = formatClock(state.startMinute),
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatClock(state.endMinute),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Hypnogram(
    stages: List<SleepStagePoint>,
    startMinute: Int,
    endMinute: Int,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val track = trackColor()
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (stages.isEmpty()) return@Canvas
        val padY = 10.dp.toPx()
        val usable = size.height - padY * 2f
        fun yFor(level: Int): Float = padY + usable * (3 - level) / 3f

        // Faint band guides: awake at the top, deep at the bottom.
        for (level in 0..3) {
            drawLine(
                color = track,
                start = Offset(0f, yFor(level)),
                end = Offset(size.width, yFor(level)),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 5.dp.toPx()),
                    0f
                )
            )
        }

        val base = stages.first().startMinute
        val endAbs = if (endMinute <= base) endMinute + 24 * 60 else endMinute
        val span = (endAbs - base).coerceAtLeast(1).toFloat()
        fun xFor(minute: Int): Float {
            val m = if (minute < base) minute + 24 * 60 else minute
            return ((m - base) / span) * size.width * progress
        }

        val stroke = 2.5.dp.toPx()
        var previousY = 0f
        stages.forEachIndexed { index, stage ->
            val level = levelFor(stage.stage)
            val x0 = xFor(stage.startMinute)
            val x1 = if (index + 1 < stages.size) {
                xFor(stages[index + 1].startMinute)
            } else {
                size.width * progress
            }
            val y = yFor(level)
            if (index > 0) {
                drawLine(
                    color = accent,
                    start = Offset(x0, previousY),
                    end = Offset(x0, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            drawLine(
                color = accent,
                start = Offset(x0, y),
                end = Offset(x1.coerceAtLeast(x0), y),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            previousY = y
        }
    }
}

private data class StagePart(val label: String, val minutes: Int, val color: Color)

@Composable
private fun stageParts(state: SleepUiState): List<StagePart> {
    val scheme = MaterialTheme.colorScheme
    return listOf(
        StagePart("DEEP", state.deep, scheme.primary),
        StagePart("LIGHT", state.light, scheme.onSurface),
        StagePart("REM", state.rem, scheme.onSurfaceVariant),
        StagePart("AWAKE", state.awake + state.stayUp, scheme.outline)
    )
}

@Composable
private fun trackColor(): Color = MaterialTheme.colorScheme.surfaceVariant

/** Maps a vendor stage code to a band level: 3 awake (top) down to 0 deep (bottom). */
private fun levelFor(stage: Int): Int = when (stage) {
    3 -> 0 // deep
    2 -> 1 // light
    4 -> 2 // REM
    1 -> 3 // awake
    6 -> 3 // stay-up
    else -> 1
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return String.format(Locale.US, "%dh %02dm", hours, rest)
}

private fun formatClock(minute: Int): String =
    String.format(Locale.US, "%02d:%02d", (minute / 60) % 24, minute % 60)
