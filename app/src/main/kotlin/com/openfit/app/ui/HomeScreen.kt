package com.openfit.app.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the Home tab shows. Every value comes from the watch or from the persisted summary the
 * activity keeps; nothing is estimated here.
 *
 * [bpm] of -1 means "no reading yet". [heartToday] is false when the saved heart-rate day is not
 * today, so the card can label the number as the last reading instead of today's.
 */
data class HomeUiState(
    val watchName: String = "No watch connected",
    val connected: Boolean = false,
    val battery: Int = 0,
    val firmware: String = "",
    val steps: Int = 0,
    val activeMinutes: Int = 0,
    val sleepMinutes: Int = 0,
    val sleepDeep: Int = 0,
    val sleepLight: Int = 0,
    val sleepRem: Int = 0,
    val bpm: Int = -1,
    val bpmLow: Int = 0,
    val bpmHigh: Int = 0,
    val heartSamples: List<Int> = emptyList(),
    val stress: Int = 0,
    val heartToday: Boolean = false
)

/** Navigation callbacks the Home screen needs from the activity. */
interface HomeActions {
    fun onManageWatch()
    fun onViewHealth()
    fun onViewSleep()
}

/**
 * Hosts the Compose Home tab. The activity owns the tabs and the BLE state; this controller only
 * renders [HomeUiState] and forwards taps through [HomeActions].
 */
class HomeScreenController(private val actions: HomeActions) {
    private var state by mutableStateOf(HomeUiState())

    fun update(next: HomeUiState) {
        state = next
    }

    fun createView(context: Context): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            OpenFitTheme {
                HomeScreen(state, actions)
            }
        }
    }
}

@Composable
private fun HomeScreen(state: HomeUiState, actions: HomeActions) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow(
            remember {
                SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
                    .format(Date())
                    .uppercase(Locale.ROOT)
            }
        )
        Spacer(Modifier.height(7.dp))
        SerifTitle("My dashboard")
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Your watch, your day, at a glance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        DeviceCard(state, actions)
        Spacer(Modifier.height(14.dp))
        ActivityCard(state)
        Spacer(Modifier.height(14.dp))
        HeartCard(state, actions)
        Spacer(Modifier.height(14.dp))
        SleepCard(state, actions)
        if (state.stress > 0) {
            Spacer(Modifier.height(14.dp))
            StressCard(state)
        }

        Spacer(Modifier.height(26.dp))
        Text(
            text = "PRIVATE BY DESIGN     ·     STORED ON THIS PHONE",
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceCard(state: HomeUiState, actions: HomeActions) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = actions::onManageWatch),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("YOUR WATCH", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.watchName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                                if (state.connected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (state.connected) {
                            "Connected"
                        } else {
                            "Saved · reconnect when nearby"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.firmware.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Firmware ${state.firmware}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                DotNumber(
                    text = if (state.battery > 0) "${state.battery}%" else "--",
                    fontSize = 32.sp,
                    color = if (state.battery in 1..20) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "BATTERY",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(state: HomeUiState) {
    val stepGoal = 8000
    val activeGoal = 60
    val sleepGoal = 8 * 60
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(
                    text = "TODAY'S ACTIVITIES",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "GOALS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapsuleRings(
                    progress = listOf(
                        state.steps.toFloat() / stepGoal,
                        state.activeMinutes.toFloat() / activeGoal,
                        state.sleepMinutes.toFloat() / sleepGoal
                    ),
                    colors = listOf(
                        MaterialTheme.colorScheme.onSurface,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(width = 94.dp, height = 130.dp)
                )
                Spacer(Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    RingLegend(
                        label = "STEPS",
                        value = state.steps.toString(),
                        unit = "",
                        goal = "of $stepGoal",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(13.dp))
                    RingLegend(
                        label = "ACTIVE",
                        value = state.activeMinutes.toString(),
                        unit = "min",
                        goal = "of $activeGoal",
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(13.dp))
                    RingLegend(
                        label = "SLEEP",
                        value = (state.sleepMinutes / 60).toString(),
                        unit = "h",
                        goal = "${state.sleepMinutes % 60}m of 8h",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RingLegend(
    label: String,
    value: String,
    unit: String,
    goal: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.Bottom) {
                DotNumber(text = value, fontSize = 22.sp)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = goal,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun HeartCard(state: HomeUiState, actions: HomeActions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(
                    text = "HEART RATE",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.bpm > 0) {
                    Text(
                        text = if (state.heartToday) "TODAY" else "LAST READING",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (state.bpm > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    DotNumber(text = state.bpm.toString(), fontSize = 54.sp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.padding(bottom = 7.dp)) {
                        Text(
                            text = "BPM",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.bpmLow > 0 && state.bpmHigh > 0) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${state.bpmLow}–${state.bpmHigh} range",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (state.heartSamples.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    SparkBars(
                        values = downsample(
                            state.heartSamples.map { it.toFloat() },
                            48
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        text = "${state.heartSamples.size} reading(s) from the watch",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                DotNumber(
                    text = "--",
                    fontSize = 54.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Connect your watch to read its daily heart-rate history.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            DashedDivider()
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions::onViewHealth),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View heart rate",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "→",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SleepCard(state: HomeUiState, actions: HomeActions) {
    val stages = listOf(
        SleepStage(state.sleepDeep, "DEEP", MaterialTheme.colorScheme.onSurface),
        SleepStage(state.sleepLight, "LIGHT", MaterialTheme.colorScheme.onSurfaceVariant),
        SleepStage(state.sleepRem, "REM", MaterialTheme.colorScheme.primary)
    ).filter { it.minutes > 0 }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(
                    text = "SLEEP",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.sleepMinutes > 0) {
                    Text(
                        text = "LAST NIGHT",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (state.sleepMinutes > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    DotNumber(
                        text = (state.sleepMinutes / 60).toString(),
                        fontSize = 46.sp
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "hr",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    DotNumber(
                        text = (state.sleepMinutes % 60).toString().padStart(2, '0'),
                        fontSize = 46.sp
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "min",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                if (stages.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.outline)
                    ) {
                        stages.forEach { stage ->
                            Box(
                                Modifier
                                    .weight(stage.minutes.toFloat())
                                    .fillMaxHeight()
                                    .background(stage.color)
                            )
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Row(Modifier.fillMaxWidth()) {
                        stages.forEach { stage ->
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .background(stage.color, CircleShape)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stage.label,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        letterSpacing = 1.1.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                DotNumber(
                                    text = formatMinutes(stage.minutes),
                                    fontSize = 17.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No sleep recorded yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "The watch has not sent a sleep history frame. OpenFit shows it " +
                        "as soon as one arrives.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            DashedDivider()
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions::onViewSleep),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "View sleep",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "→",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StressCard(state: HomeUiState) {
    val face = when {
        state.stress < 30 -> "☺"
        state.stress < 60 -> "•"
        else -> "☹"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Eyebrow("STRESS", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                DotNumber(text = state.stress.toString(), fontSize = 40.sp)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "watch reading · lower is calmer",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = face,
                fontSize = 40.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class SleepStage(val minutes: Int, val label: String, val color: Color)

private fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "—"
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "${rest}m"
        rest == 0 -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}
