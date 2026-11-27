package com.openfit.app.ui

import android.content.Context
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One snapshot of what the Health tab should show.
 *
 * Built in Java from the persisted heart-rate summary, so Compose stays a pure presentation
 * layer and the BLE code keeps owning the data. [bpm] of -1 means "no reading yet".
 */
data class HealthUiState(
    val bpm: Int,
    val source: String,
    val latest: String,
    val dayLabel: String,
    val low: Int,
    val high: Int,
    val samples: Int
)

/**
 * Hosts the Compose Health tab inside the existing programmatic Java layout.
 *
 * The activity keeps its own lifecycle; the view uses
 * [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed] so composition is torn down with
 * the tab's view tree instead of leaking when the activity is recreated.
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
    Column(Modifier.fillMaxWidth()) {
        Eyebrow("HEALTH")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your data, at a glance",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(22.dp))

        HeartCard(state)

        if (state.bpm > 0) {
            Spacer(Modifier.height(14.dp))
            Eyebrow(state.dayLabel)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("LOW", state.low, Modifier.weight(1f))
                StatTile("HIGH", state.high, Modifier.weight(1f))
                StatTile("SAMPLES", state.samples, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(14.dp))
        AboutCard()
    }
}

/**
 * The Nothing-style eyebrow: monospaced, uppercase, wide tracking, red accent.
 *
 * The Views screens use the same treatment for their section labels, which keeps the Compose
 * and Java parts of the app visually consistent.
 */
@Composable
private fun Eyebrow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        ),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun HeartCard(state: HealthUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 380f)
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
                Text(
                    text = "♥",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.size(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                if (state.bpm > 0) {
                    AnimatedContent(targetState = state.bpm, label = "heart-rate") { value ->
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "bpm",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                } else {
                    Text(
                        text = "--",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.source.isNotEmpty()) {
                Spacer(Modifier.size(8.dp))
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

            Spacer(Modifier.size(12.dp))
            Text(
                text = if (state.bpm > 0) state.latest else "No readings yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.bpm <= 0) {
                Spacer(Modifier.size(4.dp))
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
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (value > 0) value.toString() else "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
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
            Eyebrow("ABOUT THIS DATA")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Your watch records a heart rate every five minutes. OpenFit shows the " +
                    "samples it receives; gaps mean the watch was off or not worn. " +
                    "This is not a medical device.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
