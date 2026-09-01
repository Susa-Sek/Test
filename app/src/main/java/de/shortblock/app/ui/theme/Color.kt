package de.shortblock.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Die Farbwelt der App — fest, nicht vom Hintergrundbild geliehen.
 *
 * Bis v0.6 lief hier Material You, und die App übernahm die Farben des Nutzer-Wallpapers. Das
 * Ergebnis sah auf jedem Gerät anders und auf keinem nach etwas aus. Der Preis der Umstellung
 * ist genau diese Anpassung; der Gewinn ist ein Gesicht.
 *
 * **Ein Akzent, sonst nichts Buntes.** Bernstein trägt die große Zahl, den aktiven Reiter und
 * die gewählte Kontingent-Option. Grün heißt ausschließlich „läuft“, Rot ausschließlich
 * „kaputt“. Wer Farben für Dekoration verbraucht, kann mit ihnen später nichts mehr sagen.
 */
internal object Palette {

    // --- Dunkel (der Normalfall) ---
    val DarkBackground = Color(0xFF0E1815)
    val DarkSurface = Color(0xFF142320)
    val DarkSurfaceVariant = Color(0xFF1C2F2A)
    val DarkOutline = Color(0xFF2C443D)
    val DarkOnBackground = Color(0xFFECF2EF)
    val DarkOnSurfaceVariant = Color(0xFF93A9A1)

    val DarkAccent = Color(0xFFE8A33D)
    val DarkOnAccent = Color(0xFF2A1B02)
    val DarkAccentContainer = Color(0xFF3A2A10)

    val DarkGood = Color(0xFF6FBF9B)
    val DarkOnGood = Color(0xFF06251A)
    val DarkGoodContainer = Color(0xFF15352A)

    val DarkAlert = Color(0xFFE4796B)
    val DarkOnAlert = Color(0xFF3A0F09)
    val DarkAlertContainer = Color(0xFF3B1D18)

    // --- Hell ---
    val LightBackground = Color(0xFFF6F4EF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFECEDE7)
    val LightOutline = Color(0xFFD9DED7)
    val LightOnBackground = Color(0xFF16211D)
    val LightOnSurfaceVariant = Color(0xFF5B6B64)

    val LightAccent = Color(0xFFB07216)
    val LightOnAccent = Color(0xFFFFFFFF)
    val LightAccentContainer = Color(0xFFF6E4C6)

    val LightGood = Color(0xFF237051)
    val LightOnGood = Color(0xFFFFFFFF)
    val LightGoodContainer = Color(0xFFD3EADF)

    val LightAlert = Color(0xFFB3402F)
    val LightOnAlert = Color(0xFFFFFFFF)
    val LightAlertContainer = Color(0xFFF7DCD7)
}
