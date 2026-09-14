package de.trimbox.app.data

/**
 * Bringt ein eingegebenes Passwort in die Form, die der Server erwartet.
 *
 * Der Grund ist eine Falle, in die praktisch jeder tappt: Google, Yahoo und Apple zeigen
 * ihre App-Passwörter als vier Vierergruppen an — `abcd efgh ijkl mnop`. Wer das markiert
 * und einfügt, hat die Leerzeichen mit dabei. Auf den Webseiten der Anbieter fallen sie
 * nicht auf, weil deren Anmeldeformular sie selbst entfernt; über IMAP wird das Passwort
 * dagegen unverändert übertragen, der Server lehnt ab, und die App meldet „Anmeldung
 * abgelehnt" — obwohl das Passwort richtig war.
 *
 * Entfernt werden Leerzeichen deshalb **nur** bei genau diesem Muster: sechzehn Buchstaben
 * oder Ziffern in vier Gruppen. Ein selbst vergebenes Passwort, in dem ein Leerzeichen
 * vorkommen darf, bleibt unangetastet.
 */
object AppPassword {

    fun normalize(raw: String): String {
        // Führender oder folgender Leerraum ist nie beabsichtigt; er stammt vom Einfügen.
        val trimmed = raw.trim()
        if (!trimmed.contains(' ')) return trimmed

        val withoutSpaces = trimmed.replace(" ", "")
        return if (APP_PASSWORD.matches(withoutSpaces) && GROUPED.matches(trimmed)) {
            withoutSpaces
        } else {
            trimmed
        }
    }

    /** Sechzehn Buchstaben oder Ziffern — das Format aller drei grossen Anbieter. */
    private val APP_PASSWORD = Regex("^[A-Za-z0-9]{16}$")

    /** Und nur, wenn sie auch wirklich als vier Vierergruppen dastehen. */
    private val GROUPED = Regex("^[A-Za-z0-9]{4}( [A-Za-z0-9]{4}){3}$")
}
