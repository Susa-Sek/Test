package de.wissenshappen.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holt Karten von Wikipedia. Kostenlos, ohne Schlüssel, ohne Konto.
 *
 * Bewusst mit `HttpURLConnection` und `org.json` statt einer HTTP-Bibliothek: Die zwei
 * Endpunkte hier sind schlicht, und jede weitere Abhängigkeit ist eine, die bei einem
 * Android-Update bricht.
 *
 * Wikipedia verlangt einen aussagekräftigen User-Agent — Anfragen ohne werden gedrosselt
 * oder abgewiesen.
 */
class WikipediaSource(
    private val language: String = "de",
    private val random: Random = Random.Default,
) {

    /**
     * Artikel zu einem Thema.
     *
     * Der zufällige Offset ist der Trick gegen Wiederholung: ohne ihn liefert die Suche jedes
     * Mal dieselben Top-Treffer, und der Feed fühlt sich nach zwei Tagen leer an.
     */
    suspend fun topicCards(topic: String, limit: Int = 8): List<Card> {
        val offset = random.nextInt(0, MAX_SEARCH_OFFSET)
        val url = buildString {
            append("https://$language.wikipedia.org/w/api.php")
            append("?action=query&format=json&formatversion=1")
            append("&generator=search")
            append("&gsrsearch=").append(encode(topic))
            append("&gsrlimit=").append(limit)
            append("&gsroffset=").append(offset)
            append("&gsrnamespace=0")
            append("&prop=extracts|pageimages")
            append("&exintro=1&explaintext=1&exchars=420")
            append("&piprop=thumbnail&pithumbsize=800")
        }
        return WikipediaParser.parseSearchResponse(get(url), topic)
    }

    /** Artikel des Tages und "Was geschah heute". */
    suspend fun todayCards(date: LocalDate = LocalDate.now()): List<Card> {
        val path = date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        val url = "https://$language.wikipedia.org/api/rest_v1/feed/featured/$path"
        return WikipediaParser.parseFeaturedResponse(get(url))
    }

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} für $url")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TIMEOUT_MS = 12_000
        const val MAX_SEARCH_OFFSET = 120
        const val USER_AGENT =
            "Wissenshappen/0.1 (https://github.com/Susa-Sek/Test; sideloaded personal app)"
    }
}
