package com.openfit.app.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.util.Locale

/** Identity and health of the saved watch, shown at the top of the Settings tab. */
data class WatchInfoUi(
    val name: String,
    val address: String,
    val connected: Boolean,
    val battery: Int,
    val firmware: String
)

/** The sedentary-reminder window. [period] is the vendor's interval code, preserved verbatim. */
data class SedentaryUi(
    val enabled: Boolean,
    val period: Int,
    val startHour: Int,
    val startMin: Int,
    val endHour: Int,
    val endMin: Int
)

/**
 * One alarm. [data] is the watch's repeat byte: 128 means one-off, any other value is the weekday
 * mask. It is preserved from the watch so editing the list never turns a repeating alarm into a
 * one-off.
 */
data class AlarmUi(val id: Int, val hour: Int, val minute: Int, val data: Int)

/**
 * Everything the Settings tab renders. The activity owns the values and pushes a new state after
 * every watch reply, so the switches always reflect what the watch last reported.
 *
 * DND and bright screen are deliberately absent: the Buzz acknowledges or ignores their vendor
 * frames without changing anything, and the SDK stores the bright-screen flag under the key
 * "taiwan". Their builders stay in ZhBraceletBinding for models that implement them.
 */
data class SettingsUiState(
    val watch: WatchInfoUi,
    val continuousHr: Boolean = false,
    val batterySaver: Boolean = false,
    val sedentary: SedentaryUi = SedentaryUi(false, 2, 9, 0, 18, 0),
    val alarms: List<AlarmUi> = emptyList(),
    val status: String = "",
    val statusIsError: Boolean = false
)

/** Every write the Settings tab can start. The activity turns each into one Zh frame. */
interface SettingsActions {
    fun onRefreshInfo()
    fun onSyncTime()
    fun onFindWatch()
    fun onToggleContinuousHr(on: Boolean)
    fun onToggleBatterySaver(on: Boolean)
    fun onToggleSedentary(on: Boolean)
    fun onEditSedentaryWindow()
    fun onAddAlarm()
    fun onRemoveAlarm(id: Int)
    fun onForgetWatch()
}

class SettingsScreenController(private val actions: SettingsActions) {
    private var state by mutableStateOf(
        SettingsUiState(WatchInfoUi("No watch connected", "", false, 0, ""))
    )

    fun update(next: SettingsUiState) {
        state = next
    }

    fun createView(context: Context): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            OpenFitTheme {
                SettingsScreen(state, actions)
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow("SETTINGS")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Watch controls",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Changes are sent to the watch one command at a time.",
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            StatusLine(state.status, state.statusIsError)
        }

        Spacer(Modifier.height(20.dp))
        WatchCard(state.watch, actions)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                label = "Sync time",
                modifier = Modifier.weight(1f),
                onClick = actions::onSyncTime
            )
            SecondaryButton(
                label = "Find watch",
                modifier = Modifier.weight(1f),
                onClick = actions::onFindWatch
            )
        }

        Spacer(Modifier.height(22.dp))
        Eyebrow("HEALTH", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            ToggleRow(
                title = "Auto heart rate",
                subtitle = "Let the watch record your pulse through the day, about every 15 minutes. OpenFit re-applies this setting after every reconnect.",
                checked = state.continuousHr,
                onCheckedChange = actions::onToggleContinuousHr
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Reading interval",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "The watch stores one slot every 5 minutes; the graph shows 15.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "15 MIN",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ToggleRow(
                title = "Sedentary reminder",
                subtitle = "Vibrate when you sit for too long.",
                checked = state.sedentary.enabled,
                onCheckedChange = actions::onToggleSedentary
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = actions::onEditSedentaryWindow)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Reminder window",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = clock(state.sedentary.startHour, state.sedentary.startMin)
                                + "  →  " + clock(state.sedentary.endHour, state.sedentary.endMin),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "EDIT",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Eyebrow("ALARMS", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            if (state.alarms.isEmpty()) {
                Text(
                    text = "No alarms on the watch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            } else {
                for ((index, alarm) in state.alarms.withIndex()) {
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    AlarmRow(alarm) { actions.onRemoveAlarm(alarm.id) }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = actions::onAddAlarm) {
                Text(
                    text = "ADD ALARM   +",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Eyebrow("POWER", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            ToggleRow(
                title = "Battery saver",
                subtitle = "Reduce the watch's background activity.",
                checked = state.batterySaver,
                onCheckedChange = actions::onToggleBatterySaver
            )
        }

        Spacer(Modifier.height(22.dp))
        Eyebrow("THIS PHONE", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            Text(
                text = "Forgetting removes the saved binding on this phone only. The watch keeps its own pairing until you re-pair.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            DangerButton(
                label = "Forget watch",
                enabled = state.watch.address.isNotEmpty(),
                onClick = actions::onForgetWatch
            )
        }

        Spacer(Modifier.height(26.dp))
        Text(
            text = "SETTINGS ARE SENT AS SINGLE COMMANDS\nEACH REPLY IS SHOWN ABOVE\nDND AND BRIGHT SCREEN ARE NOT SUPPORTED BY THIS FIRMWARE",
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WatchCard(watch: WatchInfoUi, actions: SettingsActions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (watch.connected) "●" else "○",
                    color = if (watch.connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = if (watch.connected) "CONNECTED" else "NOT CONNECTED",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.2.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = watch.name,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (watch.address.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = watch.address,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                InfoPill(
                    if (watch.battery > 0) "${watch.battery}%" else "BATTERY —",
                    Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                InfoPill(
                    if (watch.firmware.isNotEmpty()) "FW ${watch.firmware}" else "FW —",
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                label = "Refresh watch info",
                modifier = Modifier.fillMaxWidth(),
                onClick = actions::onRefreshInfo
            )
        }
    }
}

@Composable
private fun InfoPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
private fun AlarmRow(alarm: AlarmUi, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = clock(alarm.hour, alarm.minute),
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(onClick = onRemove) {
            Text(
                text = "REMOVE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DangerButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusLine(text: String, isError: Boolean) {
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.3.sp,
            color = color
        )
    }
}

private fun clock(hour: Int, minute: Int): String =
    String.format(Locale.US, "%02d:%02d", hour, minute)
