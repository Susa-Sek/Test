package de.trimbox.app.data

/**
 * Wertet die Abmelde-Kopfzeilen einer Mail aus — `List-Unsubscribe` (RFC 2369) und
 * `List-Unsubscribe-Post` (RFC 8058).
 *
 * Das ist die fehleranfälligste Stelle der App und liegt deshalb vollständig ohne Netz,
 * ohne Android und ohne JavaMail hier drin. Was in freier Wildbahn vorkommt und hier
 * abgefangen wird: mehrere URIs in einer Zeile, gefaltete Kopfzeilen mit Zeilenumbruch
 * mittendrin, fehlende spitze Klammern, Grossschreibung an jeder erdenklichen Stelle.
 *
 * Der wichtigste Unterschied ist der zwischen [Route.OneClick] und [Route.OpenInBrowser]:
 * Nur wenn der Absender per `List-Unsubscribe-Post` ausdrücklich zusagt, dass ein POST
 * genügt, darf die App von sich aus abmelden. Ein blindes GET auf eine beliebige
 * Abmelde-Adresse kann alles Mögliche sein — eine Bestätigungsseite, ein Zähl-Pixel, im
 * schlimmsten Fall eine Anmeldung.
 */
object UnsubscribeHeader {

    /** Was in den Kopfzeilen angeboten wird. */
    data class Options(
        val httpsUrl: String? = null,
        val httpUrl: String? = null,
        val mailto: MailtoTarget? = null,
        val oneClickAllowed: Boolean = false,
    ) {
        val isEmpty: Boolean get() = httpsUrl == null && httpUrl == null && mailto == null
    }

    /** Ziel einer `mailto:`-Abmeldung, schon zerlegt. */
    data class MailtoTarget(
        val address: String,
        val subject: String?,
        val body: String?,
    )

    /** Der Weg, den die App tatsächlich gehen kann. */
    sealed interface Route {
        /** Automatisch: POST nach RFC 8058, vom Absender ausdrücklich erlaubt. */
        data class OneClick(val url: String) : Route

        /** Automatisch: Abmelde-Mail über den eigenen Postausgang. */
        data class SendMail(val target: MailtoTarget) : Route

        /** Von Hand: Link im Browser öffnen, weil ein stilles GET nicht verantwortbar ist. */
        data class OpenInBrowser(val url: String) : Route

        /** Der Absender bietet keinen Weg an. */
        data object None : Route
    }

    /**
     * @param listUnsubscribe alle Werte der Kopfzeile `List-Unsubscribe` (eine Mail darf sie
     *   mehrfach tragen; die Werte werden zusammen betrachtet).
     * @param listUnsubscribePost der Wert von `List-Unsubscribe-Post`, falls vorhanden.
     */
    fun parse(listUnsubscribe: List<String>, listUnsubscribePost: List<String> = emptyList()): Options {
        val uris = listUnsubscribe.flatMap { extractUris(it) }

        var https: String? = null
        var http: String? = null
        var mailto: MailtoTarget? = null

        for (uri in uris) {
            when {
                // Erstes Vorkommen gewinnt: Absender listen den bevorzugten Weg zuerst.
                uri.startsWith("https://", ignoreCase = true) -> if (https == null) https = uri
                uri.startsWith("http://", ignoreCase = true) -> if (http == null) http = uri
                uri.startsWith("mailto:", ignoreCase = true) ->
                    if (mailto == null) mailto = parseMailto(uri)
            }
        }

        val oneClick = listUnsubscribePost.any { value ->
            value.split(',', ';').any { part ->
                part.trim().replace(" ", "").equals("List-Unsubscribe=One-Click", ignoreCase = true)
            }
        }

        return Options(
            httpsUrl = https,
            httpUrl = http,
            mailto = mailto,
            // RFC 8058 verlangt https. Über http wäre die Abmeldung im Klartext unterwegs,
            // und die enthält eine Kennung, die auf das Postfach zeigt.
            oneClickAllowed = oneClick && https != null,
        )
    }

    /** Der beste Weg, den die App gehen kann: automatisch vor manuell. */
    fun route(options: Options): Route = when {
        options.oneClickAllowed && options.httpsUrl != null -> Route.OneClick(options.httpsUrl)
        options.mailto != null -> Route.SendMail(options.mailto)
        options.httpsUrl != null -> Route.OpenInBrowser(options.httpsUrl)
        options.httpUrl != null -> Route.OpenInBrowser(options.httpUrl)
        else -> Route.None
    }

    /**
     * Holt die URIs aus einem Kopfzeilenwert.
     *
     * Der Normalfall ist `<https://…>, <mailto:…>`. Fehlen die spitzen Klammern — was
     * gegen RFC 2369 verstösst, aber vorkommt — wird der Rest an Kommas und Leerraum
     * zerlegt, damit die Mail nicht wortlos durchfällt.
     */
    private fun extractUris(raw: String): List<String> {
        // Gefaltete Kopfzeilen bringen Zeilenumbrüche und Einrückung mit.
        val value = raw.replace(FOLDING, " ").trim()
        if (value.isEmpty()) return emptyList()

        val bracketed = ANGLE_BRACKETS.findAll(value)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (bracketed.isNotEmpty()) return bracketed

        return value.split(',', ' ', '\t')
            .map { it.trim() }
            .filter { it.contains(':') }
    }

    /**
     * Zerlegt `mailto:adresse?subject=…&body=…` nach RFC 6068.
     *
     * Bewusst kein `URLDecoder`: der macht aus `+` ein Leerzeichen. In einer mailto-Anfrage
     * ist `+` aber ein gewöhnliches Zeichen — und steckt regelmässig in genau den
     * Kennungen, mit denen der Absender das Abo wiedererkennt (`unsub+a1b2@…`).
     */
    private fun parseMailto(uri: String): MailtoTarget? {
        val rest = uri.substring("mailto:".length)
        val address = percentDecode(rest.substringBefore('?')).trim()
        if (!address.contains('@')) return null

        val query = rest.substringAfter('?', "")
        var subject: String? = null
        var body: String? = null
        if (query.isNotEmpty()) {
            for (pair in query.split('&')) {
                val name = pair.substringBefore('=').trim()
                val value = percentDecode(pair.substringAfter('=', ""))
                when {
                    name.equals("subject", ignoreCase = true) -> subject = value.ifBlank { null }
                    name.equals("body", ignoreCase = true) -> body = value.ifBlank { null }
                }
            }
        }
        return MailtoTarget(address, subject, body)
    }

    private fun percentDecode(value: String): String {
        if (!value.contains('%')) return value
        // Prozentkodierung steht auf Bytes, nicht auf Zeichen. Erst die Bytes einsammeln,
        // dann als UTF-8 lesen — sonst zerfaellt jeder Umlaut in "Ã¤".
        val bytes = java.io.ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            val hex = if (char == '%' && index + 2 < value.length) {
                value.substring(index + 1, index + 3).toIntOrNull(16)
            } else {
                null
            }
            if (hex != null) {
                bytes.write(hex)
                index += 3
            } else {
                bytes.write(char.toString().toByteArray(Charsets.UTF_8))
                index++
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    private val ANGLE_BRACKETS = Regex("<([^<>]*)>")
    private val FOLDING = Regex("\\s*\\r?\\n\\s*")
}
