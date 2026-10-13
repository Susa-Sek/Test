package de.trimbox.app.data

/**
 * Bringt Absenderangaben auf eine Form, unter der sich Mails zusammenzählen lassen.
 *
 * Ohne das steht derselbe Newsletter dreimal in der Liste, weil `From` mal
 * `Netflix <no-reply@netflix.com>`, mal `"Netflix" <No-Reply@Netflix.com>` und mal nur
 * `no-reply@netflix.com` lautet — und niemand meldet sich bei einer Liste ab, die er
 * für drei verschiedene Absender hält.
 */
object SenderKey {

    data class Sender(
        /** Kleingeschrieben, ohne Anzeigename — der Schlüssel zum Gruppieren. */
        val address: String,
        /** Was dem Nutzer gezeigt wird; leer, wenn der Absender keinen Namen mitschickt. */
        val displayName: String,
    ) {
        val domain: String get() = address.substringAfterLast('@', "")
    }

    /**
     * Zerlegt einen rohen `From`-Wert. Gibt `null` zurück, wenn keine brauchbare Adresse
     * darin steckt — dann fällt die Mail aus der Auswertung, statt eine Gruppe "" zu bilden.
     */
    fun fromHeader(raw: String?): Sender? {
        val value = raw?.replace(FOLDING, " ")?.trim().orEmpty()
        if (value.isEmpty()) return null

        // Mehrere Absender sind erlaubt, aber selten; der erste zählt.
        val first = splitTopLevel(value).firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) return null

        val angle = ANGLE.find(first)
        return if (angle != null) {
            val address = normalize(angle.groupValues[1]) ?: return null
            Sender(address, cleanDisplayName(first.removeRange(angle.range)))
        } else {
            // Form "adresse@example.org (Anzeigename)" oder nackte Adresse.
            val paren = PAREN.find(first)
            val addressPart = if (paren != null) first.removeRange(paren.range) else first
            val address = normalize(addressPart) ?: return null
            Sender(address, cleanDisplayName(paren?.groupValues?.get(1).orEmpty()))
        }
    }

    /** Für den Fall, dass JavaMail die Adresse schon herausgelöst hat. */
    fun normalize(rawAddress: String?): String? {
        val address = rawAddress?.trim()?.trim('<', '>', '"', ' ')?.lowercase().orEmpty()
        if (address.count { it == '@' } != 1) return null
        val local = address.substringBefore('@')
        val domain = address.substringAfter('@')
        if (local.isEmpty() || domain.isEmpty() || !domain.contains('.')) return null
        return address
    }

    private fun cleanDisplayName(raw: String): String =
        raw.trim().trim(',').trim().removeSurrounding("\"").trim()

    /** Zerlegt an Kommas, die nicht in Anführungszeichen stehen. */
    private fun splitTopLevel(value: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in value) {
            when {
                char == '"' -> { inQuotes = !inQuotes; current.append(char) }
                char == ',' && !inQuotes -> { parts += current.toString(); current.clear() }
                else -> current.append(char)
            }
        }
        parts += current.toString()
        return parts
    }

    private val ANGLE = Regex("<([^<>]*)>")
    private val PAREN = Regex("\\(([^()]*)\\)")
    private val FOLDING = Regex("\\s*\\r?\\n\\s*")
}
