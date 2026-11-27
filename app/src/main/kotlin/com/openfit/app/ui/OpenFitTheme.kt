package com.openfit.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfit.app.R

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
 * Bundled OFL faces. DM Serif Display carries the page titles; Doto is the dot-matrix face for
 * hero numerals. Both files live in `res/font` so the look survives without a network connection.
 */
internal val SerifDisplay = FontFamily(
    Font(R.font.dm_serif_display, FontWeight.Normal)
)

internal val DotMatrix = FontFamily(
    Font(R.font.doto, FontWeight.Bold)
)

/**
 * The M3 baseline styles with the expressive weights and tracking, plus the two bundled faces:
 * display styles become dot-matrix, and the headline/title-large styles become the serif.
 */
private val OpenFitTypography = Typography(
    displayLarge = OpenFitBaseTypography.displayLarge.copy(
        fontFamily = DotMatrix,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp
    ),
    displayMedium = OpenFitBaseTypography.displayMedium.copy(
        fontFamily = DotMatrix,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = OpenFitBaseTypography.displaySmall.copy(
        fontFamily = DotMatrix,
        fontWeight = FontWeight.Bold
    ),
    headlineLarge = OpenFitBaseTypography.headlineLarge.copy(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = OpenFitBaseTypography.headlineMedium.copy(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Normal
    ),
    headlineSmall = OpenFitBaseTypography.headlineSmall.copy(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = OpenFitBaseTypography.titleLarge.copy(
        fontFamily = SerifDisplay,
        fontWeight = FontWeight.Normal
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
