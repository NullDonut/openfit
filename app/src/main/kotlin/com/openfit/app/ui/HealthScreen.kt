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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
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
                    text = "BPM · 15 MIN",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(14.dp))
            if (hasData) {
                HeartRateChart(
                    points = points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(196.dp)
                )
            } else {
                EmptyChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "The watch has not sent today's heart-rate history yet. It " +
                        "arrives after the watch binds.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeartRateChart(points: List<Int?>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline
    val gridFaint = MaterialTheme.colorScheme.outlineVariant
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    val hole = MaterialTheme.colorScheme.background
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 9.sp
    )

    val valid = remember(points) { points.filterNotNull() }
    val bounds = remember(points) { chartBounds(valid) }
    val low = bounds[0]
    val high = bounds[1]
    val step = bounds[2]
    val span = (high - low).coerceAtLeast(1)
    val markers = remember(points) {
        var last = -1
        var peakIndex = -1
        var peak = Int.MIN_VALUE
        points.forEachIndexed { index, value ->
            if (value != null) {
                last = index
                if (value > peak) {
                    peak = value
                    peakIndex = index
                }
            }
        }
        intArrayOf(last, peakIndex)
    }
    val progress by animateFloatAsState(
        targetValue = if (valid.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = 760),
        label = "heart-chart"
    )
    val nowMinute = remember {
        val now = java.time.LocalTime.now()
        now.hour * 60 + now.minute
    }

    Canvas(modifier) {
        val leftPad = 30.dp.toPx()
        val rightPad = 12.dp.toPx()
        val topPad = 16.dp.toPx()
        val bottomPad = 20.dp.toPx()
        val plotLeft = leftPad
        val plotRight = size.width - rightPad
        val plotTop = topPad
        val plotBottom = size.height - bottomPad
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val count = points.size.coerceAtLeast(2)

        fun x(index: Int): Float = plotLeft + index.toFloat() / (count - 1) * plotWidth
        fun y(value: Int): Float =
            plotBottom - ((value - low).toFloat() / span * progress) * plotHeight

        // Horizontal value guides, labelled down the left gutter.
        var guide = low
        while (guide <= high) {
            val guideY = plotBottom - ((guide - low).toFloat() / span) * plotHeight
            drawLine(
                color = grid,
                start = Offset(plotLeft, guideY),
                end = Offset(plotRight, guideY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(2.dp.toPx(), 6.dp.toPx()),
                    0f
                )
            )
            val text = measurer.measure(guide.toString(), labelStyle)
            drawText(
                textLayoutResult = text,
                topLeft = Offset(
                    leftPad - text.size.width - 6.dp.toPx(),
                    guideY - text.size.height / 2f
                ),
                color = soft
            )
            guide += step
        }

        // Faint six-hour divisions.
        for (hour in intArrayOf(6, 12, 18)) {
            val guideX = plotLeft + hour / 24f * plotWidth
            drawLine(
                color = gridFaint,
                start = Offset(guideX, plotTop),
                end = Offset(guideX, plotBottom),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Where the clock is now.
        if (nowMinute in 0..1440) {
            val nowX = plotLeft + nowMinute / 1440f * plotWidth
            drawLine(
                color = accent.copy(alpha = 0.35f),
                start = Offset(nowX, plotTop),
                end = Offset(nowX, plotBottom),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        // The trace itself, one smooth run per unbroken stretch of readings.
        var index = 0
        while (index < points.size) {
            if (points[index] == null) {
                index++
                continue
            }
            val offsets = ArrayList<Offset>()
            while (index < points.size && points[index] != null) {
                offsets.add(Offset(x(index), y(points[index]!!)))
                index++
            }
            if (offsets.size == 1) {
                drawCircle(color = accent, radius = 2.6.dp.toPx(), center = offsets.first())
                continue
            }
            val area = Path()
            val line = smoothPath(offsets, area, plotBottom)
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                    startY = plotTop,
                    endY = plotBottom
                )
            )
            drawPath(
                path = line,
                color = accent,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            offsets.forEachIndexed { offsetIndex, offset ->
                if (offsetIndex % 4 == 0) {
                    drawCircle(color = accent, radius = 2.dp.toPx(), center = offset)
                }
            }
        }

        // Peak label, so the day's hardest moment is readable at a glance.
        val peakIndex = markers[1]
        if (peakIndex in points.indices) {
            val peakValue = points[peakIndex] ?: 0
            val peakX = x(peakIndex)
            val peakY = y(peakValue)
            val text = measurer.measure(peakValue.toString(), labelStyle)
            val labelX = (peakX - text.size.width / 2f)
                .coerceIn(plotLeft, (plotRight - text.size.width).coerceAtLeast(plotLeft))
            val labelY = (peakY - text.size.height - 7.dp.toPx()).coerceAtLeast(0f)
            drawText(textLayoutResult = text, topLeft = Offset(labelX, labelY), color = accent)
        }

        // The most recent reading carries a soft halo.
        val lastIndex = markers[0]
        if (lastIndex in points.indices) {
            val lastValue = points[lastIndex] ?: 0
            val center = Offset(x(lastIndex), y(lastValue))
            drawCircle(color = accent.copy(alpha = 0.20f), radius = 7.dp.toPx(), center = center)
            drawCircle(color = accent, radius = 3.2.dp.toPx(), center = center)
            drawCircle(color = hole, radius = 1.2.dp.toPx(), center = center)
        }

        // Hour labels under the plot.
        listOf(0 to "00", 6 to "06", 12 to "12", 18 to "18", 24 to "24").forEach { (hour, label) ->
            val labelX = plotLeft + hour / 24f * plotWidth
            val text = measurer.measure(label, labelStyle)
            val tx = (labelX - text.size.width / 2f)
                .coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
            drawText(
                textLayoutResult = text,
                topLeft = Offset(tx, plotBottom + 5.dp.toPx()),
                color = soft
            )
        }
    }
}

/** Rounds the readings into a tidy BPM window: returns [low, high, step] for the guide lines. */
private fun chartBounds(values: List<Int>): IntArray {
    if (values.isEmpty()) return intArrayOf(50, 150, 25)
    val min = values.min()
    val max = values.max()
    var chosen = 50
    for (step in intArrayOf(10, 20, 25, 40, 50)) {
        val low = Math.floorDiv(min, step) * step
        var high = ((max + step - 1) / step) * step
        if (high <= max) high += step
        val lines = (high - low) / step + 1
        if (lines in 3..6) {
            chosen = step
            break
        }
    }
    val step = chosen
    var low = Math.floorDiv(min, step) * step
    var high = ((max + step - 1) / step) * step
    if (high <= max) high += step
    while ((high - low) / step + 1 < 3) high += step
    return intArrayOf(low, high, step)
}

/** A quiet placeholder drawn where the graph will appear before a reading arrives. */
@Composable
private fun EmptyChart(modifier: Modifier = Modifier) {
    val grid = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 6.dp.toPx()), 0f)
        for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
            val y = size.height * fraction
            drawLine(
                color = grid,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
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
