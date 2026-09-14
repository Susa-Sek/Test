package de.shortblock.app.service

/**
 * Was mit dem Instagram-Startfeed geschehen soll.
 */
sealed interface FeedDecision {

    /** Nicht im Startfeed, oder Zustand unklar — nichts tun. Der sichere Default. */
    data object Idle : FeedDecision

    /** „Folge ich“ ist bereits aktiv, der Feed enthält nur gefolgte Accounts. */
    data object AlreadyFiltered : FeedDecision

    /**
     * Das Menü bzw. die Tab-Leiste ist offen: den Eintrag „Folge ich“ antippen.
     *
     * Nur noch von [TikTokPolicy] benutzt. TikToks Tab-Leiste ist seit Jahren stabil, dort
     * lohnt das Umschalten weiterhin; Instagram sperrt stattdessen (siehe [BlockFeed]).
     */
    data class ChooseFollowing(val node: UiNode) : FeedDecision

    /**
     * Algorithmischer Feed aktiv: die Wand hochziehen, ab [headerBottomPx] abwärts.
     *
     * Bis v0.10.2 stand hier stattdessen „Titel antippen“ und „Menüeintrag antippen“. Dieser
     * Weg hing an Instagrams Menüaufbau und ist dreimal gebrochen — jedes Mal war der Filter
     * danach still wirkungslos. Gesperrt wird jetzt, statt umzuschalten: Das braucht nur den
     * Titeltext, und der hat alle drei Umbauten überlebt.
     */
    data class BlockFeed(val headerBottomPx: Int) : FeedDecision

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
        //
        // KEINER dieser Wege darf die Kette kappen. Bis v0.10.1 gab Weg 1 sein `Idle`
        // ungeprüft durch, sobald er irgendeinen Knoten mit Titel-Kennung fand — und die
        // Wege 2 und 3 kamen nie zum Zug. Bei Instagrams mittiger Kopfzeile, deren
        // Beschriftung in einem Kindknoten steckt, war der Filter damit vollständig
        // wirkungslos: Die App hielt sich für zuständig und tat nichts.
        findTitleNodeByViewId(root)?.let { title ->
            val byViewId = fromTitle(root, title)
            if (byViewId != FeedDecision.Idle) return byViewId
        }

        val byTabs = evaluateTabs(root)
        if (byTabs != FeedDecision.Idle) return byTabs

        val header = headerTitleNode(root) ?: return FeedDecision.Idle
        return fromTitle(root, header)
    }

    /** Der gemeinsame Ablauf, sobald der Titelknoten feststeht — egal, wie er gefunden wurde. */
    private fun fromTitle(root: UiNode, title: UiNode): FeedDecision {
        val titleLabel = labelOf(title) ?: return FeedDecision.Idle

        if (Rules.InstagramFeed.FOLLOWING_TITLES.any { titleLabel == it }) {
            val marker = visibleEndMarker(root)
            return if (marker != null) FeedDecision.EndOfFeed(marker) else FeedDecision.AlreadyFiltered
        }

        if (Rules.InstagramFeed.ALGORITHMIC_TITLES.none { titleLabel == it }) {
            // Unbekannter Titel — vermutlich ein neues Layout. Lieber nichts tun als eine
            // Wand über einen Bildschirm zu legen, den niemand eingeordnet hat.
            return FeedDecision.Idle
        }

        return FeedDecision.BlockFeed(wallTopFor(root, title))
    }

    /**
     * Wo die Wand anfängt: direkt unter der Kopfzeile.
     *
     * Die Kopfzeile muss frei bleiben, sonst käme niemand mehr an den Umschalter und säße
     * fest. Fehlen die Bounds des Titelknotens, gilt [HEADER_FRACTION] — eine Wand ab 0 wäre
     * der schlimmste Ausgang: Sie verdeckt genau den Ausweg, den sie verlangt.
     */
    private fun wallTopFor(root: UiNode, title: UiNode): Int {
        title.bounds?.bottom?.takeIf { it > 0 }?.let { return it }

        val windowTop = root.bounds?.top ?: 0
        val height = (root.bounds?.bottom ?: 0) - windowTop
        return windowTop + (height * HEADER_FRACTION).toInt()
    }

    /**
     * Die Beschriftung eines Titelknotens — notfalls aus einem direkten Kind.
     *
     * Instagrams mittige Kopfzeile ist ein Container zwischen „+“ und Herz; der Text sitzt
     * eine Ebene tiefer. Ohne diesen Blick nach unten trägt der gefundene Knoten keine
     * Beschriftung und die Auswertung steigt aus.
     *
     * Bewusst nur **eine** Ebene: Wer tiefer sucht, findet irgendwann den ersten Beitrag und
     * hält ihn für den Titel.
     */
    private fun labelOf(node: UiNode): String? {
        normalizeForMatch(node.text)?.let { return it }
        normalizeForMatch(node.contentDescription)?.let { return it }

        for (index in 0 until node.childCount) {
            val child = node.child(index) ?: continue
            if (!child.isVisible) continue
            normalizeForMatch(child.text)?.let { return it }
            normalizeForMatch(child.contentDescription)?.let { return it }
        }
        return null
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

        return FeedDecision.BlockFeed(wallTopFor(root, forYou))
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

        // Stehen beide Beschriftungen nebeneinander in der Kopfzeile, ist es eine Tab-Leiste,
        // die ihren Auswahlzustand nicht meldet. Dann ist schlicht unbekannt, welcher Feed
        // vorne ist — und der reine Text sagt es auch nicht. Seit die App sperrt statt zu
        // tippen, wiegt ein Fehlgriff hier schwerer: Eine Wand über dem gefolgten Feed
        // nähme etwas weg, das erlaubt sein soll. Also nichts tun.
        if (headerHasBothLabels(root, limit)) return null

        return RuleMatcher.findNode(root) { node ->
            val top = node.bounds?.top ?: return@findNode false
            if (top > limit) return@findNode false
            val label = normalizeForMatch(node.text)
                ?: normalizeForMatch(node.contentDescription)
                ?: return@findNode false
            label in HEADER_TITLES
        }
    }

    /** Trägt die Kopfzeile gleichzeitig „Für dich“ und „Gefolgt“? Dann ist nichts entschieden. */
    private fun headerHasBothLabels(root: UiNode, limit: Int): Boolean {
        var algorithmic = false
        var following = false

        RuleMatcher.traverse(root) { node ->
            if (!node.isVisible) return@traverse false
            val top = node.bounds?.top ?: return@traverse false
            if (top > limit) return@traverse false
            val label = normalizeForMatch(node.text)
                ?: normalizeForMatch(node.contentDescription)
                ?: return@traverse false

            if (label in Rules.InstagramFeed.TAB_FOR_YOU_LABELS) algorithmic = true
            if (label in Rules.InstagramFeed.FOLLOWING_TITLES) following = true
            algorithmic && following
        }
        return algorithmic && following
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

    private fun visibleEndMarker(root: UiNode): String? {
        val node = RuleMatcher.findNode(root) { candidate ->
            val text = normalizeForMatch(candidate.text) ?: return@findNode false
            Rules.InstagramFeed.END_MARKERS.any { text.contains(it) }
        }
        return node?.text?.trim()
    }
}
