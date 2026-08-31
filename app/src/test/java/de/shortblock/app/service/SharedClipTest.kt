package de.shortblock.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedClipTest {

    private val now = 1_800_000_000_000L

    // --- Herkunft ---------------------------------------------------------------------

    @Test
    fun `opened straight from another app counts as shared`() {
        assertTrue(
            SharedClip.cameFromShare(
                sawOwnScreenBeforeViewer = false,
                directSeenAtMs = 0L,
                nowMs = now,
            ),
        )
    }

    /** Der Reels-Tab: Dorthin kommt man über die Startseite, die vorher sichtbar ist. */
    @Test
    fun `navigating inside the app is never shared`() {
        assertFalse(
            SharedClip.cameFromShare(
                sawOwnScreenBeforeViewer = true,
                directSeenAtMs = 0L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `a direct message just now counts as shared`() {
        assertTrue(
            SharedClip.cameFromShare(
                sawOwnScreenBeforeViewer = true,
                directSeenAtMs = now - 3_000L,
                nowMs = now,
            ),
        )
    }

    /**
     * Ohne diese Frist würde eine DM von vorhin später den Reels-Tab freischalten — der Dienst
     * merkt sich den letzten Bildschirm, nicht die Absicht dahinter.
     */
    @Test
    fun `a stale direct message does not unlock the reels tab`() {
        assertFalse(
            SharedClip.cameFromShare(
                sawOwnScreenBeforeViewer = true,
                directSeenAtMs = now - 11_000L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `a rewound clock does not count as a fresh share`() {
        assertFalse(
            SharedClip.cameFromShare(
                sawOwnScreenBeforeViewer = true,
                directSeenAtMs = now + 60_000L,
                nowMs = now,
            ),
        )
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
    fun `the first swipe ends it`() {
        assertTrue(SharedClip.mayWatch(fromShare = true, swipes = 0, startedAtMs = now, nowMs = now))
        assertFalse(SharedClip.mayWatch(fromShare = true, swipes = 1, startedAtMs = now, nowMs = now))
    }

    /**
     * Die Reißleine ist der Grund, warum die Ausnahme kein Scheunentor werden kann: Meldet ein
     * Update die Pager-ID nicht mehr, endet sie trotzdem.
     */
    @Test
    fun `it ends after ninety seconds even without a detected swipe`() {
        assertTrue(
            SharedClip.mayWatch(true, swipes = 0, startedAtMs = now, nowMs = now + 89_000L),
        )
        assertFalse(
            SharedClip.mayWatch(true, swipes = 0, startedAtMs = now, nowMs = now + 91_000L),
        )
    }

    @Test
    fun `nothing is allowed when it did not come from a share`() {
        assertFalse(SharedClip.mayWatch(fromShare = false, swipes = 0, startedAtMs = 0L, nowMs = now))
    }
}
