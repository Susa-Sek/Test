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
        assertTrue(SharedClip.looksLikeAlgorithmicStream(tab, Packages.INSTAGRAM))
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
        assertTrue(SharedClip.looksLikeAlgorithmicStream(tab, Packages.INSTAGRAM))
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
        assertFalse(SharedClip.looksLikeAlgorithmicStream(deepLink, Packages.INSTAGRAM))
    }

    @Test
    fun `a fullscreen viewer without a tab bar is a chosen video`() {
        val chosen = igNode(
            id = "root",
            children = listOf(igNode(id = "clips_viewer"), igNode(id = "video_container")),
        )
        assertFalse(SharedClip.looksLikeAlgorithmicStream(chosen, Packages.INSTAGRAM))
    }

    /** Ein unausgewählter Reels-Tab-Knoten ohne sichtbare Leiste zählt nicht. */
    @Test
    fun `an unselected reels entry does not count`() {
        val node = igNode(
            id = "root",
            children = listOf(igNode(id = "clips_tab", selected = false)),
        )
        assertFalse(SharedClip.looksLikeAlgorithmicStream(node, Packages.INSTAGRAM))
    }

    @Test
    fun `nothing at all is not the stream`() {
        assertFalse(SharedClip.looksLikeAlgorithmicStream(null, Packages.INSTAGRAM))
    }

    // --- Wisch ------------------------------------------------------------------------

    @Test
    fun `the video pager is recognised`() {
        assertTrue(SharedClip.isFromPager("com.instagram.android:id/clips_viewer_view_pager", Packages.INSTAGRAM))
        assertTrue(SharedClip.isFromPager("com.google.android.youtube:id/reel_recycler", Packages.YOUTUBE))
    }

    /** Wer beim Lesen der Kommentare rausfliegt, hält die App für kaputt. */
    @Test
    fun `the comments list is not the pager`() {
        assertFalse(SharedClip.isFromPager("com.instagram.android:id/comment_thread_recycler", Packages.INSTAGRAM))
        assertFalse(SharedClip.isFromPager(null, Packages.INSTAGRAM))
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

    // --- Der Fehler aus v0.11.2: die Ausnahme liess alles durch ------------------------
    //
    // Für YouTube stand im Dienst `match.rule.id != "yt_shorts_tab_selected"`. Weil
    // findFirstMatch die **erste** Regel der Liste liefert und `yt_shorts_player` vor der
    // Tab-Regel steht, war das im Shorts-Tab nie die Tab-Regel — jeder Short galt als bewusst
    // ausgewählt, und damit blockte für Reels und Shorts gar nichts mehr. Entschieden wird
    // seither am Bildschirm.

    /** Der wichtigste Test dieser Datei: der Shorts-Tab ist der Algorithmus, nicht deine Wahl. */
    @Test
    fun `a selected shorts tab is the algorithmic stream`() {
        val tab = ytNode(
            id = "root",
            children = listOf(
                ytNode(id = "reel_recycler"),
                ytNode(id = "pivot_bar_item", description = "Shorts", selected = true),
            ),
        )
        assertTrue(SharedClip.looksLikeAlgorithmicStream(tab, Packages.YOUTUBE))
    }

    @Test
    fun `the shorts tab is also recognised by its text`() {
        val tab = ytNode(
            id = "root",
            children = listOf(
                ytNode(id = "reel_recycler"),
                ytNode(id = "unbekannt", text = "Shorts", selected = true),
            ),
        )
        assertTrue(SharedClip.looksLikeAlgorithmicStream(tab, Packages.YOUTUBE))
    }

    /** Aus dem Regal geöffnet: kein ausgewählter Tab — die Ausnahme darf greifen. */
    @Test
    fun `a short opened from the shelf is not the stream`() {
        val fromShelf = ytNode(
            id = "root",
            children = listOf(
                ytNode(id = "reel_recycler"),
                ytNode(id = "pivot_bar_item", description = "Startseite", selected = true),
            ),
        )
        assertFalse(SharedClip.looksLikeAlgorithmicStream(fromShelf, Packages.YOUTUBE))
        assertTrue(SharedClip.canPolicySwipes(fromShelf, Packages.YOUTUBE))
    }

    // --- Keine Ausnahme ohne Reissleine -----------------------------------------------

    /**
     * Der Fall, der die Ausnahme bisher dauerhaft offen stehen liess.
     *
     * Kennt die App die Seitenliste nicht, kann sie keinen Wisch erkennen — dann ist das
     * Versprechen „ein Video, aber Wischen blockt“ nicht einlösbar und die Ausnahme darf nicht
     * erteilt werden. Vorher hiess derselbe Zustand: fünf Minuten frei, danach von vorn.
     */
    @Test
    fun `an unknown pager id refuses the exception`() {
        val renamed = ytNode(
            id = "root",
            children = listOf(ytNode(id = "reel_watch_player_v2")),
        )
        assertFalse(SharedClip.looksLikeAlgorithmicStream(renamed, Packages.YOUTUBE))
        assertFalse(SharedClip.canPolicySwipes(renamed, Packages.YOUTUBE))
    }

    @Test
    fun `the instagram viewer can police swipes`() {
        val viewer = igNode(id = "root", children = listOf(igNode(id = "clips_viewer")))
        assertTrue(SharedClip.canPolicySwipes(viewer, Packages.INSTAGRAM))
    }

    @Test
    fun `an empty screen cannot police swipes`() {
        assertFalse(SharedClip.canPolicySwipes(null, Packages.YOUTUBE))
        assertFalse(SharedClip.canPolicySwipes(igNode(id = "root"), Packages.INSTAGRAM))
    }

    // --- Muster nie zwischen den Apps kopieren ------------------------------------------

    /**
     * `reel_*` heisst bei Instagram **Stories**, bei YouTube Shorts.
     *
     * Mit der früheren gemeinsamen Liste hätte ein Story-Wisch bei Instagram als Reels-Wisch
     * gezählt und ein bewusst angetipptes Reel mitten im Video beendet.
     */
    @Test
    fun `an instagram story recycler is not the reels pager`() {
        assertFalse(
            SharedClip.isFromPager("com.instagram.android:id/reel_recycler", Packages.INSTAGRAM),
        )
        val stories = igNode(id = "root", children = listOf(igNode(id = "reel_recycler")))
        assertFalse(SharedClip.canPolicySwipes(stories, Packages.INSTAGRAM))
    }

    /** Und umgekehrt: Instagrams Kennungen sind bei YouTube keine Seitenliste. */
    @Test
    fun `the instagram pager id does not count on youtube`() {
        assertFalse(
            SharedClip.isFromPager("com.google.android.youtube:id/clips_viewer", Packages.YOUTUBE),
        )
    }
}
