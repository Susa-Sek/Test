package de.wissenshappen.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WikipediaParserTest {

    private fun searchJson(vararg pages: String) =
        """{"query":{"pages":{${pages.joinToString(",")}}}}"""

    private fun page(id: Int, title: String, extract: String, thumb: String? = null): String {
        val thumbnail = thumb?.let { ""","thumbnail":{"source":"$it"}""" } ?: ""
        return """"$id":{"pageid":$id,"title":"$title","extract":"$extract"$thumbnail}"""
    }

    private val goodExtract =
        "Die Apsis bezeichnet in der Astronomie einen der zwei Hauptscheitel der elliptischen " +
            "Umlaufbahn eines Himmelskoerpers um einen anderen als Zentralkoerper."

    @Test
    fun `a proper article becomes a card`() {
        val cards = WikipediaParser.parseSearchResponse(
            searchJson(page(1, "Apsis", goodExtract, "https://example.org/bild.png")),
            topic = "Astronomie",
        )

        assertEquals(1, cards.size)
        assertEquals("Apsis", cards[0].title)
        assertEquals("Astronomie", cards[0].topic)
        assertEquals(CardKind.TOPIC, cards[0].kind)
        assertEquals("https://example.org/bild.png", cards[0].imageUrl)
        assertEquals("https://de.wikipedia.org/wiki/Apsis", cards[0].sourceUrl)
    }

    @Test
    fun `spaces in the title become a usable link`() {
        val cards = WikipediaParser.parseSearchResponse(
            searchJson(page(2, "Apsis (Astronomie)", goodExtract)),
            topic = "Astronomie",
        )
        assertEquals("https://de.wikipedia.org/wiki/Apsis_(Astronomie)", cards[0].sourceUrl)
    }

    @Test
    fun `a missing image is fine`() {
        val cards = WikipediaParser.parseSearchResponse(
            searchJson(page(3, "Apsis", goodExtract)),
            topic = "Astronomie",
        )
        assertNull(cards[0].imageUrl)
    }

    // --- Qualitaetsfilter ----------------------------------------------------------------
    //
    // Der wichtigste Teil der App. Eine Suche auf Wikipedia liefert zuverlaessig auch
    // Begriffsklaerungen, Namenslisten und Denkmalverzeichnisse. Ein Feed, der die zeigt,
    // ist wertlos: Man wischt weiter, ohne etwas gelernt zu haben — genau das, was die App
    // eigentlich abschaffen soll.

    @Test
    fun `name lists are rejected`() {
        assertFalse(
            WikipediaParser.isWorthShowing(
                "Compaore",
                "Compaore ist der Familienname folgender Personen: Ana Peleteiro-Compaore, " +
                    "Blaise Compaore, Francois Compaore und weitere Traeger dieses Namens.",
            ),
        )
    }

    @Test
    fun `monument and other lists are rejected`() {
        assertFalse(
            WikipediaParser.isWorthShowing(
                "Liste der Kulturdenkmale in Havelberg",
                "In der Liste der Kulturdenkmale in Havelberg sind alle Kulturdenkmale der " +
                    "Hansestadt Havelberg in Sachsen-Anhalt aufgefuehrt und beschrieben.",
            ),
        )
    }

    @Test
    fun `disambiguation pages are rejected`() {
        assertFalse(WikipediaParser.isWorthShowing("Merkur (Begriffsklärung)", goodExtract))
        assertFalse(
            WikipediaParser.isWorthShowing(
                "Merkur",
                "Merkur steht für: den Planeten, den roemischen Gott und mehrere Zeitungen " +
                    "sowie weitere Bedeutungen dieses vielseitigen Begriffs.",
            ),
        )
    }

    /** Ein Stichwort ist kein Happen. */
    @Test
    fun `stubs that are too short are rejected`() {
        assertFalse(WikipediaParser.isWorthShowing("Irgendwas", "Ein sehr kurzer Satz."))
    }

    @Test
    fun `pure year articles are rejected`() {
        assertFalse(WikipediaParser.isWorthShowing("1983", goodExtract))
        assertTrue(WikipediaParser.isWorthShowing("1983 im Film", goodExtract))
    }

    @Test
    fun `rejected pages never reach the feed`() {
        val cards = WikipediaParser.parseSearchResponse(
            searchJson(
                page(1, "Apsis", goodExtract),
                page(2, "Liste der Kulturdenkmale in Havelberg", "In der Liste der Kulturdenkmale in Havelberg sind alle Denkmale aufgefuehrt und ausfuehrlich beschrieben."),
            ),
            topic = "Astronomie",
        )
        assertEquals(listOf("Apsis"), cards.map { it.title })
    }

    // --- Robustheit ----------------------------------------------------------------------

    @Test
    fun `broken json yields no cards instead of throwing`() {
        assertTrue(WikipediaParser.parseSearchResponse("kein json", "Astronomie").isEmpty())
        assertTrue(WikipediaParser.parseSearchResponse("", "Astronomie").isEmpty())
        assertTrue(WikipediaParser.parseFeaturedResponse("{kaputt").isEmpty())
    }

    @Test
    fun `an empty result yields no cards`() {
        assertTrue(WikipediaParser.parseSearchResponse("""{"query":{"pages":{}}}""", "X").isEmpty())
    }

    // --- Tagesfeed -----------------------------------------------------------------------

    @Test
    fun `featured feed yields article of the day and events`() {
        val json = """
            {
              "tfa": {
                "titles": {"normalized": "Orchideen"},
                "extract": "$goodExtract",
                "thumbnail": {"source": "https://example.org/o.png"}
              },
              "onthisday": [
                {"year": 1969, "text": "Die erste bemannte Mondlandung gelingt und wird weltweit im Fernsehen uebertragen.", "pages": [{"titles": {"normalized": "Apollo 11"}}]},
                {"year": 1789, "text": "Der Sturm auf die Bastille markiert den Beginn der Franzoesischen Revolution in Paris.", "pages": []}
              ]
            }
        """.trimIndent()

        val cards = WikipediaParser.parseFeaturedResponse(json)

        assertEquals(CardKind.TODAY, cards[0].kind)
        assertEquals("Orchideen", cards[0].title)
        assertEquals(CardKind.ON_THIS_DAY, cards[1].kind)
        assertEquals("Im Jahr 1969", cards[1].title)
        assertEquals("https://de.wikipedia.org/wiki/Apollo_11", cards[1].sourceUrl)
        assertEquals(3, cards.size)
    }

    /** Ohne Tagesfeed darf der Rest trotzdem funktionieren. */
    @Test
    fun `featured feed without tfa still returns the events`() {
        val json = """{"onthisday":[{"year":1969,"text":"Die erste bemannte Mondlandung gelingt und wird weltweit uebertragen.","pages":[]}]}"""
        val cards = WikipediaParser.parseFeaturedResponse(json)

        assertEquals(1, cards.size)
        assertEquals(CardKind.ON_THIS_DAY, cards[0].kind)
    }
}
