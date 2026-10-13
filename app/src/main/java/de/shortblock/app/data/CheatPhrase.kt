package de.shortblock.app.data

/**
 * Der Satz, den man zum Anfordern abtippen muss.
 *
 * Verglichen wird nach Sinn, nicht nach Zeichen: Ein Tippfehler in Groß-/Kleinschreibung oder ein
 * doppeltes Leerzeichen darf nicht zur Schikane werden — die Hürde soll die Gedankenlosigkeit
 * treffen, nicht die Feinmotorik. Satzzeichen zählen dagegen mit; sonst schrumpft der Satz beim
 * Tippen unbemerkt zusammen und die Hürde mit ihm.
 */
object CheatPhrase {

    fun matches(typed: String, expected: String): Boolean {
        val normalized = normalize(typed)
        return normalized.isNotEmpty() && normalized == normalize(expected)
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}
