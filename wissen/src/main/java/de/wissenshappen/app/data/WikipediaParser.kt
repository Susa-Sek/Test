package de.wissenshappen.app.data

import org.json.JSONObject

/**
 * Wandelt Wikipedia-Antworten in Karten um — reine Funktionen auf Strings, damit der Teil,
 * in dem die Fehler stecken, ohne Netzwerk und ohne Gerät testbar ist.
 *
 * Der wichtigste Teil ist nicht das Parsen, sondern [isWorthShowing]. Wikipedia liefert auf
 * Suchanfragen zuverlässig auch Begriffsklärungen, Namenslisten und Denkmalverzeichnisse.
 * Ein Feed, der solche Einträge zeigt, ist wertlos — man wischt weiter, ohne etwas gelernt zu
 * haben, und genau das soll die App ja abschaffen.
 */
object WikipediaParser {

    /** Kürzer als das ist kein Happen, sondern ein Stichwort. */
    private const val MIN_TEXT_LENGTH = 120

    private val TITLE_REJECTS = listOf(
        "liste der",
        "liste von",
        "(begriffsklärung)",
        "(disambiguation)",
    )

    private val TEXT_REJECTS = listOf(
        "ist der familienname folgender personen",
        "ist ein familienname",
        "ist ein weiblicher vorname",
        "ist ein männlicher vorname",
        "steht für:",
        "bezeichnet:",
        "diese liste",
        "in der liste",
        "ist eine begriffsklärung",
        "kann sich beziehen auf",
    )

    /**
     * Antwort von `action=query&generator=search&prop=extracts|pageimages`.
     */
    fun parseSearchResponse(json: String, topic: String): List<Card> {
        val pages = runCatching {
            JSONObject(json).optJSONObject("query")?.optJSONObject("pages")
        }.getOrNull() ?: return emptyList()

        return pages.keys().asSequence()
            .mapNotNull { key -> pages.optJSONObject(key) }
            .mapNotNull { page ->
                val title = page.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val text = cleanExtract(page.optString("extract"))
                if (!isWorthShowing(title, text)) return@mapNotNull null
                Card(
                    id = "page-${page.optInt("pageid")}",
                    title = title,
                    text = text,
                    imageUrl = page.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null },
                    sourceUrl = articleUrl(title),
                    kind = CardKind.TOPIC,
                    topic = topic,
                )
            }
            // Wikipedia liefert die Reihenfolge über "index"; die Map-Keys sind unsortiert.
            .sortedBy { it.id }
            .toList()
    }

    /**
     * Antwort von `/api/rest_v1/feed/featured/JJJJ/MM/TT` — Artikel des Tages und
     * "Was geschah heute". Die beiden sind redaktionell kuratiert und damit die
     * verlässlichste Qualitätsquelle, die Wikipedia kostenlos anbietet.
     */
    fun parseFeaturedResponse(json: String, maxOnThisDay: Int = 3): List<Card> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val cards = mutableListOf<Card>()

        root.optJSONObject("tfa")?.let { tfa ->
            val title = tfa.optJSONObject("titles")?.optString("normalized").orEmpty()
                .ifBlank { tfa.optString("title") }
            val text = cleanExtract(tfa.optString("extract"))
            if (title.isNotBlank() && text.isNotBlank()) {
                cards += Card(
                    id = "tfa-$title",
                    title = title,
                    text = text,
                    imageUrl = tfa.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null },
                    sourceUrl = articleUrl(title),
                    kind = CardKind.TODAY,
                    topic = null,
                )
            }
        }

        val events = root.optJSONArray("onthisday")
        if (events != null) {
            for (index in 0 until minOf(events.length(), maxOnThisDay)) {
                val event = events.optJSONObject(index) ?: continue
                val year = event.opt("year")?.toString()?.takeIf { it != "null" }
                val text = cleanExtract(event.optString("text"))
                if (text.isBlank()) continue
                val page = event.optJSONArray("pages")?.optJSONObject(0)
                val pageTitle = page?.optJSONObject("titles")?.optString("normalized")
                    ?: page?.optString("title").orEmpty()
                cards += Card(
                    id = "otd-$year-${text.take(24)}",
                    title = year?.let { "Im Jahr $it" } ?: "An diesem Tag",
                    text = text,
                    imageUrl = page?.optJSONObject("thumbnail")?.optString("source")?.ifBlank { null },
                    sourceUrl = if (pageTitle.isNotBlank()) articleUrl(pageTitle) else WIKI_BASE,
                    kind = CardKind.ON_THIS_DAY,
                    topic = null,
                )
            }
        }
        return cards
    }

    /**
     * Der Qualitätsfilter. Lieber eine Karte zu wenig als eine Namensliste im Feed.
     */
    fun isWorthShowing(title: String, text: String): Boolean {
        if (text.length < MIN_TEXT_LENGTH) return false

        val lowerTitle = title.lowercase()
        if (TITLE_REJECTS.any { lowerTitle.startsWith(it) || lowerTitle.contains(it) }) return false
        // Reine Jahreszahlen und Datumsartikel sind Chroniken, keine Happen.
        if (lowerTitle.matches(YEAR_ONLY)) return false

        val lowerText = text.lowercase()
        if (TEXT_REJECTS.any { lowerText.contains(it) }) return false

        return true
    }

    private fun cleanExtract(raw: String?): String =
        raw.orEmpty()
            .replace('…', '…')
            .replace(MULTI_NEWLINE, " ")
            .trim()
            .removeSuffix("…")
            .trim()

    private fun articleUrl(title: String): String =
        WIKI_BASE + "/wiki/" + title.replace(' ', '_')

    private const val WIKI_BASE = "https://de.wikipedia.org"
    private val MULTI_NEWLINE = Regex("\\s*\\n+\\s*")
    private val YEAR_ONLY = Regex("^\\d{3,4}$")
}
