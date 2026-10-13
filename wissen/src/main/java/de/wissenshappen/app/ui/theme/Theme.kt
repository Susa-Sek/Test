package de.wissenshappen.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Fest dunkel, unabhängig vom Systemthema.
 *
 * Der Feed ist Vollbild-Inhalt mit Bildern; auf hellem Grund flackert jeder Kartenwechsel.
 * Dieselbe Entscheidung treffen Reels und Shorts, und sie ist hier richtig.
 */
@Composable
fun WissenshappenTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}
