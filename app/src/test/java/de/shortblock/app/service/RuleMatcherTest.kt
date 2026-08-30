package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    private val allFeatures = Feature.entries.toSet()

    // --- Instagram Reels ---------------------------------------------------------------

    @Test
    fun `reels viewer is blocked`() {
        val tree = igNode(
            id = "layout_container_main",
            children = listOf(
                igNode(id = "clips_viewer_view_pager"),
            ),
        )
        val rule = RuleMatcher.findFirstMatch(tree, Packages.INSTAGRAM, allFeatures)
        assertEquals(Feature.INSTAGRAM_REELS, rule?.feature)
    }

    /**
     * Die wichtigste Gegenprobe der ganzen Suite.
     *
     * Instagram nennt STORIES intern "reel". Wer die YouTube-Muster ("reel_") unbesehen auf
     * Instagram anwendet, blockt Stories statt Reels — und das fällt beim Entwickeln kaum auf,
     * weil beides Vollbild-Video ist.
     */
    @Test
    fun `instagram stories are not blocked`() {
        val tree = igNode(
            id = "layout_container_main",
            children = listOf(
                igNode(id = "reel_viewer_texture_view"),
                igNode(id = "reel_viewer_media_container"),
                igNode(id = "reel_tray"),
            ),
        )
        assertNull(RuleMatcher.findFirstMatch(tree, Packages.INSTAGRAM, allFeatures))
    }

    @Test
    fun `ordinary feed post is not blocked`() {
        val tree = igNode(
            id = "feed_recycler_view",
            children = listOf(
                igNode(id = "row_feed_photo_profile_name", text = "some.account"),
                igNode(id = "row_feed_button_like", description = "Gefällt mir"),
            ),
        )
        assertNull(RuleMatcher.findFirstMatch(tree, Packages.INSTAGRAM, allFeatures))
    }

    @Test
    fun `reels tab matches only while selected`() {
        val selected = igNode(id = "tab_bar", children = listOf(igNode(id = "clips_tab", selected = true)))
        val unselected = igNode(id = "tab_bar", children = listOf(igNode(id = "clips_tab", selected = false)))

        assertNotNull(RuleMatcher.findFirstMatch(selected, Packages.INSTAGRAM, allFeatures))
        assertNull(RuleMatcher.findFirstMatch(unselected, Packages.INSTAGRAM, allFeatures))
    }

    // --- YouTube Shorts ----------------------------------------------------------------

    @Test
    fun `shorts player is blocked`() {
        val tree = ytNode(
            id = "content",
            children = listOf(ytNode(id = "reel_recycler")),
        )
        val rule = RuleMatcher.findFirstMatch(tree, Packages.YOUTUBE, allFeatures)
        assertEquals(Feature.YOUTUBE_SHORTS, rule?.feature)
    }

    @Test
    fun `shorts tab matches by content description when selected`() {
        val tree = ytNode(
            id = "pivot_bar",
            children = listOf(ytNode(description = "Shorts", selected = true)),
        )
        assertNotNull(RuleMatcher.findFirstMatch(tree, Packages.YOUTUBE, allFeatures))
    }

    @Test
    fun `normal youtube video is not blocked`() {
        val tree = ytNode(
            id = "watch_player",
            children = listOf(
                ytNode(id = "player_video_title", text = "Ein ganz normales Video"),
                ytNode(description = "Shorts", selected = false),
            ),
        )
        assertNull(RuleMatcher.findFirstMatch(tree, Packages.YOUTUBE, allFeatures))
    }

    // --- Abgrenzung --------------------------------------------------------------------

    @Test
    fun `disabled feature does not block`() {
        val tree = ytNode(id = "reel_recycler")
        val enabled = setOf(Feature.INSTAGRAM_REELS, Feature.INSTAGRAM_FEED)
        assertNull(RuleMatcher.findFirstMatch(tree, Packages.YOUTUBE, enabled))
    }

    @Test
    fun `youtube rules do not apply to instagram trees`() {
        // Falls Instagram jemals einen Knoten "reel_recycler" bekäme, darf die YouTube-Regel
        // trotzdem nicht greifen — Regeln sind an ihr Paket gebunden.
        val tree = igNode(id = "reel_recycler")
        assertNull(RuleMatcher.findFirstMatch(tree, Packages.INSTAGRAM, allFeatures))
    }

    // --- Traversierung -----------------------------------------------------------------

    @Test
    fun `traversal respects the depth limit`() {
        var deepest = igNode(id = "clips_viewer_view_pager")
        repeat(40) { deepest = igNode(id = "wrapper", children = listOf(deepest)) }

        assertNull(RuleMatcher.findFirstMatch(deepest, Packages.INSTAGRAM, allFeatures))
    }

    @Test
    fun `traversal visits every node within the limits`() {
        val tree = igNode(
            id = "a",
            children = listOf(igNode(id = "b"), igNode(id = "c", children = listOf(igNode(id = "d")))),
        )
        var visited = 0
        val stopped = RuleMatcher.traverse(tree) { visited++; false }

        assertFalse(stopped)
        assertEquals(4, visited)
    }

    @Test
    fun `signatures list identifiable nodes for diagnostics`() {
        val tree = igNode(
            id = "tab_bar",
            children = listOf(
                igNode(id = "clips_tab", description = "Reels", selected = true),
                igNode(text = "kein view id, keine description"),
            ),
        )
        val signatures = RuleMatcher.collectSignatures(tree)

        assertTrue(signatures.any { it.contains("id=clips_tab") && it.contains("selected") })
        assertTrue(signatures.none { it.contains("kein view id") })
    }
}
