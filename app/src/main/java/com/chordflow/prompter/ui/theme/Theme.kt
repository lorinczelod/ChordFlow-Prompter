package com.chordflow.prompter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Strict monochrome stage palette: pure black, phosphor green. Nothing else. */
object Phosphor {
    val Black = Color(0xFF000000)
    val Green = Color(0xFF00FF41)
    val GreenBright = Color(0xFF39FF14)
    val GreenDim = Color(0xFF0A8F28)
    val GreenFaint = Color(0xFF064D16)
    val GreenGhost = Color(0xFF02240A)
}

private val ChordFlowColors = darkColorScheme(
    primary = Phosphor.Green,
    onPrimary = Phosphor.Black,
    primaryContainer = Phosphor.GreenFaint,
    onPrimaryContainer = Phosphor.Green,
    secondary = Phosphor.GreenBright,
    onSecondary = Phosphor.Black,
    secondaryContainer = Phosphor.GreenGhost,
    onSecondaryContainer = Phosphor.Green,
    tertiary = Phosphor.GreenDim,
    onTertiary = Phosphor.Black,
    background = Phosphor.Black,
    onBackground = Phosphor.Green,
    surface = Phosphor.Black,
    onSurface = Phosphor.Green,
    surfaceVariant = Phosphor.Black,
    onSurfaceVariant = Phosphor.GreenDim,
    outline = Phosphor.GreenDim,
    outlineVariant = Phosphor.GreenFaint,
    error = Phosphor.GreenBright,
    onError = Phosphor.Black,
    scrim = Phosphor.Black
)

private val Mono = FontFamily.Monospace

private val ChordFlowTypography = Typography(
    displayLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 56.sp),
    headlineLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Mono, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 15.sp)
)

@Composable
fun ChordFlowTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Always the stage theme — a light sanctuary is still a dark stage.
    MaterialTheme(
        colorScheme = ChordFlowColors,
        typography = ChordFlowTypography,
        content = content
    )
}
