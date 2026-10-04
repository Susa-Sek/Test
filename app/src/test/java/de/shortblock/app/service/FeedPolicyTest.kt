package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertTrue(FeedPolicy.evaluate(onHome) == FeedDecision.RemindToSwitch)
    }

    @Test
    fun `algorithmic feed raises the wall`() {
        assertTrue(FeedPolicy.evaluate(feed("Instagram")) == FeedDecision.RemindToSwitch)
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
        assertTrue(FeedPolicy.evaluate(suggested) == FeedDecision.RemindToSwitch)
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
    fun `for you tab selected asks to switch`() {
        assertEquals(
            FeedDecision.RemindToSwitch,
            FeedPolicy.evaluate(tabs(forYouSelected = true, followingSelected = false)),
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
        assertTrue(decision == FeedDecision.RemindToSwitch)
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
            decision == FeedDecision.RemindToSwitch,
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

        assertTrue(FeedPolicy.evaluate(root) == FeedDecision.RemindToSwitch)
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
            decision == FeedDecision.RemindToSwitch,
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
            decision == FeedDecision.RemindToSwitch,
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
 * Der Feed-Filter erinnert, er sperrt nicht.
 *
 * Diese Klasse hiess bis v0.12 `FeedWallTopTest` und prüfte, wo die Wand anfängt. Die Wand ist
 * raus: Sie blieb nach dem Verlassen von Instagram über fremden Apps stehen und machte das
 * Gerät unbenutzbar. Geblieben ist die Erkennung — und die Zusicherung, dass aus ihr nichts
 * mehr folgt, was den Bildschirm festhält.
 */
class FeedReminderTest {

    private fun feedRoot(vararg children: FakeNode) = igNode(
        id = "root",
        children = listOf(igNode(id = "feed_recycler_view")) + children,
    )

    @Test
    fun `the for you feed only asks to switch`() {
        val root = feedRoot(
            igNode(id = "action_bar_title", text = "Für dich", bounds = NodeBounds(0, 100, 1080, 264)),
        )

        assertEquals(FeedDecision.RemindToSwitch, FeedPolicy.evaluate(root))
    }

    /**
     * Fehlende Bounds waren früher der gefährlichste Ausgang — eine Wand ab 0 deckte den
     * Umschalter mit ab. Ohne Wand ist die Entscheidung dieselbe wie mit Bounds.
     */
    @Test
    fun `missing bounds change nothing any more`() {
        val root = igNode(
            id = "root",
            bounds = NodeBounds(0, 0, 1080, 2400),
            children = listOf(
                igNode(id = "feed_recycler_view"),
                igNode(id = "action_bar_title", text = "Für dich", bounds = null),
            ),
        )

        assertEquals(FeedDecision.RemindToSwitch, FeedPolicy.evaluate(root))
    }

    @Test
    fun `the followed feed is left alone`() {
        assertEquals(
            FeedDecision.AlreadyFiltered,
            FeedPolicy.evaluate(feedRoot(igNode(id = "action_bar_title", text = "Gefolgt"))),
        )
    }
}

/**
 * Das Ziel für den Tipp auf „Gefolgt“.
 *
 * Hier sitzt der teuerste Fehlgriff der ganzen App: „Gefolgt“ steht bei Instagram auch als
 * Knopf unter jedem fremden Profil. Ein Tipp darauf **entfolgt jemanden**, und das merkt man
 * erst Wochen später — es gibt keine Rückmeldung und kein Rückgängig.
 */
class FollowingTabToTapTest {

    private val window = NodeBounds(0, 0, 1080, 2400)

    /** Oberste 20 % des Fensters — dort und nur dort darf getippt werden. */
    private val inHeader = NodeBounds(40, 120, 400, 300)
    private val belowHeader = NodeBounds(40, 900, 400, 1000)

    private fun screen(vararg children: FakeNode) =
        igNode(id = "root", bounds = window, children = children.toList())

    @Test
    fun `the tab in the header is a valid target`() {
        val root = screen(igNode(text = "Gefolgt", bounds = inHeader))

        assertNotNull(FeedPolicy.followingTabToTap(root))
    }

    @Test
    fun `the unambiguous label works too`() {
        val root = screen(igNode(text = "Folge ich", bounds = inHeader))

        assertNotNull(FeedPolicy.followingTabToTap(root))
    }

    /**
     * Der Test, um den es hier geht.
     *
     * Auf einer Profilseite steht „Gefolgt“ als Knopf mitten auf dem Schirm. Er darf nie als
     * Ziel geliefert werden — sonst entfolgt die App beim Umschalten des Feeds einen Account.
     */
    @Test
    fun `a follow button in the middle of the screen is never a target`() {
        val root = screen(igNode(text = "Gefolgt", bounds = belowHeader))

        assertNull(
            "Ein „Gefolgt“-Knopf ausserhalb der Kopfzeile darf nie angetippt werden",
            FeedPolicy.followingTabToTap(root),
        )
    }

    /** Und auch dann nicht, wenn zusätzlich ein echter Tab oben steht — geliefert wird der obere. */
    @Test
    fun `with both present the header node wins`() {
        val root = screen(
            igNode(text = "Gefolgt", bounds = belowHeader),
            igNode(text = "Gefolgt", bounds = inHeader),
        )

        val target = FeedPolicy.followingTabToTap(root)

        assertNotNull(target)
        assertEquals(inHeader, target?.bounds)
    }

    /** Ohne Fenstermasse lässt sich die Kopfzeile nicht bestimmen — dann gar nichts tun. */
    @Test
    fun `without window bounds nothing is offered`() {
        val root = igNode(
            id = "root",
            bounds = null,
            children = listOf(igNode(text = "Gefolgt", bounds = inHeader)),
        )

        assertNull(FeedPolicy.followingTabToTap(root))
    }

    /** Ein Knoten ohne eigene Bounds ist nicht einzuordnen und scheidet aus. */
    @Test
    fun `a node without bounds is not a target`() {
        val root = screen(igNode(text = "Gefolgt", bounds = null))

        assertNull(FeedPolicy.followingTabToTap(root))
    }

    @Test
    fun `an unrelated header label is not a target`() {
        val root = screen(igNode(text = "Für dich", bounds = inHeader))

        assertNull(FeedPolicy.followingTabToTap(root))
    }
}

/**
 * Die Liste, die gebremst wird.
 *
 * v0.14.1 suchte sie ausschliesslich über zwei View-IDs. Auf einem echten Gerät stand keine
 * davon im Baum — die Bremse meldete `brake_no_list` und tat nichts. Deshalb ein zweites Bein,
 * das keine Namen kennt und nach der Form sucht.
 */
class FeedListToBrakeTest {

    private val window = NodeBounds(0, 0, 1080, 2400)

    private fun screen(vararg children: FakeNode) =
        igNode(id = "root", bounds = window, children = children.toList())

    @Test
    fun `the known id is found`() {
        val root = screen(
            igNode(id = "feed_recycler_view", bounds = NodeBounds(0, 300, 1080, 2400)),
        )

        assertNotNull(FeedPolicy.feedListToBrake(root))
    }

    /** Der Fall, an dem v0.14.1 scheiterte: eine Liste, deren Kennung die App nicht kennt. */
    @Test
    fun `an unknown list is found by its shape`() {
        val root = screen(
            igNode(
                id = "irgendein_neuer_name",
                bounds = NodeBounds(0, 300, 1080, 2400),
                scrollable = true,
            ),
        )

        val list = FeedPolicy.feedListToBrake(root)

        assertNotNull("Die Liste muss auch ohne bekannte Kennung gefunden werden", list)
    }

    /** Von zwei scrollbaren Kandidaten gewinnt der höhere — das ist die Beitragsliste. */
    @Test
    fun `the taller scroller wins over a flat strip`() {
        val strip = NodeBounds(0, 200, 1080, 500)
        val feed = NodeBounds(0, 500, 1080, 2400)
        val root = screen(
            igNode(id = "stories", bounds = strip, scrollable = true),
            igNode(id = "posts", bounds = feed, scrollable = true),
        )

        assertEquals(feed, FeedPolicy.feedListToBrake(root)?.bounds)
    }

    /** Ein schmales Karussell mitten im Beitrag ist nicht die Liste. */
    @Test
    fun `a narrow carousel is not the list`() {
        val root = screen(
            igNode(id = "carousel", bounds = NodeBounds(300, 600, 700, 2400), scrollable = true),
        )

        assertNull(FeedPolicy.feedListToBrake(root))
    }

    /** Was nicht scrollbar ist, kann auch nicht gebremst werden. */
    @Test
    fun `a wide but unscrollable container is not the list`() {
        val root = screen(igNode(id = "container", bounds = NodeBounds(0, 300, 1080, 2400)))

        assertNull(FeedPolicy.feedListToBrake(root))
    }

    /** Unsichtbare Knoten zählen nie — der Baum enthält recycelte Views. */
    @Test
    fun `an invisible scroller is ignored`() {
        val root = screen(
            igNode(
                id = "recycelt",
                bounds = NodeBounds(0, 300, 1080, 2400),
                scrollable = true,
                visible = false,
            ),
        )

        assertNull(FeedPolicy.feedListToBrake(root))
    }

    @Test
    fun `without window bounds nothing is offered`() {
        val root = igNode(
            id = "root",
            bounds = null,
            children = listOf(
                igNode(id = "posts", bounds = NodeBounds(0, 300, 1080, 2400), scrollable = true),
            ),
        )

        assertNull(FeedPolicy.feedListToBrake(root))
    }
}
