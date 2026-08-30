package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokPolicyTest {

    /** TikTok hat keine brauchbaren View-IDs — die Tabs werden nur über Text erkannt. */
    private fun tabs(forYouSelected: Boolean, withFollowing: Boolean = true) = FakeNode(
        children = buildList {
            add(FakeNode(text = "Für dich", isSelected = forYouSelected))
            if (withFollowing) add(FakeNode(text = "Folge ich", isSelected = !forYouSelected))
        },
    )

    @Test
    fun `for you active switches to following`() {
        val decision = TikTokPolicy.evaluate(tabs(forYouSelected = true))

        assertTrue(decision is FeedDecision.ChooseFollowing)
        assertEquals("Folge ich", (decision as FeedDecision.ChooseFollowing).node.text)
    }

    @Test
    fun `following active needs no action`() {
        assertEquals(FeedDecision.AlreadyFiltered, TikTokPolicy.evaluate(tabs(forYouSelected = false)))
    }

    /**
     * DMs, Suche und Profil haben keine Feed-Tabs. Dort darf der Schalter „Für dich blocken“
     * nichts tun — sonst wäre er in Wahrheit ein „TikTok blocken“.
     */
    @Test
    fun `screens without feed tabs are left alone`() {
        val inbox = FakeNode(children = listOf(FakeNode(text = "Posteingang")))
        assertEquals(FeedDecision.Idle, TikTokPolicy.evaluate(inbox))
    }

    @Test
    fun `invisible tab labels do not trigger anything`() {
        val hidden = FakeNode(
            children = listOf(
                FakeNode(text = "Für dich", isSelected = true, isVisible = false),
                FakeNode(text = "Folge ich", isVisible = false),
            ),
        )
        assertEquals(FeedDecision.Idle, TikTokPolicy.evaluate(hidden))
    }

    @Test
    fun `english labels work too`() {
        val english = FakeNode(
            children = listOf(
                FakeNode(text = "For You", isSelected = true),
                FakeNode(text = "Following"),
            ),
        )
        assertTrue(TikTokPolicy.evaluate(english) is FeedDecision.ChooseFollowing)
    }

    /** Ohne Zieltab gibt es nichts anzutippen — dann lieber nichts tun als raten. */
    @Test
    fun `missing following tab means idle`() {
        val decision = TikTokPolicy.evaluate(tabs(forYouSelected = true, withFollowing = false))
        assertEquals(FeedDecision.Idle, decision)
    }

    @Test
    fun `no tab selected means idle`() {
        val neither = FakeNode(
            children = listOf(FakeNode(text = "Für dich"), FakeNode(text = "Folge ich")),
        )
        assertEquals(FeedDecision.Idle, TikTokPolicy.evaluate(neither))
    }
}
