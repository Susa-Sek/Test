package de.shortblock.app.service

/**
 * Was mit Instagrams Explore-Tab geschehen soll.
 */
sealed interface ExploreDecision {

    /** Nicht auf dem Vorschlagsraster — nichts tun. Der sichere Default. */
    data object Idle : ExploreDecision

    /** Der Nutzer sucht gerade. Suchen ist erlaubt, also still bleiben. */
    data object Searching : ExploreDecision

    /** Raster ist da, aber die Schonfrist läuft noch. */
    data object Grace : ExploreDecision

    /** Raster ist da und die Schonfrist ist abgelaufen: raus hier. */
    data class Block(val marker: String) : ExploreDecision
}

/**
 * Zustandslose Auswertung des Explore-Tabs.
 *
 * Der Startfeed lässt sich auf „Folge ich“ umschalten; für Explore gibt es kein Gegenstück —
 * dort ist jeder Beitrag ein Vorschlag von jemandem, dem man nicht folgt. Also wird nicht
 * umgeschaltet, sondern verlassen.
 *
 * **Die Schonfrist ist der ganze Trick.** Explore und Suche sind bei Instagram derselbe Tab:
 * Wer die Lupe antippt, landet zwangsläufig erst auf dem Raster, auch wenn er nur ein Profil
 * suchen will. Würde sofort geblockt, käme niemand mehr an das Suchfeld — die Sperre nähme
 * eine Funktion mit, die niemand abschalten wollte. Also ein paar Sekunden Ruhe: Wer in
 * dieser Zeit die Suche antippt, verschwindet das Raster von selbst und es passiert nichts.
 * Wer stattdessen anfängt zu scrollen, fliegt raus.
 *
 * **Was hier bewusst fehlt, ist ein Rückfall auf den ausgewählten Lupen-Tab.** Findet keine
 * der bekannten Raster-Kennungen einen Knoten, tut die App gar nichts. Das ist die
 * unbefriedigende, aber richtige Variante: Ein Rückfall auf „Lupe ist ausgewählt“ würde auf
 * einer unbekannten Instagram-Fassung auch bei jeder Suche feuern — und ein Fehlalarm ist
 * teurer als eine Lücke. Wenn Instagram die Namen ändert, zeigt der Diagnose-Bildschirm die
 * echten Kennungen des Geräts.
 */
object ExplorePolicy {

    /** Zeit zum Antippen des Suchfelds, bevor das Raster als Vorschlagsraster gilt. */
    const val GRACE_MILLIS = 3_000L

    /**
     * @param onExploreSinceMillis Wann das Raster zuerst gesehen wurde; `null`, wenn gerade
     *   erst. Der Aufrufer hält diesen Zeitstempel — die Auswertung selbst bleibt zustandslos
     *   und damit prüfbar.
     */
    fun evaluate(
        root: UiNode?,
        onExploreSinceMillis: Long?,
        nowMillis: Long,
        graceMillis: Long = GRACE_MILLIS,
    ): ExploreDecision {
        if (root == null) return ExploreDecision.Idle

        // Zuerst die Suche: Sie hat immer Vorrang, sonst blockt die App jemanden, der nur
        // ein Profil sucht.
        if (isSearching(root)) return ExploreDecision.Searching

        val grid = gridNode(root) ?: return ExploreDecision.Idle

        if (onExploreSinceMillis == null) return ExploreDecision.Grace
        if (nowMillis - onExploreSinceMillis < graceMillis) return ExploreDecision.Grace

        return ExploreDecision.Block(markerOf(grid))
    }

    /** Ist das Vorschlagsraster sichtbar und gross genug, um das Ziel des Tabs zu sein? */
    private fun gridNode(root: UiNode): UiNode? {
        val windowArea = RuleMatcher.windowArea(root)
        return RuleMatcher.findNode(root) { node ->
            if (!node.isVisible) return@findNode false
            val viewId = normalizeForMatch(node.viewId) ?: return@findNode false
            if (Rules.InstagramExplore.GRID_VIEW_IDS.none { viewId.contains(it) }) return@findNode false
            hasGridSize(node, windowArea)
        }
    }

    private fun hasGridSize(node: UiNode, windowArea: Long): Boolean {
        if (windowArea <= 0L) return false
        val area = node.bounds?.area ?: return false
        return area >= windowArea * Rules.InstagramExplore.MIN_GRID_AREA_FRACTION
    }

    /**
     * Sucht der Nutzer gerade? Zwei Wege, weil die View-Kennungen je nach Fassung wechseln —
     * der Textweg hält auch dann noch, wenn die Kennung nicht mehr passt.
     */
    private fun isSearching(root: UiNode): Boolean = RuleMatcher.containsNode(root) { node ->
        if (!node.isVisible) return@containsNode false

        val viewId = normalizeForMatch(node.viewId)
        if (viewId != null && Rules.InstagramExplore.SEARCH_MODE_VIEW_IDS.any { viewId.contains(it) }) {
            return@containsNode true
        }

        // Exakte Gleichheit, nicht contains: „recent“ steckt sonst in jedem zweiten
        // Beitragstext und schaltet die Sperre stillschweigend ab.
        val label = normalizeForMatch(node.text) ?: normalizeForMatch(node.contentDescription)
        label != null && Rules.InstagramExplore.SEARCH_MODE_LABELS.any { it == label }
    }

    private fun markerOf(node: UiNode): String =
        normalizeForMatch(node.viewId)?.substringAfterLast('/') ?: "explore_grid"
}
