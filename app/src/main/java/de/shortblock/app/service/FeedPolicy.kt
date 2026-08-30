package de.shortblock.app.service

/**
 * Was mit dem Instagram-Startfeed geschehen soll.
 */
sealed interface FeedDecision {

    /** Nicht im Startfeed, oder Zustand unklar — nichts tun. Der sichere Default. */
    data object Idle : FeedDecision

    /** „Folge ich“ ist bereits aktiv, der Feed enthält nur gefolgte Accounts. */
    data object AlreadyFiltered : FeedDecision

    /** Algorithmischer Feed aktiv: den Titel oben links antippen, um das Menü zu öffnen. */
    data class OpenSwitcher(val node: UiNode) : FeedDecision

    /** Das Menü ist offen: den Eintrag „Folge ich“ antippen. */
    data class ChooseFollowing(val node: UiNode) : FeedDecision

    /** Ende des „Folge ich“-Feeds erreicht, ab hier kommen wieder Fremd-Inhalte. */
    data class EndOfFeed(val marker: String) : FeedDecision
}

/**
 * Zustandslose Auswertung des Instagram-Startfeeds.
 *
 * Zwei Eigenschaften, die nicht verhandelbar sind:
 *
 * 1. **Nur sichtbare Knoten zählen.** [RuleMatcher.findNode] filtert das bereits. Ohne diese
 *    Filterung gilt ein „Vorgeschlagene Beiträge“-Knoten, der noch weit unter dem Bildschirm
 *    liegt, sofort als Feed-Ende — und die App wirft beim Öffnen aus Instagram heraus.
 * 2. **Der Titel wird zuerst ausgewertet.** Stünde die Menü-Erkennung vorne, würde der bereits
 *    umgeschaltete Titel („Folge ich“) selbst als Menüeintrag gelesen — und die App würde
 *    endlos auf sich selbst tippen.
 */
object FeedPolicy {

    fun evaluate(root: UiNode?): FeedDecision {
        if (root == null) return FeedDecision.Idle
        if (!isOnHomeFeed(root)) return FeedDecision.Idle

        val title = findTitleNode(root) ?: return FeedDecision.Idle
        val titleLabel = normalizeForMatch(title.text)
            ?: normalizeForMatch(title.contentDescription)
            ?: return FeedDecision.Idle

        if (Rules.InstagramFeed.FOLLOWING_TITLES.any { titleLabel == it }) {
            val marker = visibleEndMarker(root)
            return if (marker != null) FeedDecision.EndOfFeed(marker) else FeedDecision.AlreadyFiltered
        }

        if (Rules.InstagramFeed.ALGORITHMIC_TITLES.none { titleLabel == it }) {
            // Unbekannter Titel — vermutlich hat Instagram das Layout geändert. Lieber nichts
            // tun als blind irgendwo hintippen.
            return FeedDecision.Idle
        }

        val menuEntry = findFollowingMenuEntry(root)
        return if (menuEntry != null) {
            FeedDecision.ChooseFollowing(menuEntry)
        } else {
            FeedDecision.OpenSwitcher(title)
        }
    }

    private fun isOnHomeFeed(root: UiNode): Boolean =
        RuleMatcher.containsNode(root) { node ->
            val viewId = normalizeForMatch(node.viewId) ?: return@containsNode false
            when {
                Rules.InstagramFeed.FEED_ROOT_VIEW_IDS.any { viewId.contains(it) } -> true
                Rules.InstagramFeed.FEED_TAB_VIEW_IDS.any { viewId.contains(it) } -> node.isSelected
                else -> false
            }
        }

    private fun findTitleNode(root: UiNode): UiNode? =
        RuleMatcher.findNode(root) { node ->
            val viewId = normalizeForMatch(node.viewId) ?: return@findNode false
            Rules.InstagramFeed.TITLE_VIEW_IDS.any { viewId.contains(it) }
        }

    private fun findFollowingMenuEntry(root: UiNode): UiNode? =
        RuleMatcher.findNode(root) { node ->
            val label = normalizeForMatch(node.text) ?: return@findNode false
            Rules.InstagramFeed.MENU_FOLLOWING_ENTRIES.any { label == it }
        }

    private fun visibleEndMarker(root: UiNode): String? {
        val node = RuleMatcher.findNode(root) { candidate ->
            val text = normalizeForMatch(candidate.text) ?: return@findNode false
            Rules.InstagramFeed.END_MARKERS.any { text.contains(it) }
        }
        return node?.text?.trim()
    }
}
