package com.openfit.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * OpenFit's Material 3 Expressive theme.
 *
 * The palette mirrors the Java dashboard (near-black surfaces, white text, one red accent) so
 * Compose cards sit next to the remaining Views screens without a visual seam. The expressive
 * motion scheme is what gives the new components their spring-based transitions.
 */
private val OpenFitColors = darkColorScheme(
    primary = Color(0xFFF5283C),
    onPrimary = Color(0xFF1A0004),
    primaryContainer = Color(0xFF4A0910),
    onPrimaryContainer = Color(0xFFFFD9DC),
    secondary = Color(0xFF9A9A9A),
    onSecondary = Color(0xFF101010),
    background = Color(0xFF080808),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1B1B1B),
    onSurfaceVariant = Color(0xFF8F8F8F),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1E1E1E)
)

private val OpenFitShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OpenFitTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = OpenFitColors,
        motionScheme = MotionScheme.expressive(),
        shapes = OpenFitShapes,
        typography = Typography(),
        content = content
    )
}
