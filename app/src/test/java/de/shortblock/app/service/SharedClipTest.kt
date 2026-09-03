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
     * Der Test für den Fehler aus v0.9.0.
     *
     * Dort galt eine sichtbare untere Navigationsleiste als Beleg für den Reels-Tab, mit der
     * Begründung, ein aus Story oder Profil geöffnetes Reel komme als Vollbild ohne sie. Das
     * war eine Annahme und sie war falsch: Instagram öffnet Deep Links innerhalb der normalen
     * Tab-Hülle. Damit galt jedes angetippte Reel als Tab-Strom — und wurde geblockt.
     */
    @Test
    fun `a visible tab bar alone is not the stream`() {
        val deepLink = igNode(
            id = "root",
            children = listOf(igNode(id = "clips_viewer"), igNode(id = "tab_bar")),
        )
        assertFalse(SharedClip.looksLikeAlgorithmicStream(deepLink))
    }

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
    fun `the video pager is recognised`() {
        assertTrue(SharedClip.isFromPager("com.instagram.android:id/clips_viewer_view_pager"))
        assertTrue(SharedClip.isFromPager("com.google.android.youtube:id/reel_recycler"))
    }

    /** Wer beim Lesen der Kommentare rausfliegt, hält die App für kaputt. */
    @Test
    fun `the comments list is not the pager`() {
        assertFalse(SharedClip.isFromPager("com.instagram.android:id/comment_thread_recycler"))
        assertFalse(SharedClip.isFromPager(null))
    }

    @Test
    fun `a changed index is a swipe`() {
        assertTrue(SharedClip.countsAsSwipe(fromPager = true, index = 1, lastIndex = 0, sinceStartMs = 200L))
    }

    @Test
    fun `the same index is not a swipe`() {
        assertFalse(SharedClip.countsAsSwipe(fromPager = true, index = 0, lastIndex = 0, sinceStartMs = 9_000L))
    }

    /** Der erste gemeldete Index ist nur der Ausgangspunkt, nicht schon eine Bewegung. */
    @Test
    fun `the first reported index is not a swipe`() {
        assertFalse(SharedClip.countsAsSwipe(fromPager = true, index = 0, lastIndex = -1, sinceStartMs = 9_000L))
    }

    /**
     * Der Test für den zweiten Fehler aus v0.9.0: Eine RecyclerView meldet auch beim Einrasten
     * in die erste Seite einen Scroll. Vorher stand der Zähler damit auf 1, bevor das Video das
     * erste Bild gezeigt hatte.
     */
    @Test
    fun `an unknown index right after opening is settling, not a swipe`() {
        assertFalse(SharedClip.countsAsSwipe(fromPager = true, index = -1, lastIndex = -1, sinceStartMs = 300L))
    }

    /** Ohne Indizes bleibt nur die Zeit — sonst stünde die Ausnahme still auf Dauer offen. */
    @Test
    fun `an unknown index later on does count`() {
        assertTrue(SharedClip.countsAsSwipe(fromPager = true, index = -1, lastIndex = -1, sinceStartMs = 5_000L))
    }

    @Test
    fun `nothing outside the pager ever counts`() {
        assertFalse(SharedClip.countsAsSwipe(fromPager = false, index = 7, lastIndex = 0, sinceStartMs = 9_000L))
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
