package com.openfit.app.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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

/** One face the watch reported on SE id 80. [id] is the watch's own opaque string. */
data class WatchFaceUi(
    val id: String,
    val current: Boolean,
    val removable: Boolean
)

/**
 * Everything the Faces screen renders. The activity owns these values and pushes a new state
 * after each SE reply, so the screen only ever reflects what the watch last said.
 */
data class FacesUiState(
    val supported: Boolean,
    val connected: Boolean,
    val faces: List<WatchFaceUi>,
    val ruleCount: Int,
    val status: String,
    val statusIsError: Boolean,
    val loading: Boolean,
    /** True only on the ZhBracelet / ColorFit profile, where setting a current face is wired. */
    val canSet: Boolean = false,
    /** True only when a custom dial can be installed right now (Buzz, connected, idle). */
    val canInstall: Boolean = false,
    /** 0..100 while a custom-face upload is streaming, else -1. */
    val uploadProgress: Int = -1
)

/** Every action the Faces screen can start. */
interface FacesActions {
    fun onRefresh()
    fun onSetFace(id: String)
    fun onInstallCustomFace()
}

class FacesScreenController(private val actions: FacesActions) {
    private var state by mutableStateOf(
        FacesUiState(
            supported = false,
            connected = false,
            faces = emptyList(),
            ruleCount = -1,
            status = "",
            statusIsError = false,
            loading = false,
            canSet = false
        )
    )

    fun update(next: FacesUiState) {
        state = next
    }

    fun createView(context: Context): View = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            OpenFitTheme {
                FacesScreen(state, actions)
            }
        }
    }
}

@Composable
private fun FacesScreen(state: FacesUiState, actions: FacesActions) {
    Column(Modifier.fillMaxWidth()) {
        Eyebrow("WATCH FACES")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "On the wrist",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                state.canInstall ->
                    "Tap a face to make it current, or install a custom dial from this phone. " +
                        "Nothing is uploaded until you pick a file and confirm; the current face " +
                        "is kept."
                state.canSet ->
                    "Tap a face to make it current. The watch confirms the change; you can undo " +
                        "it on the watch."
                else ->
                    "Read-only for now. This lists the faces the watch reports and marks the one " +
                        "it is using."
            },
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FacesStatus(state.status, state.statusIsError)
        }

        Spacer(Modifier.height(20.dp))
        when {
            !state.supported -> FacesCard {
                Text(
                    text = "Watch faces are not available on this watch. This screen is enabled for the ColorFit Ultra Buzz (ZhBracelet) and SE / Watch Beat profiles.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            !state.connected -> FacesCard {
                Text(
                    text = "Connect the watch to read its faces.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                FacesCard {
                    when {
                        state.loading && state.faces.isEmpty() -> Text(
                            text = "READING THE WATCH…",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        state.faces.isEmpty() -> Text(
                            text = "The watch reported no faces yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                        else -> {
                            for ((index, face) in state.faces.withIndex()) {
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                                FaceRow(face, state.canSet && !state.loading, actions)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    label = when {
                        state.loading -> "Reading…"
                        state.uploadProgress >= 0 -> "Installing…"
                        else -> "Refresh faces"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (!state.loading && state.uploadProgress < 0) actions.onRefresh()
                    }
                )
                if (state.canInstall) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(
                        label = if (state.uploadProgress >= 0) {
                            "Installing face..."
                        } else {
                            "Install custom face"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (!state.loading && state.uploadProgress < 0) {
                                actions.onInstallCustomFace()
                            }
                        }
                    )
                }
                if (state.uploadProgress >= 0) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { state.uploadProgress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "UPLOADING CUSTOM FACE  " +
                            state.uploadProgress.coerceIn(0, 100) + "%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.ruleCount >= 0) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = "CURRENT FACE RULES  " + state.ruleCount,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(26.dp))
        Text(
            text = when {
                state.canInstall ->
                    "TAP A FACE TO MAKE IT CURRENT\nINSTALL CUSTOM FACE IS LIVE · IT ASKS FIRST"
                state.canSet ->
                    "TAP A FACE TO MAKE IT CURRENT\nSETTING A FACE IS LIVE"
                else ->
                    "READ-ONLY DISCOVERY\nFACE SWITCHING AND CUSTOM UPLOAD COME NEXT"
            },
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
private fun FaceRow(face: WatchFaceUi, canSet: Boolean, actions: FacesActions) {
    // Only a non-current face on the set-capable profile is tappable; the activity confirms.
    val settable = canSet && !face.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (settable) it.clickable { actions.onSetFace(face.id) } else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val label = if (face.id.isEmpty()) "UNKNOWN" else face.id.uppercase(Locale.ROOT)
        Column(Modifier.weight(1f)) {
            Text(
                text = "FACE " + label,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (face.removable) "Stored on the watch" else "Built in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            face.current -> Text(
                text = "CURRENT",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            settable -> Text(
                text = "TAP TO SET",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FacesCard(content: @Composable () -> Unit) {
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
private fun FacesStatus(text: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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