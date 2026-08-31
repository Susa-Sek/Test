package de.shortblock.app.service

/**
 * Ein geschicktes Reel oder Short einmal ansehen dürfen — aber nicht weiterscrollen.
 *
 * Der Sinn: Wer eine DM mit einem Reel bekommt, soll sie lesen können. Der Feind ist nicht das
 * einzelne Video, sondern die Endlosschleife danach.
 *
 * „Geteilt“ heißt hier immer dasselbe: **Du bist nicht in der App dorthin navigiert.** Entweder
 * kam der Viewer aus einer anderen App, oder unmittelbar aus einem DM-Verlauf. Wer den
 * Reels-Tab antippt, sieht vorher die Startseite — und fällt damit heraus.
 *
 * Reine Regel, kein Android; der Zustand liegt im Dienst.
 */
object SharedClip {

    /**
     * Wie lange ein gesehener DM-Verlauf als Herkunft zählt.
     *
     * Ohne diese Frist würde eine DM von vorhin später den Reels-Tab freischalten: Der Dienst
     * merkt sich den letzten Bildschirm, nicht die Absicht dahinter.
     */
    const val ORIGIN_MAX_AGE_MS = 10_000L

    /**
     * Reißleine. Kein Reel und kein Short ist länger.
     *
     * Sie ist der Grund, warum diese Ausnahme kein Scheunentor werden kann: Meldet ein
     * Instagram-Update die Pager-ID nicht mehr und der Wisch wird nie erkannt, endet die
     * Ausnahme trotzdem — statt still auf Dauer offen zu stehen.
     */
    const val MAX_WATCH_MS = 90_000L

    /**
     * Kam der Viewer von außen oder aus einer DM?
     *
     * @param sawOwnScreenBeforeViewer ob seit dem Wechsel in diese App schon ein anderer
     *   Bildschirm derselben App zu sehen war. Trifft das zu, wurde in der App navigiert.
     * @param directSeenAtMs wann zuletzt ein DM-Verlauf sichtbar war, 0 für nie.
     */
    fun cameFromShare(
        sawOwnScreenBeforeViewer: Boolean,
        directSeenAtMs: Long,
        nowMs: Long,
    ): Boolean {
        if (!sawOwnScreenBeforeViewer) return true
        if (directSeenAtMs <= 0L) return false
        val age = nowMs - directSeenAtMs
        // Negatives Alter heißt zurückgestellte Uhr — dann eben nicht. Im Zweifel blocken.
        return age in 0..ORIGIN_MAX_AGE_MS
    }

    /**
     * Zählt dieses Scroll-Ereignis als Wisch zum nächsten Video?
     *
     * Das Gatter auf die Seitenliste ist notwendig, nicht kosmetisch: Im Kommentar-Bereich
     * scrollt man ebenfalls, und wer beim Lesen der Kommentare rausfliegt, hält die App für
     * kaputt.
     */
    fun isSwipeToNext(scrollSourceViewId: String?): Boolean {
        val viewId = normalizeForMatch(scrollSourceViewId) ?: return false
        return Rules.SharedClip.PAGER_VIEW_IDS.any { viewId.contains(it) }
    }

    /**
     * Darf gerade weitergeschaut werden?
     *
     * @param startedAtMs Beginn der Ausnahme; 0 heißt „hat noch nicht angefangen“.
     */
    fun mayWatch(fromShare: Boolean, swipes: Int, startedAtMs: Long, nowMs: Long): Boolean {
        if (!fromShare) return false
        if (swipes > 0) return false
        if (startedAtMs <= 0L) return true
        val watched = nowMs - startedAtMs
        return watched in 0..MAX_WATCH_MS
    }
}
