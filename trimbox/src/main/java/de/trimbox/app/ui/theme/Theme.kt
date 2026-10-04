package de.trimbox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Anders als die beiden Geschwister-Apps folgt TrimBox dem Systemthema: Hier werden Listen
 * gelesen und Häkchen gesetzt, oft abends — ein fest helles Bild wäre eine Zumutung.
 */
private val Pine = Color(0xFF12433B)
private val Mint = Color(0xFF4CC38A)
private val Amber = Color(0xFFF2C94C)

private val LightScheme = lightColorScheme(
    primary = Pine,
    secondary = Mint,
    tertiary = Amber,
    background = Color(0xFFFBFAF7),
    surface = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Mint,
    secondary = Mint,
    tertiary = Amber,
    background = Color(0xFF101413),
    surface = Color(0xFF181D1C),
)

@Composable
fun TrimBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
