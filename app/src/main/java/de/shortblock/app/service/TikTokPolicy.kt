package de.shortblock.app.service

/**
 * Schaltet TikTok vom „Für dich“-Algorithmus auf „Folge ich“.
 *
 * Bewusst keine gemeinsame Oberklasse mit [FeedPolicy]: Instagram braucht zwei Tipps über ein
 * Aufklappmenü, TikTok genau einen auf einen sichtbaren Tab. Geteilt wird, was sich wirklich
 * teilen lässt — [FeedDecision], [Actions] und [RuleMatcher].
 *
 * Erkennung ausschließlich über sichtbaren Text: TikToks View-IDs sind generierte Kürzel, die
 * sich mit jeder Version ändern. Der Preis dafür ist, dass die Beschriftungen übersetzt sind —
 * deshalb stehen in [Rules.TikTokFeed] beide Sprachen.
 */
object TikTokPolicy {

    fun evaluate(root: UiNode?): FeedDecision {
        if (root == null) return FeedDecision.Idle

        val forYou = findTab(root, Rules.TikTokFeed.FOR_YOU_LABELS)
        val following = findTab(root, Rules.TikTokFeed.FOLLOWING_LABELS)

        // Kein Tab sichtbar: Wir sind nicht im Feed, sondern in DMs, Suche oder Profil.
        // Die bleiben unangetastet — sonst wäre der Schalter „Für dich blocken“ in Wahrheit
        // ein „TikTok blocken“, und dafür gibt es den anderen.
        if (forYou == null && following == null) return FeedDecision.Idle

        if (following != null && following.isSelected) return FeedDecision.AlreadyFiltered
        if (forYou == null || !forYou.isSelected) return FeedDecision.Idle

        // „Für dich“ ist aktiv. Gibt es den Zieltab, tippen wir ihn an.
        return following?.let { FeedDecision.ChooseFollowing(it) } ?: FeedDecision.Idle
    }

    private fun findTab(root: UiNode, labels: List<String>): UiNode? =
        RuleMatcher.findNode(root) { node ->
            val label = normalizeForMatch(node.text)
                ?: normalizeForMatch(node.contentDescription)
                ?: return@findNode false
            labels.any { label == it }
        }
}
