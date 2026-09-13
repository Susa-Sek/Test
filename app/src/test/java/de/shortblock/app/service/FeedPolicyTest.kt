package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedPolicyTest {

    private fun feed(title: String?, extra: List<FakeNode> = emptyList()) = igNode(
        id = "root",
        children = buildList {
            add(igNode(id = "feed_recycler_view"))
            if (title != null) add(igNode(id = "action_bar_title", text = title))
            addAll(extra)
        },
    )

    @Test
    fun `not on the home feed means do nothing`() {
        val explore = igNode(
            id = "root",
            children = listOf(
                igNode(id = "explore_grid"),
                igNode(id = "action_bar_title", text = "Instagram"),
            ),
        )
        assertEquals(FeedDecision.Idle, FeedPolicy.evaluate(explore))
    }

    @Test
    fun `home tab alone only counts when it is selected`() {
        val onExplore = igNode(
            id = "root",
            children = listOf(
                igNode(id = "feed_tab", selected = false),
                igNode(id = "action_bar_title", text = "Instagram"),
            ),
        )
        assertEquals(FeedDecision.Idle, FeedPolicy.evaluate(onExplore))

        val onHome = igNode(
            id = "root",
            children = listOf(
                igNode(id = "feed_tab", selected = true),
                igNode(id = "action_bar_title", text = "Instagram"),
            ),
        )
        assertTrue(FeedPolicy.evaluate(onHome) is FeedDecision.BlockFeed)
    }

    @Test
    fun `algorithmic feed raises the wall`() {
        assertTrue(FeedPolicy.evaluate(feed("Instagram")) is FeedDecision.BlockFeed)
    }

    @Test
    fun `following feed needs no action`() {
        assertEquals(FeedDecision.AlreadyFiltered, FeedPolicy.evaluate(feed("Folge ich")))
        assertEquals(FeedDecision.AlreadyFiltered, FeedPolicy.evaluate(feed("Following")))
    }

    /**
     * Kein Endlos-Tippen: Steht im Titel bereits "Folge ich", darf dieser Text nicht als
     * Menueintrag missverstanden werden.
     */
    @Test
    fun `switched title is not mistaken for a menu entry`() {
        repeat(3) {
            assertEquals(FeedDecision.AlreadyFiltered, FeedPolicy.evaluate(feed("Folge ich")))
        }
    }

    @Test
    fun `visible end marker ends the following feed`() {
        val caughtUp = feed(
            "Folge ich",
            extra = listOf(igNode(id = "row_text", text = "Du bist auf dem neuesten Stand")),
        )
        val decision = FeedPolicy.evaluate(caughtUp)

        assertTrue(decision is FeedDecision.EndOfFeed)
        assertEquals("Du bist auf dem neuesten Stand", (decision as FeedDecision.EndOfFeed).marker)
    }

    /**
     * DER Fehler aus Version 0.1.0, in Testform.
     *
     * Der Accessibility-Baum enthaelt auch Knoten weit unterhalb des Bildschirms. Zaehlt ein
     * solcher unsichtbarer "Vorgeschlagene Beitraege"-Knoten als Feed-Ende, wirft die App
     * beim Oeffnen von Instagram sofort wieder heraus — und zwar jedes Mal, sodass die App
     * praktisch unbenutzbar wird.
     */
    @Test
    fun `end marker below the fold does not end the feed`() {
        val notYetReached = feed(
            "Folge ich",
            extra = listOf(igNode(id = "row_text", text = "Vorgeschlagene Beiträge", visible = false)),
        )
        assertEquals(FeedDecision.AlreadyFiltered, FeedPolicy.evaluate(notYetReached))
    }

    @Test
    fun `english end marker is recognised despite the typographic apostrophe`() {
        val caughtUp = feed(
            "Following",
            extra = listOf(igNode(id = "row_text", text = "You’re all caught up")),
        )
        assertTrue(FeedPolicy.evaluate(caughtUp) is FeedDecision.EndOfFeed)
    }

    /**
     * Im algorithmischen Feed steht "Vorgeschlagen fuer dich" an einzelnen Beitraegen mitten im
     * Feed. Wuerde der Ende-Marker dort greifen, floege man beim Scrollen aus Instagram raus —
     * deshalb zaehlt er nur, wenn der Folge-ich-Feed aktiv ist.
     */
    @Test
    fun `end marker is ignored while the algorithmic feed is active`() {
        val suggested = feed(
            "Instagram",
            extra = listOf(igNode(id = "row_text", text = "Vorgeschlagene Beiträge")),
        )
        assertTrue(FeedPolicy.evaluate(suggested) is FeedDecision.BlockFeed)
    }

    @Test
    fun `unknown title means do nothing`() {
        assertEquals(FeedDecision.Idle, FeedPolicy.evaluate(feed("Etwas ganz Neues")))
        assertEquals(FeedDecision.Idle, FeedPolicy.evaluate(feed(null)))
    }

    // --- Tab-Oberfläche (ab v0.6) ---------------------------------------------------
    //
    // Neuere Instagram-Versionen zeigen „Für dich“ und „Folge ich“ nebeneinander statt im
    // Aufklappmenü. Vorher fand die Policy weder Titel noch Menü und tat still gar nichts.

    private fun tabs(forYouSelected: Boolean, followingSelected: Boolean) = igNode(
        id = "root",
        children = listOf(
            igNode(id = "feed_recycler_view"),
            igNode(id = "tab_for_you", text = "Für dich", selected = forYouSelected),
            igNode(id = "tab_following", text = "Folge ich", selected = followingSelected),
        ),
    )

    @Test
    fun `for you tab selected raises the wall`() {
        assertTrue(
            FeedPolicy.evaluate(tabs(forYouSelected = true, followingSelected = false))
                is FeedDecision.BlockFeed,
        )
    }

    @Test
    fun `following tab selected needs no action`() {
        assertEquals(
            FeedDecision.AlreadyFiltered,
            FeedPolicy.evaluate(tabs(forYouSelected = false, followingSelected = true)),
        )
    }

    /**
     * Meldet Instagram keinen Tab als ausgewählt, greift seit v0.8.1 der Textweg und tippt den
     * „Folge ich“-Tab in der Kopfzeile an.
     *
     * Bis v0.8.0 stand hier `Idle` mit der Begründung, sonst würde die App auf jeden Text
     * „Folge ich“ tippen, der irgendwo im Baum steht. Diese Sorge trägt zwei eigene Gatter
     * (`isOnHomeFeed` und die Positionsschranke) und ihren eigenen Test — siehe
     * `a following button outside the home feed is never tapped` und
     * `the same text further down the screen is ignored`.
     *
     * Innerhalb der Kopfzeile des Startfeeds ist ein Tipp auf „Folge ich“ dagegen genau die
     * gewünschte Handlung: Entweder schaltet er um, oder wir sind schon dort und nichts
     * passiert. Der alte Zustand war der stille Fehler — die App tat gar nichts, sobald
     * Instagram den Auswahl-Zustand nicht mehr meldete.
     */
    @Test
    fun `an unreported selection is left alone`() {
        // Ohne belegte Auswahl ist unklar, welcher Feed vorne ist. Eine Wand ins Blaue wäre
        // der teure Fehler; bis v0.10.2 wurde hier noch getippt, was billiger daneben lag.
        assertEquals(
            FeedDecision.Idle,
            FeedPolicy.evaluate(tabs(forYouSelected = false, followingSelected = false)),
        )
    }

    @Test
    fun `a following button outside the home feed is never tapped`() {
        val profile = igNode(
            id = "root",
            children = listOf(
                igNode(id = "profile_header"),
                // Echte Bounds statt Vollbild: Der Folge-ich-Knopf eines Profils sitzt unter
                // Bild und Bio, weit unterhalb der Kopfzeile. Vor v0.8.1 spielte die Position
                // keine Rolle, deshalb stand hier der Standardwert.
                igNode(
                    id = "follow_button",
                    text = "Folge ich",
                    selected = false,
                    bounds = NodeBounds(60, 900, 1020, 1020),
                ),
            ),
        )
        assertEquals(FeedDecision.Idle, FeedPolicy.evaluate(profile))
    }

    // --- Dritte Kopfzeile: mittiger Titel, keine bekannte View-ID (ab v0.8.1) -----------
    //
    // Instagram hat die Kopfzeile dreimal umgebaut. Bei jedem Umbau brachen die View-IDs; der
    // Text „Für dich" hat alle drei überlebt. Diese Fälle sichern den Weg ohne View-ID ab.

    /** Oberste 20 % eines 2400 hohen Fensters. */
    private fun headerBounds() = NodeBounds(330, 130, 750, 220)

    private fun centeredHeader(label: String, extra: List<FakeNode> = emptyList()) = igNode(
        id = "root",
        children = buildList {
            add(igNode(id = "unbekannte_liste"))
            add(igNode(text = label, bounds = headerBounds()))
            addAll(extra)
        },
    )

    @Test
    fun `a centered header without any known view id raises the wall`() {
        val decision = FeedPolicy.evaluate(centeredHeader("Für dich"))
        assertTrue(decision is FeedDecision.BlockFeed)
    }

    @Test
    fun `a centered header already on following needs no action`() {
        assertEquals(FeedDecision.AlreadyFiltered, FeedPolicy.evaluate(centeredHeader("Folge ich")))
    }

    /** Der Test, der einen Fehlalarm mitten im Feed verhindert. */
    @Test
    fun `the same text further down the screen is ignored`() {
        val inFeed = igNode(
            id = "root",
            children = listOf(
                igNode(id = "unbekannte_liste"),
                igNode(text = "Für dich", bounds = NodeBounds(60, 1400, 500, 1480)),
            ),
        )
        assertEquals(FeedDecision.Idle, FeedPolicy.evaluate(inFeed))
    }

    /**
     * Verglichen wird exakt, nicht per `contains` — sonst träfe „Vorgeschlagen für dich" an
     * einem einzelnen Beitrag zu und die App tippte mitten in den Feed.
     */
    @Test
    fun `a longer label containing the title does not count`() {
        assertEquals(
            FeedDecision.Idle,
            FeedPolicy.evaluate(centeredHeader("Vorgeschlagen für dich")),
        )
    }
}

/**
 * Die Beschriftung, mit der Instagram im Herbst 2026 aufgetaucht ist: „Gefolgt“ statt
 * „Folge ich“. Nachgebaut aus einem echten Screenshot — Titel „Für dich“, offenes Menü mit
 * „Gefolgt“ und „Favoriten“.
 */
class FeedPolicyGefolgtTest {

    private fun header(label: String) = igNode(
        id = "action_bar_title",
        text = label,
        bounds = NodeBounds(0, 100, 1080, 260),
    )

    private fun feedRoot(vararg children: FakeNode) = igNode(
        id = "root",
        children = listOf(igNode(id = "feed_recycler_view")) + children,
    )

    @Test
    fun `a Gefolgt button on a post is never tapped`() {
        // DER gefaehrliche Fall: "Gefolgt" ist auch der Zustand des Folgen-Knopfes an einem
        // Beitrag. Ohne Menue daneben darf die App das niemals antippen — sonst kuendigt sie
        // stillschweigend ein Abo.
        val root = feedRoot(
            header("Für dich"),
            igNode(text = "Gefolgt", bounds = NodeBounds(700, 900, 1000, 980)),
        )

        val decision = FeedPolicy.evaluate(root)

        // Seit v0.11 wird ohnehin nichts mehr angetippt — der Fall bleibt geprueft, damit
        // ein spaeterer Rueckbau auf Antippen nicht unbemerkt den alten Fehler mitbringt.
        assertTrue(
            "Ohne offenes Menü darf nichts angetippt werden, war $decision",
            decision is FeedDecision.BlockFeed,
        )
    }

    @Test
    fun `the switched feed is recognised as already filtered`() {
        // Vorher hielt die App den umgeschalteten Feed fuer einen unbekannten Titel und tat
        // gar nichts mehr — auch das Feed-Ende wurde nie erkannt.
        val root = feedRoot(header("Gefolgt"))

        assertEquals(FeedDecision.AlreadyFiltered, FeedPolicy.evaluate(root))
    }

    @Test
    fun `the end of the switched feed still fires`() {
        val root = feedRoot(
            header("Gefolgt"),
            igNode(text = "Du bist auf dem neuesten Stand", bounds = NodeBounds(0, 800, 1080, 900)),
        )

        val decision = FeedPolicy.evaluate(root)

        assertTrue(decision is FeedDecision.EndOfFeed)
    }

    @Test
    fun `a stray Folge ich label does not stop the wall`() {
        // Frueher war das der Menueeintrag zum Antippen. Jetzt ist es nur Text im Baum und
        // darf die Sperre nicht aushebeln.
        val root = feedRoot(header("Für dich"), igNode(text = "Folge ich"))

        assertTrue(FeedPolicy.evaluate(root) is FeedDecision.BlockFeed)
    }
}

/**
 * Die Rückfallkette in [FeedPolicy.evaluate].
 *
 * Weg 1 (View-ID) durfte bis v0.10.1 die Kette kappen: Fand er einen Titelknoten, dessen
 * eigener Text leer war, gab er `Idle` zurück — und die Wege 2 und 3 kamen nie zum Zug.
 * Genau das passiert bei Instagrams mittiger Kopfzeile, wo die Beschriftung in einem
 * Kindknoten steckt.
 */
class FeedPolicyFallbackTest {

    private fun feedRoot(vararg children: FakeNode) = igNode(
        id = "root",
        children = listOf(igNode(id = "feed_recycler_view")) + children,
    )

    @Test
    fun `a title container with the label in a child still switches`() {
        val root = feedRoot(
            igNode(
                id = "action_bar_title",
                bounds = NodeBounds(0, 100, 1080, 260),
                children = listOf(
                    igNode(text = "Für dich", bounds = NodeBounds(300, 120, 780, 240)),
                ),
            ),
            igNode(text = "Folge ich"),
        )

        val decision = FeedPolicy.evaluate(root)

        assertTrue(
            "Weg 1 fand einen Titelknoten ohne eigenen Text und kappte die Kette; war $decision",
            decision is FeedDecision.BlockFeed,
        )
    }

    @Test
    fun `an unknown title on path one does not kill the tab path`() {
        val root = feedRoot(
            igNode(id = "action_bar_title", text = "Etwas Unbekanntes", bounds = NodeBounds(0, 100, 1080, 260)),
            igNode(text = "Für dich", selected = true),
            igNode(text = "Folge ich", selected = false),
        )

        val decision = FeedPolicy.evaluate(root)

        assertTrue(
            "Der Tab-Weg hätte greifen müssen; war $decision",
            decision is FeedDecision.BlockFeed,
        )
    }

    @Test
    fun `an empty title container without any label stays idle`() {
        // Kein Titel, keine Tabs, kein Text — dann bleibt es beim sicheren Nichtstun.
        val root = feedRoot(igNode(id = "action_bar_title", bounds = NodeBounds(0, 100, 1080, 260)))

        assertEquals(FeedDecision.Idle, FeedPolicy.evaluate(root))
    }
}

/**
 * Wo die Wand anfängt. Die Kopfzeile muss frei bleiben — eine Wand ab 0 verdeckt genau den
 * Umschalter, den sie verlangt, und sperrt den Nutzer aus.
 */
class FeedWallTopTest {

    private fun feedRoot(vararg children: FakeNode) = igNode(
        id = "root",
        children = listOf(igNode(id = "feed_recycler_view")) + children,
    )

    @Test
    fun `the wall starts at the bottom edge of the header`() {
        val root = feedRoot(
            igNode(id = "action_bar_title", text = "Für dich", bounds = NodeBounds(0, 100, 1080, 264)),
        )

        val decision = FeedPolicy.evaluate(root)

        assertTrue(decision is FeedDecision.BlockFeed)
        assertEquals(264, (decision as FeedDecision.BlockFeed).headerBottomPx)
    }

    @Test
    fun `without bounds it falls back below the header, never to zero`() {
        // Der gefaehrlichste Ausgang: Eine Wand ab 0 deckt den Umschalter mit ab.
        val root = igNode(
            id = "root",
            bounds = NodeBounds(0, 0, 1080, 2400),
            children = listOf(
                igNode(id = "feed_recycler_view"),
                igNode(id = "action_bar_title", text = "Für dich", bounds = null),
            ),
        )

        val decision = FeedPolicy.evaluate(root)

        assertTrue(decision is FeedDecision.BlockFeed)
        val top = (decision as FeedDecision.BlockFeed).headerBottomPx
        assertTrue("Wand begann bei $top — das verdeckt den Umschalter", top >= 400)
    }

    @Test
    fun `the followed feed raises no wall`() {
        assertEquals(
            FeedDecision.AlreadyFiltered,
            FeedPolicy.evaluate(feedRoot(igNode(id = "action_bar_title", text = "Gefolgt"))),
        )
    }
}
