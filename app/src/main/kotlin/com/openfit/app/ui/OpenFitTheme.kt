package com.openfit.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * OpenFit's Compose theme.
 *
 * The palette mirrors the Java dashboard (near-black surfaces, white text, one red accent) so
 * Compose cards sit next to the remaining Views screens without a visual seam.
 *
 * Note on Material 3 Expressive: `MaterialExpressiveTheme`, `MotionScheme` and the expressive
 * components only became public API in material3 1.5.0, which requires compileSdk 37 and
 * AGP 9.1. This module builds with compileSdk 35 / AGP 8.6.1, so material3 1.4.0 is the newest
 * usable release. The expressive *character* is reproduced here with the stable API surface:
 * oversized corner radii, emphasized (tighter, heavier) display/title styles, and spring-based
 * size/colour motion at the call sites.
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

private val OpenFitBaseTypography = Typography()

/**
 * The M3 baseline styles with the heavier weights and tighter tracking used by the expressive
 * type scale. Only the styles the dashboard actually uses are overridden.
 */
private val OpenFitTypography = Typography(
    displayLarge = OpenFitBaseTypography.displayLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-2).sp
    ),
    headlineSmall = OpenFitBaseTypography.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = OpenFitBaseTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = OpenFitBaseTypography.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp
    )
)

@Composable
fun OpenFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenFitColors,
        shapes = OpenFitShapes,
        typography = OpenFitTypography,
        content = content
    )
}
