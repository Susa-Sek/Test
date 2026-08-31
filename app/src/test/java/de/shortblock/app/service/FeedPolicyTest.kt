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
        assertTrue(FeedPolicy.evaluate(onHome) is FeedDecision.OpenSwitcher)
    }

    @Test
    fun `algorithmic feed opens the switcher`() {
        assertTrue(FeedPolicy.evaluate(feed("Instagram")) is FeedDecision.OpenSwitcher)
    }

    @Test
    fun `open menu picks the following entry`() {
        val withMenu = feed("Instagram", extra = listOf(igNode(id = "menu_item", text = "Folge ich")))
        val decision = FeedPolicy.evaluate(withMenu)

        assertTrue(decision is FeedDecision.ChooseFollowing)
        assertEquals("Folge ich", (decision as FeedDecision.ChooseFollowing).node.text)
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
        assertTrue(FeedPolicy.evaluate(suggested) is FeedDecision.OpenSwitcher)
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
    fun `for you tab selected switches to following`() {
        val decision = FeedPolicy.evaluate(tabs(forYouSelected = true, followingSelected = false))
        assertTrue(decision is FeedDecision.ChooseFollowing)
        assertEquals("Folge ich", (decision as FeedDecision.ChooseFollowing).node.text)
    }

    @Test
    fun `following tab selected needs no action`() {
        assertEquals(
            FeedDecision.AlreadyFiltered,
            FeedPolicy.evaluate(tabs(forYouSelected = false, followingSelected = true)),
        )
    }

    /**
     * Das UND-Gatter: Ohne die Auswahl-Bedingung würde die App auf jeden Text „Folge ich“
     * tippen, der irgendwo im Baum steht — etwa auf den Knopf im Profil eines Accounts.
     */
    @Test
    fun `no selected tab means do not tap blindly`() {
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
                igNode(id = "follow_button", text = "Folge ich", selected = false),
            ),
        )
        assertEquals(FeedDecision.Idle, FeedPolicy.evaluate(profile))
    }
}
