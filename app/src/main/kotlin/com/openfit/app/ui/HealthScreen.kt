package com.openfit.app.ui

import android.content.Context
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One snapshot of the heart-rate page.
 *
 * [bpm] of -1 means "no reading yet". [slots] holds all 288 five-minute slots of the watch's
 * day; the graph samples every third slot so a point lands on the 15-minute cadence NoiseFit
 * uses. [sensors] holds the read-only history frames the watch pushes (SpO2, stress, ...).
 */
data class HealthUiState(
    val bpm: Int,
    val source: String,
    val latest: String,
    val dayLabel: String,
    val low: Int,
    val high: Int,
    val average: Int,
    val samples: Int,
    val slots: List<Int> = emptyList(),
    val sensors: List<SensorCard> = emptyList()
)

/**
 * One read-only watch-history value. [value] is already formatted for display; [unit] may be
 * empty. [detail] says where the number came from (a date, or "LAST <time>").
 */
data class SensorCard(
    val label: String,
    val value: String,
    val unit: String,
    val detail: String
)

/**
 * Hosts the Compose heart-rate page inside the existing programmatic Java layout.
 *
 * The activity keeps its own lifecycle; the view uses
 * [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed] so composition is torn down with
 * the screen's view tree instead of leaking when the activity is recreated.
 */
class HealthScreenController {
    private var state by mutableStateOf(
        HealthUiState(
            bpm = -1,
            source = "",
            latest = "",
            dayLabel = "",
            low = 0,
            high = 0,
            average = 0,
            samples = 0
        )
    )

    fun update(next: HealthUiState) {
        state = next
    }

    fun createView(context: Context): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            OpenFitTheme {
                HealthScreen(state)
            }
        }
    }
}

@Composable
private fun HealthScreen(state: HealthUiState) {
    var range by remember { mutableIntStateOf(0) }
    val ranges = listOf("DAY", "WEEK", "MONTH", "YEAR")

    Column(Modifier.fillMaxWidth()) {
        Eyebrow("HEART RATE")
        Spacer(Modifier.height(7.dp))
        SerifTitle("Heart rate")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Every reading the watch stores, placed on the clock.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))
        SegmentedPill(
            options = ranges,
            selectedIndex = range,
            modifier = Modifier.fillMaxWidth()
        ) { range = it }

        Spacer(Modifier.height(14.dp))
        HeroCard(state)

        if (state.bpm > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile("LOW", state.low, Modifier.weight(1f))
                MetricTile("AVG", state.average, Modifier.weight(1f))
                MetricTile("HIGH", state.high, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        if (range == 0) {
            GraphCard(state)
        } else {
            RangeNote(ranges[range], state)
        }

        if (state.sensors.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Eyebrow("WATCH HISTORY", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            state.sensors.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { SensorTile(it, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(14.dp))
        AboutCard()
    }
}

@Composable
private fun HeroCard(state: HealthUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f)
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Heart rate",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (state.dayLabel.isNotEmpty()) {
                    Text(
                        text = state.dayLabel,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "♥",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                if (state.bpm > 0) {
                    AnimatedContent(
                        targetState = state.bpm,
                        transitionSpec = {
                            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 })
                                .togetherWith(fadeOut(tween(140)))
                        },
                        label = "heart-rate"
                    ) { value ->
                        DotNumber(text = value.toString(), fontSize = 62.sp)
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.padding(bottom = 9.dp)) {
                        Text(
                            text = "BPM",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.3.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.average > 0) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "avg ${state.average}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    DotNumber(
                        text = "--",
                        fontSize = 62.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.source.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = state.source,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = if (state.bpm > 0) state.latest else "No readings yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.bpm <= 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Connect your watch to read its daily heart-rate history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GraphCard(state: HealthUiState) {
    val points = remember(state.slots) { heartPoints(state.slots) }
    val hasData = points.any { it != null }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "24-HOUR TREND",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "15 MIN",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(16.dp))
            if (hasData) {
                HeartRateChart(
                    points = points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("00", "06", "12", "18", "24").forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "The watch has not sent today's heart-rate history yet. " +
                        "It arrives after the watch binds.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeartRateChart(points: List<Int?>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline
    val valid = points.filterNotNull()
    val low = ((valid.minOrNull() ?: 60) - 8).coerceAtLeast(30)
    val high = (valid.maxOrNull() ?: 100) + 8
    val span = (high - low).coerceAtLeast(20).toFloat()
    val progress by animateFloatAsState(
        targetValue = if (valid.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = 720),
        label = "heart-chart"
    )

    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val count = points.size.coerceAtLeast(2)
        val step = width / (count - 1).toFloat()
        fun x(index: Int) = step * index
        fun y(value: Int) = height - ((value - low) / span) * height * progress

        // Quiet horizontal guides.
        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            drawLine(
                color = grid,
                start = Offset(0f, height * fraction),
                end = Offset(width, height * fraction),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(3.dp.toPx(), 5.dp.toPx()),
                    0f
                )
            )
        }

        var index = 0
        while (index < points.size) {
            val value = points[index]
            if (value == null) {
                index++
                continue
            }
            val segment = ArrayList<Pair<Int, Int>>()
            while (index < points.size && points[index] != null) {
                segment.add(index to points[index]!!)
                index++
            }
            if (segment.size == 1) {
                val only = segment.first()
                drawCircle(
                    color = color,
                    radius = 2.4.dp.toPx(),
                    center = Offset(x(only.first), y(only.second))
                )
                continue
            }
            val line = Path()
            val area = Path()
            val first = segment.first()
            line.moveTo(x(first.first), y(first.second))
            area.moveTo(x(first.first), height)
            area.lineTo(x(first.first), y(first.second))
            for (k in 1 until segment.size) {
                val point = segment[k]
                line.lineTo(x(point.first), y(point.second))
                area.lineTo(x(point.first), y(point.second))
            }
            area.lineTo(x(segment.last().first), height)
            area.close()
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.20f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )
            drawPath(
                path = line,
                color = color,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
            segment.forEachIndexed { dotIndex, point ->
                if (dotIndex % 4 == 0) {
                    drawCircle(
                        color = color,
                        radius = 2.2.dp.toPx(),
                        center = Offset(x(point.first), y(point.second))
                    )
                }
            }
        }
    }
}

/** Buckets the 288 five-minute slots into 96 points, one every 15 minutes. */
private fun heartPoints(slots: List<Int>): List<Int?> {
    if (slots.isEmpty()) return List(96) { null }
    return (0 until 288 step 3).map { start ->
        var value = 0
        for (index in start until minOf(start + 3, slots.size)) {
            val slot = slots[index]
            if (slot > 0) value = slot
        }
        if (value > 0) value else null
    }
}

@Composable
private fun RangeNote(range: String, state: HealthUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$range VIEW",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.3.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (state.average > 0) {
                    DotNumber(text = state.average.toString(), fontSize = 26.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "OpenFit stores today's 15-minute detail in full. Longer ranges need " +
                    "one saved day per bar, so they fill in as you keep the watch on and open " +
                    "the app. Today is already counted.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MetricTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(5.dp))
            DotNumber(
                text = if (value > 0) value.toString() else "—",
                fontSize = 24.sp
            )
        }
    }
}

@Composable
private fun SensorTile(card: SensorCard, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = card.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = card.value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (card.unit.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = card.unit,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = card.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Eyebrow("ABOUT THIS DATA", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your watch records a heart rate every five minutes and keeps its own " +
                    "history for steps, sleep, SpO2, stress, wrist temperature and blood " +
                    "pressure. The graph plots every third slot, matching the 15-minute " +
                    "cadence NoiseFit shows. OpenFit only reads what the watch sends after it " +
                    "connects; gaps mean the watch was off or not worn. Wrist temperature is " +
                    "shown as the watch's raw value because its scale is not published. " +
                    "This is not a medical device.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
