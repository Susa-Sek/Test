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

        // Drei Bauarten der Kopfzeile, in dieser Reihenfolge — jede spätere ist ungenauer als
        // die vorige, deshalb kommt sie später dran:
        //   1. Titel mit bekannter View-ID (Aufklappmenü, seit v0.1)
        //   2. Tab-Leiste über den Auswahl-Zustand (seit v0.6)
        //   3. Titel über Text und Position, ganz ohne View-ID (seit v0.8.1)
        findTitleNodeByViewId(root)?.let { return fromTitle(root, it) }

        val byTabs = evaluateTabs(root)
        if (byTabs != FeedDecision.Idle) return byTabs

        val header = headerTitleNode(root) ?: return FeedDecision.Idle
        return fromTitle(root, header)
    }

    /** Der gemeinsame Ablauf, sobald der Titelknoten feststeht — egal, wie er gefunden wurde. */
    private fun fromTitle(root: UiNode, title: UiNode): FeedDecision {
        val titleLabel = normalizeForMatch(title.text)
            ?: normalizeForMatch(title.contentDescription)
            ?: return FeedDecision.Idle

        if (Rules.InstagramFeed.FOLLOWING_TITLES.any { titleLabel == it }) {
            val marker = visibleEndMarker(root)
            return if (marker != null) FeedDecision.EndOfFeed(marker) else FeedDecision.AlreadyFiltered
        }

        if (Rules.InstagramFeed.ALGORITHMIC_TITLES.none { titleLabel == it }) {
            // Unbekannter Titel — vermutlich ein neues Layout. Lieber nichts tun als blind
            // irgendwo hinzutippen.
            return FeedDecision.Idle
        }

        val menuEntry = findFollowingMenuEntry(root)
        return if (menuEntry != null) {
            FeedDecision.ChooseFollowing(menuEntry)
        } else {
            FeedDecision.OpenSwitcher(title)
        }
    }

    /**
     * Der zweite Weg: „Für dich“ und „Folge ich“ als Tabs nebeneinander, wie bei TikTok.
     *
     * Umgeschaltet wird nur, wenn der Algorithmus-Tab **ausgewählt** ist und der Zieltab
     * sichtbar daneben liegt. Dieses UND-Gatter ist der ganze Schutz: Ohne die
     * Auswahl-Bedingung würde die App auf jeden Text „Folge ich“ tippen, der irgendwo im Baum
     * steht — etwa auf den Knopf im Profil eines fremden Accounts.
     */
    private fun evaluateTabs(root: UiNode): FeedDecision {
        val forYou = findTab(root, Rules.InstagramFeed.TAB_FOR_YOU_LABELS)
        val following = findTab(root, Rules.InstagramFeed.MENU_FOLLOWING_ENTRIES)

        if (following != null && following.isSelected) {
            val marker = visibleEndMarker(root)
            return if (marker != null) FeedDecision.EndOfFeed(marker) else FeedDecision.AlreadyFiltered
        }
        if (forYou == null || !forYou.isSelected) return FeedDecision.Idle

        return following?.let { FeedDecision.ChooseFollowing(it) } ?: FeedDecision.Idle
    }

    private fun findTab(root: UiNode, labels: List<String>): UiNode? =
        RuleMatcher.findNode(root) { node ->
            val label = normalizeForMatch(node.text)
                ?: normalizeForMatch(node.contentDescription)
                ?: return@findNode false
            labels.any { label == it }
        }

    private fun isOnHomeFeed(root: UiNode): Boolean {
        val byViewId = RuleMatcher.containsNode(root) { node ->
            val viewId = normalizeForMatch(node.viewId) ?: return@containsNode false
            when {
                Rules.InstagramFeed.FEED_ROOT_VIEW_IDS.any { viewId.contains(it) } -> true
                Rules.InstagramFeed.FEED_TAB_VIEW_IDS.any { viewId.contains(it) } -> node.isSelected
                else -> false
            }
        }
        if (byViewId) return true

        // Ohne diese zweite Zeile stiege evaluate() in der ersten aus, und der Textweg käme
        // nie zum Zug. Eine Kopfzeile, die exakt „Für dich“ oder „Folge ich“ heißt, ist ein
        // belastbarer Beleg für den Startfeed — diese Beschriftung trägt in Instagram kein
        // anderer Bildschirm.
        return headerTitleNode(root) != null
    }

    /**
     * Der dritte Weg: die Kopfzeile über **Text und Position**, ganz ohne View-ID.
     *
     * Instagram hat die Kopfzeile inzwischen dreimal umgebaut — Titel links mit Aufklappmenü,
     * Tab-Leiste, und jetzt ein mittiger Titel zwischen „+“ und Herz. Bei jedem Umbau brachen
     * die View-IDs; der Text „Für dich“ hat alle drei überlebt. Deshalb dieselbe Entscheidung
     * wie bei [TikTokPolicy], die von Anfang an ohne IDs auskommen musste.
     *
     * Zwei Bedingungen, beide notwendig:
     *
     * 1. **Exakte Gleichheit**, nicht `contains`. Im Feed steht „Vorgeschlagen für dich“ an
     *    einzelnen Beiträgen; mit `contains` würde die App mitten im Feed zutreffen und dort
     *    hintippen.
     * 2. **Oberste [HEADER_FRACTION] des Fensters.** Der Stories-Streifen beginnt bei rund
     *    einem Viertel der Höhe und bleibt damit draußen; für große Schrift und Notch ist Luft.
     */
    private fun headerTitleNode(root: UiNode): UiNode? {
        val windowBottom = root.bounds?.bottom ?: return null
        val windowTop = root.bounds?.top ?: return null
        val height = windowBottom - windowTop
        if (height <= 0) return null
        val limit = windowTop + (height * HEADER_FRACTION).toInt()

        return RuleMatcher.findNode(root) { node ->
            val top = node.bounds?.top ?: return@findNode false
            if (top > limit) return@findNode false
            val label = normalizeForMatch(node.text)
                ?: normalizeForMatch(node.contentDescription)
                ?: return@findNode false
            label in HEADER_TITLES
        }
    }

    private const val HEADER_FRACTION = 0.20f

    /**
     * Beschriftungen, die eine Kopfzeile allein schon ausweisen.
     *
     * Ausdrücklich **ohne** „instagram“ aus [Rules.InstagramFeed.ALGORITHMIC_TITLES]: Das Wort
     * steht überall in der App — im Logo, in Beschreibungen, auf Explore. Als Beleg für den
     * Startfeed taugt nur die Feed-Beschriftung selbst.
     */
    private val HEADER_TITLES: Set<String> =
        (Rules.InstagramFeed.FOLLOWING_TITLES + Rules.InstagramFeed.TAB_FOR_YOU_LABELS).toSet()

    private fun findTitleNodeByViewId(root: UiNode): UiNode? =
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
