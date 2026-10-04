package de.klarzeit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF1B2A4A)
private val Mint = Color(0xFF4CC38A)
private val Amber = Color(0xFFF2C94C)

private val LightScheme = lightColorScheme(
    primary = Navy,
    secondary = Mint,
    tertiary = Amber,
    background = Color(0xFFFBFAF7),
    surface = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Mint,
    secondary = Mint,
    tertiary = Amber,
    background = Color(0xFF101318),
    surface = Color(0xFF181C22),
)

@Composable
fun KlarzeitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
