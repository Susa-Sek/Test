package de.klarzeit.app.data

/**
 * Millisekunden als Zeitangabe, wie ein Mensch sie sagt.
 *
 * Bewusst ohne `DateUtils` oder Locale-Formatierer: Die Ausgabe wird auch im Widget
 * gebraucht, wo jede Fehlerquelle als leerer Kasten auf dem Startbildschirm endet.
 * Abgerundet wird immer — 89 Sekunden sind "1m", nicht "2m". Wer seine Bildschirmzeit
 * ansieht, soll keine Zahl bekommen, die grösser ist als die Wahrheit.
 */
object TimeFormat {

    /** "2h 14m", "14m", "0m". */
    fun short(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val totalMinutes = safe / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /** Für Fliesstext: "2 Stunden 14 Minuten". */
    fun long(millis: Long): String {
        val safe = millis.coerceAtLeast(0)
        val totalMinutes = safe / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val hourPart = when (hours) {
            0L -> null
            1L -> "1 Stunde"
            else -> "$hours Stunden"
        }
        val minutePart = when (minutes) {
            1L -> "1 Minute"
            else -> "$minutes Minuten"
        }
        return listOfNotNull(hourPart, minutePart).joinToString(" ")
    }
}
