package de.shortblock.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * „Ein Video, kein Feed“.
 *
 * Bis v0.8 prüfte diese Datei die **Herkunft** — kam der Viewer aus einer anderen App oder
 * frisch aus einer DM? Damit fiel ein Reel aus einer Story oder von einem Profil durch. Seit
 * v0.9 zählt nur noch, ob **du** ausgewählt hast oder der Algorithmus.
 */
class SharedClipTest {

    private val now = 1_800_000_000_000L
    private val fiveMinutes = 5 * 60 * 1000L

    // --- Ausgewählt oder Tab-Strom? ---------------------------------------------------

    @Test
    fun `a selected reels tab is the algorithmic stream`() {
        val tab = igNode(
            id = "root",
            children = listOf(
                igNode(id = "clips_viewer"),
                igNode(id = "clips_tab", selected = true),
            ),
        )
        assertTrue(SharedClip.looksLikeAlgorithmicStream(tab))
    }

    @Test
    fun `the reels tab is also recognised by its label`() {
        val tab = igNode(
            id = "root",
            children = listOf(
                igNode(id = "clips_viewer"),
                igNode(id = "unbekannt", description = "Reels", selected = true),
            ),
        )
        assertTrue(SharedClip.looksLikeAlgorithmicStream(tab))
    }

    /**
     * Das robustere Merkmal: Im Tab bleibt die untere Leiste stehen, ein aus Story, DM oder
     * Profil geöffnetes Reel kommt als Vollbild ohne sie.
     */
    @Test
    fun `a visible tab bar alone is enough`() {
        val withBar = igNode(
            id = "root",
            children = listOf(igNode(id = "clips_viewer"), igNode(id = "tab_bar")),
        )
        assertTrue(SharedClip.looksLikeAlgorithmicStream(withBar))
    }

    /** Der Fall, um den es dem Nutzer geht: aus einer Story oder von einem Profil angetippt. */
    @Test
    fun `a fullscreen viewer without a tab bar is a chosen video`() {
        val chosen = igNode(
            id = "root",
            children = listOf(igNode(id = "clips_viewer"), igNode(id = "video_container")),
        )
        assertFalse(SharedClip.looksLikeAlgorithmicStream(chosen))
    }

    /** Ein unausgewählter Reels-Tab-Knoten ohne sichtbare Leiste zählt nicht. */
    @Test
    fun `an unselected reels entry does not count`() {
        val node = igNode(
            id = "root",
            children = listOf(igNode(id = "clips_tab", selected = false)),
        )
        assertFalse(SharedClip.looksLikeAlgorithmicStream(node))
    }

    @Test
    fun `nothing at all is not the stream`() {
        assertFalse(SharedClip.looksLikeAlgorithmicStream(null))
    }

    // --- Wisch ------------------------------------------------------------------------

    @Test
    fun `scrolling the video pager counts as moving on`() {
        assertTrue(SharedClip.isSwipeToNext("com.instagram.android:id/clips_viewer_view_pager"))
        assertTrue(SharedClip.isSwipeToNext("com.google.android.youtube:id/reel_recycler"))
    }

    /** Wer beim Lesen der Kommentare rausfliegt, hält die App für kaputt. */
    @Test
    fun `scrolling the comments does not count`() {
        assertFalse(SharedClip.isSwipeToNext("com.instagram.android:id/comment_thread_recycler"))
        assertFalse(SharedClip.isSwipeToNext(null))
    }

    // --- Ende der Ausnahme ------------------------------------------------------------

    @Test
    fun `the algorithmic stream is never allowed`() {
        assertFalse(SharedClip.mayWatch(chosen = false, swipes = 0, startedAtMs = 0L, nowMs = now))
    }

    @Test
    fun `the first swipe ends it`() {
        assertTrue(SharedClip.mayWatch(chosen = true, swipes = 0, startedAtMs = now, nowMs = now))
        assertFalse(SharedClip.mayWatch(chosen = true, swipes = 1, startedAtMs = now, nowMs = now))
    }

    /**
     * Die Reißleine steht seit v0.9 bei fünf statt anderthalb Minuten: Die alten 90 Sekunden
     * brachen ein bewusst angetipptes Video mittendrin ab.
     */
    @Test
    fun `it runs for five minutes and not longer`() {
        assertTrue(SharedClip.mayWatch(true, 0, startedAtMs = now, nowMs = now + fiveMinutes - 1000L))
        assertFalse(SharedClip.mayWatch(true, 0, startedAtMs = now, nowMs = now + fiveMinutes + 1000L))
    }

    @Test
    fun `a rewound clock ends it instead of extending it`() {
        assertFalse(SharedClip.mayWatch(true, 0, startedAtMs = now, nowMs = now - 60_000L))
    }
}
