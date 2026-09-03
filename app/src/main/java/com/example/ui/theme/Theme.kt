package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BentoColorScheme = lightColorScheme(
    primary = BentoPrimary,
    onPrimary = Color.White,
    primaryContainer = BentoHeroContainer,
    onPrimaryContainer = BentoHeroOnContainer,
    secondary = BentoTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = BentoTileBg,
    onSecondaryContainer = BentoTextPrimary,
    tertiary = BentoStreakText,
    onTertiary = Color.White,
    background = BentoBackground,
    onBackground = BentoTextPrimary,
    surface = BentoSurfaceCard,
    onSurface = BentoTextPrimary,
    surfaceVariant = BentoTileBg,
    onSurfaceVariant = BentoTextSecondary,
    outline = BentoTileBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BentoColorScheme,
        typography = Typography,
        content = content
    )
}


