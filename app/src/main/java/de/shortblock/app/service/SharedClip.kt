package de.shortblock.app.service

/**
 * Ein bewusst angetipptes Video einmal ansehen dürfen — aber nicht weiterscrollen.
 *
 * Der Feind war nie das einzelne Video, sondern die Endlosschleife. Wer ein Reel in einer Story,
 * auf einem Profil, in einer DM oder über einen Link antippt, hat **ausgewählt**; wer den
 * Reels-Tab öffnet, überlässt die Auswahl dem Algorithmus.
 *
 * **Was sich in v0.9 geändert hat, und warum:** Bis v0.8 entschied die *Herkunft* — erlaubt war
 * nur, was aus einer anderen App kam oder frisch aus einem DM-Verlauf. Damit fiel ein Reel aus
 * einer Story oder von einem Profil durch, weil vorher ein Instagram-Bildschirm zu sehen war.
 * Die Herkunftserkennung war der komplizierteste Teil dieser Datei und beantwortete die falsche
 * Frage: nicht „woher kamst du", sondern „hast du das ausgewählt".
 *
 * Reine Regel, kein Android; der Zustand liegt im Dienst.
 */
object SharedClip {

    /**
     * Reißleine, falls der Wisch nie erkannt wird.
     *
     * Bis v0.8 waren es 90 Sekunden, aus der Annahme „länger ist kein Reel". Die stimmt nicht
     * mehr und brach ein bewusst angetipptes Video mittendrin ab. Die Reißleine ist der Notnagel
     * für den Fall, dass ein Instagram-Update die Pager-ID nicht mehr meldet — dafür reichen
     * fünf Minuten. Ohne sie stünde die Ausnahme irgendwann still auf Dauer offen.
     */
    const val MAX_WATCH_MS = 5 * 60 * 1000L

    /**
     * Zeigt der Bildschirm den algorithmischen Reels-Strom statt eines ausgewählten Videos?
     *
     * Zwei Merkmale, **eines genügt**:
     *
     * 1. Ein sichtbarer Knoten der unteren Leiste ist **ausgewählt** und weist sich als Reels
     *    aus.
     * 2. Die untere Navigationsleiste ist überhaupt sichtbar. Tippt man ein Reel in einer Story,
     *    einer DM oder auf einem Profil an, öffnet Instagram es als Vollbild **ohne** Tableiste;
     *    im Reels-Tab bleibt sie stehen. Dieses Merkmal ist das robustere — es braucht weder
     *    eine Beschriftung noch einen Auswahl-Zustand, und beides ändert Instagram gern.
     */
    fun looksLikeAlgorithmicStream(root: UiNode?): Boolean {
        if (root == null) return false
        return RuleMatcher.containsNode(root) { node ->
            val viewId = normalizeForMatch(node.viewId)

            if (viewId != null && Rules.SharedClip.TAB_BAR_VIEW_IDS.any { viewId.contains(it) }) {
                return@containsNode true
            }
            if (!node.isSelected) return@containsNode false
            if (viewId != null && Rules.SharedClip.REELS_TAB_VIEW_IDS.any { viewId.contains(it) }) {
                return@containsNode true
            }
            val label = normalizeForMatch(node.contentDescription) ?: normalizeForMatch(node.text)
            label != null && label in Rules.SharedClip.REELS_TAB_LABELS
        }
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
     * @param chosen ob dieses Video ausgewählt wurde (also **nicht** der Tab-Strom ist).
     * @param startedAtMs Beginn der Ausnahme; 0 heißt „hat noch nicht angefangen“.
     */
    fun mayWatch(chosen: Boolean, swipes: Int, startedAtMs: Long, nowMs: Long): Boolean {
        if (!chosen) return false
        if (swipes > 0) return false
        if (startedAtMs <= 0L) return true
        val watched = nowMs - startedAtMs
        // Negative Dauer heißt zurückgestellte Uhr — dann beenden. Im Zweifel blocken.
        return watched in 0..MAX_WATCH_MS
    }
}
