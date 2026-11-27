package com.openfit.app.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/** What the Home tab shows. [watchName] is the saved or connected watch name. */
data class HomeUiState(val watchName: String)

/** Navigation callbacks the Home screen needs from the activity. */
interface HomeActions {
    fun onManageWatch()
    fun onViewHealth()
}

/**
 * Hosts the Compose Home tab. The activity owns the tabs and the BLE state; this controller only
 * renders [HomeUiState] and forwards taps through [HomeActions].
 */
class HomeScreenController(private val actions: HomeActions) {
    private var state by mutableStateOf(HomeUiState("No watch connected"))

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "●",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "OPENFIT",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "/ DAILY DEVICE LOG",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        Spacer(Modifier.height(22.dp))

        Text(
            text = remember {
                SimpleDateFormat("EEE  d MMM", Locale.getDefault())
                    .format(Date()).uppercase(Locale.ROOT) + "  ·  HEALTH STATUS"
            },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(9.dp))
        Text(
            text = "Less noise.\nMore signal.",
            fontSize = 34.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = "Your health, at a glance.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(Modifier.padding(horizontal = 19.dp, vertical = 18.dp)) {
                Eyebrow("YOUR WATCH", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = state.watchName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Pair a watch to see supported data and connection details.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    label = "Manage watch   →",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = actions::onManageWatch
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SecondaryButton(
            label = "View health   →",
            modifier = Modifier.fillMaxWidth(),
            onClick = actions::onViewHealth
        )

        Spacer(Modifier.height(28.dp))
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
