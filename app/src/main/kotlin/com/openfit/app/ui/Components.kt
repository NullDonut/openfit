package com.openfit.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared pieces of the Nothing-style visual language.
 *
 * Keeping these here means the Health, Home and Sleep screens cannot drift apart while the
 * remaining Views tabs are migrated one at a time.
 */

/**
 * Section label: monospaced, uppercase, wide tracking. Red by default, matching the Views
 * screens' section labels.
 */
@Composable
internal fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        ),
        color = color
    )
}

/** Page title in the bundled serif face. */
@Composable
internal fun SerifTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontFamily = SerifDisplay,
            fontWeight = FontWeight.Normal
        ),
        color = color
    )
}

/** Hero numeral in the bundled dot-matrix face. */
@Composable
internal fun DotNumber(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 46.sp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = DotMatrix,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        color = color
    )
}

/** Hairline dotted rule used between rows and card sections. */
@Composable
internal fun DashedDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(5.dp.toPx(), 5.dp.toPx()),
                0f
            )
        )
    }
}

/**
 * Segmented control: a charcoal pill with a white capsule on the selected option. The selected
 * label is drawn in the background colour so it stays legible on the white capsule.
 */
@Composable
internal fun SegmentedPill(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .clickable { onSelect(index) },
                    shape = RoundedCornerShape(50),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        Color.Transparent
                    }
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        textAlign = TextAlign.Center,
                        color = if (selected) {
                            MaterialTheme.colorScheme.background
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/**
 * Concentric stadium ("capsule") progress rings. Each entry in [progress] is a 0..1 fraction and
 * gets its own inset so the three rings in the dashboard read as one stacked indicator.
 */
@Composable
internal fun CapsuleRings(
    progress: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.outline
) {
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val gap = stroke * 1.15f
        progress.forEachIndexed { index, value ->
            val inset = stroke / 2f + index * (stroke + gap)
            if (size.width - inset * 2f <= 0f || size.height - inset * 2f <= 0f) {
                return@forEachIndexed
            }
            val rect = Rect(inset, inset, size.width - inset, size.height - inset)
            val radius = CornerRadius(minOf(rect.width, rect.height) / 2f)
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect.left,
                        rect.top,
                        rect.right,
                        rect.bottom,
                        radius,
                        radius,
                        radius,
                        radius
                    )
                )
            }
            drawPath(
                path = path,
                color = trackColor,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val fraction = value.coerceIn(0f, 1f)
            if (fraction <= 0f) return@forEachIndexed
            val measure = PathMeasure().apply { setPath(path, false) }
            val segment = Path()
            if (measure.getSegment(0f, measure.length * fraction, segment, true)) {
                drawPath(
                    path = segment,
                    color = colors[index % colors.size],
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/** Bar sparkline. Bars are scaled between the smallest and largest value in [values]. */
@Composable
internal fun SparkBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 1f
        val range = (max - min).coerceAtLeast(1f)
        val slot = size.width / values.size
        val bar = (slot * 0.62f).coerceIn(1f, 8.dp.toPx())
        values.forEachIndexed { index, value ->
            val fraction = if (values.size == 1) {
                0.6f
            } else {
                ((value - min) / range).coerceIn(0f, 1f) * 0.85f + 0.15f
            }
            val height = fraction * size.height
            drawRoundRect(
                color = color,
                topLeft = Offset(index * slot + (slot - bar) / 2f, size.height - height),
                size = Size(bar, height),
                cornerRadius = CornerRadius(bar / 2f)
            )
        }
    }
}

/** Evenly reduces [values] to at most [target] entries, keeping the overall shape. */
internal fun downsample(values: List<Float>, target: Int): List<Float> {
    if (target <= 0 || values.size <= target) return values
    return List(target) { index ->
        values[(index.toFloat() / target * values.size).toInt().coerceIn(0, values.size - 1)]
    }
}

/**
 * The secondary action button: flat dark fill, hairline border, bold label. Mirrors
 * `MainActivity.secondaryButton()` so Compose and Views buttons look the same.
 */
@Composable
internal fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
