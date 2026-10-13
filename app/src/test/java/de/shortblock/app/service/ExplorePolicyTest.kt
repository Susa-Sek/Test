package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorePolicyTest {

    private val start = 100_000L
    private val grace = ExplorePolicy.GRACE_MILLIS

    /** Ein Raster, das den Bildschirm füllt — so sieht Explore aus. */
    private fun grid(id: String = "explore_grid", visible: Boolean = true, bounds: NodeBounds? = FakeNode.FULLSCREEN) =
        igNode(id = id, visible = visible, bounds = bounds)

    private fun tree(vararg children: FakeNode) = igNode(id = "root", children = children.toList())

    @Test
    fun `an empty screen is never blocked`() {
        assertEquals(ExploreDecision.Idle, ExplorePolicy.evaluate(null, null, start))
        assertEquals(ExploreDecision.Idle, ExplorePolicy.evaluate(tree(), start, start + grace * 2))
    }

    @Test
    fun `the grid is left alone during the grace period`() {
        val root = tree(grid())

        // Erste Sichtung: Der Aufrufer hat noch keinen Zeitstempel.
        assertEquals(ExploreDecision.Grace, ExplorePolicy.evaluate(root, null, start))
        // Kurz danach immer noch.
        assertEquals(ExploreDecision.Grace, ExplorePolicy.evaluate(root, start, start + grace - 1))
    }

    @Test
    fun `after the grace period the grid is blocked`() {
        val root = tree(grid())

        val decision = ExplorePolicy.evaluate(root, start, start + grace)
        assertTrue(decision is ExploreDecision.Block)
        assertEquals("explore_grid", (decision as ExploreDecision.Block).marker)
    }

    @Test
    fun `searching is never blocked, not even after the grace period`() {
        // Das Herzstueck: Wer sucht, soll suchen duerfen. Zaehlt auch, wenn das Raster
        // im Baum noch herumliegt.
        val root = tree(grid(), igNode(id = "search_results"))

        assertEquals(ExploreDecision.Searching, ExplorePolicy.evaluate(root, start, start + grace * 10))
    }

    @Test
    fun `recent searches also count as searching`() {
        val byId = tree(grid(), igNode(id = "recent_searches"))
        val byLabel = tree(grid(), igNode(text = "Zuletzt gesucht"))

        assertEquals(ExploreDecision.Searching, ExplorePolicy.evaluate(byId, start, start + grace * 2))
        assertEquals(ExploreDecision.Searching, ExplorePolicy.evaluate(byLabel, start, start + grace * 2))
    }

    @Test
    fun `the search label is matched exactly, not as a substring`() {
        // "recent" steckt sonst in jedem zweiten Beitragstext und wuerde die Sperre
        // stillschweigend abschalten.
        val root = tree(grid(), igNode(text = "Most recent posts from your friends"))

        assertTrue(ExplorePolicy.evaluate(root, start, start + grace) is ExploreDecision.Block)
    }

    @Test
    fun `an invisible grid does not count`() {
        // Der Baum enthaelt recycelte Knoten weit unter dem Bildschirm.
        val root = tree(grid(visible = false))

        assertEquals(ExploreDecision.Idle, ExplorePolicy.evaluate(root, start, start + grace * 2))
    }

    @Test
    fun `a small embedded grid is not the explore tab`() {
        // Ein Raster im Profil ist klein; das Explore-Raster fuellt den Bildschirm.
        val root = tree(grid(bounds = NodeBounds(0, 0, 300, 300)))

        assertEquals(ExploreDecision.Idle, ExplorePolicy.evaluate(root, start, start + grace * 2))
    }

    @Test
    fun `all known grid names are recognised`() {
        Rules.InstagramExplore.GRID_VIEW_IDS.forEach { id ->
            val decision = ExplorePolicy.evaluate(tree(grid(id)), start, start + grace)
            assertTrue("$id wurde nicht als Raster erkannt", decision is ExploreDecision.Block)
        }
    }

    @Test
    fun `the feed is not the explore grid`() {
        // Der Startfeed hat eine eigene Policy; hier darf nichts feuern.
        val root = tree(igNode(id = "feed_recycler_view"))

        assertEquals(ExploreDecision.Idle, ExplorePolicy.evaluate(root, start, start + grace * 2))
    }

    @Test
    fun `a custom grace period is honoured`() {
        val root = tree(grid())

        assertEquals(ExploreDecision.Grace, ExplorePolicy.evaluate(root, start, start + 500, graceMillis = 1_000))
        assertTrue(
            ExplorePolicy.evaluate(root, start, start + 1_000, graceMillis = 1_000) is ExploreDecision.Block,
        )
    }
}
