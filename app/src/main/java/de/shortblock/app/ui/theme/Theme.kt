package de.shortblock.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Feste Farbschemata statt Material You.
 *
 * Die Zuordnung ist die eigentliche Aussage: `tertiary` ist durchgehend „läuft/gut“ und `error`
 * durchgehend „kaputt“. Wer eine Karte einfärbt, greift zu einer dieser Rollen — nie zu einer
 * Farbe direkt. Sonst driften die Bedeutungen mit jedem neuen Bildschirm auseinander.
 */
private val DarkScheme = darkColorScheme(
    primary = Palette.DarkAccent,
    onPrimary = Palette.DarkOnAccent,
    primaryContainer = Palette.DarkAccentContainer,
    onPrimaryContainer = Palette.DarkAccent,
    secondary = Palette.DarkOnSurfaceVariant,
    onSecondary = Palette.DarkBackground,
    tertiary = Palette.DarkGood,
    onTertiary = Palette.DarkOnGood,
    tertiaryContainer = Palette.DarkGoodContainer,
    onTertiaryContainer = Palette.DarkGood,
    error = Palette.DarkAlert,
    onError = Palette.DarkOnAlert,
    errorContainer = Palette.DarkAlertContainer,
    onErrorContainer = Palette.DarkAlert,
    background = Palette.DarkBackground,
    onBackground = Palette.DarkOnBackground,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkOnBackground,
    surfaceVariant = Palette.DarkSurfaceVariant,
    onSurfaceVariant = Palette.DarkOnSurfaceVariant,
    surfaceContainer = Palette.DarkSurface,
    surfaceContainerHigh = Palette.DarkSurfaceVariant,
    outline = Palette.DarkOutline,
    outlineVariant = Palette.DarkOutline,
)

private val LightScheme = lightColorScheme(
    primary = Palette.LightAccent,
    onPrimary = Palette.LightOnAccent,
    primaryContainer = Palette.LightAccentContainer,
    onPrimaryContainer = Palette.LightAccent,
    secondary = Palette.LightOnSurfaceVariant,
    onSecondary = Palette.LightSurface,
    tertiary = Palette.LightGood,
    onTertiary = Palette.LightOnGood,
    tertiaryContainer = Palette.LightGoodContainer,
    onTertiaryContainer = Palette.LightGood,
    error = Palette.LightAlert,
    onError = Palette.LightOnAlert,
    errorContainer = Palette.LightAlertContainer,
    onErrorContainer = Palette.LightAlert,
    background = Palette.LightBackground,
    onBackground = Palette.LightOnBackground,
    surface = Palette.LightSurface,
    onSurface = Palette.LightOnBackground,
    surfaceVariant = Palette.LightSurfaceVariant,
    onSurfaceVariant = Palette.LightOnSurfaceVariant,
    surfaceContainer = Palette.LightSurface,
    surfaceContainerHigh = Palette.LightSurfaceVariant,
    outline = Palette.LightOutline,
    outlineVariant = Palette.LightOutline,
)

@Composable
fun ShortBlockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = ShortBlockTypography,
        content = content,
    )
}
