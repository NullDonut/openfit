package com.openfit.app.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import java.util.Locale

/**
 * The locally logged sleep duration. [minutes] of 0 means no entry; [label] is "LAST NIGHT" or
 * the stored date in uppercase.
 */
data class SleepUiState(val minutes: Int, val label: String)

/** The only action the Sleep tab needs from the activity: open the duration editor. */
fun interface SleepActions {
    fun onEdit()
}

class SleepScreenController(private val actions: SleepActions) {
    private var state by mutableStateOf(SleepUiState(0, ""))

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
        Text(
            text = "Personal log",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(22.dp))

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
                    Text(
                        text = "◌",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (state.minutes > 0) state.label else "NO ENTRY YET",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.minutes > 0) formatDuration(state.minutes) else "—",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.minutes > 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "A private note, stored only on this phone.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))
                SecondaryButton(
                    label = "Add sleep duration   +",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = actions::onEdit
                )
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return String.format(Locale.getDefault(), "%dh %02dm", hours, rest)
}
