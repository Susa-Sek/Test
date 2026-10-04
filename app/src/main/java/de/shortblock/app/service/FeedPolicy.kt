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
     * lohnt das Umschalten weiterhin; bei Instagram wird nur erinnert (siehe [RemindToSwitch]).
     */
    data class ChooseFollowing(val node: UiNode) : FeedDecision

    /**
     * Algorithmischer Feed aktiv — **erinnern, nicht festhalten**.
     *
     * Die Geschichte dieser Zeile ist eine Kette von Fehlschlägen, und das Ergebnis ist
     * bewusst bescheiden. Bis v0.10.2 stand hier „Titel antippen, Menüeintrag antippen“; das
     * hing an Instagrams Menüaufbau und ist dreimal gebrochen, jedes Mal war der Filter danach
     * still wirkungslos. v0.11 zog stattdessen eine Wand über den Feed — und die blieb nach
     * dem Verlassen von Instagram über fremden Apps stehen und machte das Gerät unbenutzbar.
     *
     * Deshalb jetzt nur noch eine kurze Erinnerung, die sich selbst abräumt. Sie hält niemanden
     * fest und kann nichts blockieren. **Wer hier wieder etwas einbaut, das Berührungen
     * schluckt, wiederholt den teuersten Fehler dieser App.**
     */
    data object RemindToSwitch : FeedDecision

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
            // Unbekannter Titel — vermutlich ein neues Layout. Lieber nichts tun als auf einem
            // Bildschirm zu reagieren, den niemand eingeordnet hat.
            return FeedDecision.Idle
        }

        return FeedDecision.RemindToSwitch
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

        return FeedDecision.RemindToSwitch
    }

    /**
     * Der antippbare „Gefolgt“-Knoten in der **Kopfzeile** — oder null.
     *
     * **Nie über eine blosse Textsuche.** „Gefolgt“ steht bei Instagram auch als Knopf unter
     * jedem fremden Profil; ein Tipp darauf entfolgt jemanden, und das merkt man erst Wochen
     * später. Deshalb liegt die Beschriftung in [Rules.InstagramFeed.MENU_AMBIGUOUS_FOLLOWING_ENTRIES]
     * und nicht bei den eindeutigen.
     *
     * Drei Gatter, alle nötig:
     *
     * 1. Der Aufrufer ruft das hier nur nach [FeedDecision.RemindToSwitch] — dann ist der
     *    „Für dich“-Tab nachweislich **ausgewählt**, wir sind also sicher im Startfeed.
     * 2. Der Knoten liegt in der obersten [HEADER_FRACTION] des Fensters. Ein Profil-Knopf
     *    liegt nie in der Kopfzeile.
     * 3. [Actions.clickNearest] deckelt die Fläche zusätzlich auf 30 %.
     *
     * Ohne bekannte Fensterhöhe wird **nichts** geliefert: Ohne Bounds lässt sich Gatter 2
     * nicht prüfen, und dann ist Nichtstun die einzig vertretbare Antwort.
     */
    fun followingTabToTap(root: UiNode): UiNode? {
        val windowTop = root.bounds?.top ?: return null
        val windowBottom = root.bounds?.bottom ?: return null
        val height = windowBottom - windowTop
        if (height <= 0) return null
        val headerBottom = windowTop + (height * HEADER_FRACTION).toInt()

        val labels = Rules.InstagramFeed.MENU_FOLLOWING_ENTRIES +
            Rules.InstagramFeed.MENU_AMBIGUOUS_FOLLOWING_ENTRIES

        return RuleMatcher.findNode(root) { node ->
            val bottom = node.bounds?.bottom ?: return@findNode false
            if (bottom > headerBottom) return@findNode false
            val label = normalizeForMatch(node.text)
                ?: normalizeForMatch(node.contentDescription)
                ?: return@findNode false
            labels.any { label == it }
        }
    }

    /**
     * Die scrollbare Liste des Startfeeds — für die Bremse.
     *
     * **Zwei Beine, und das zweite kennt keine Namen.** v0.14.1 suchte ausschliesslich über
     * [Rules.InstagramFeed.FEED_ROOT_VIEW_IDS]; auf einem echten Gerät stand dort keine der
     * beiden Kennungen im Baum, die Bremse meldete `brake_no_list` und tat nichts. Dieselbe
     * Falle wie bei `yt_shorts_player`: eine Handvoll Namen, die ein App-Update umbenennt.
     *
     * Das zweite Bein sucht deshalb nach der **Form**: der höchste sichtbare scrollbare Knoten,
     * der praktisch die volle Fensterbreite einnimmt. Das ist in einem Startfeed eindeutig die
     * Beitragsliste.
     *
     * Warum das hier erlaubt ist, obwohl `CLAUDE.md` vor weiten Mustern warnt: Der Aufrufer hat
     * bereits [FeedDecision.RemindToSwitch] — es steht also **positiv fest**, dass der
     * algorithmische Startfeed vorne ist. Innerhalb dieses Bildschirms ist „das grosse
     * senkrechte Scrollding“ nicht mehrdeutig. Und die schlimmste Folge eines Fehlgriffs ist
     * ein Zurück-Scroll, der nichts hinterlässt.
     *
     * Verglichen wird die **Breite**, nicht die Fläche: Die Höhe einer scrollbaren Liste ist
     * ihre Layout-Höhe und kann das Fenster weit überschreiten — siehe den `minAreaFraction`-
     * Fallstrick in `CLAUDE.md`.
     */
    fun feedListToBrake(root: UiNode): UiNode? {
        byViewId(root)?.let { return it }
        return widestScroller(root)
    }

    private fun byViewId(root: UiNode): UiNode? = RuleMatcher.findNode(root) { node ->
        val viewId = normalizeForMatch(node.viewId) ?: return@findNode false
        Rules.InstagramFeed.FEED_ROOT_VIEW_IDS.any { viewId.contains(it) }
    }

    private fun widestScroller(root: UiNode): UiNode? {
        val windowWidth = root.bounds?.width ?: return null
        if (windowWidth <= 0) return null
        val minWidth = windowWidth * MIN_LIST_WIDTH_FRACTION

        var best: UiNode? = null
        RuleMatcher.traverse(root) { node ->
            if (node.isVisible && node.isScrollable) {
                val bounds = node.bounds
                if (bounds != null && bounds.width >= minWidth) {
                    if (bounds.height > (best?.bounds?.height ?: 0)) best = node
                }
            }
            false
        }
        return best
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

    /**
     * Wie breit die Beitragsliste mindestens sein muss, gemessen am Fenster.
     *
     * Eine Feed-Liste füllt die Breite; ein waagerechter Stories-Streifen oder ein Karussell
     * tut das zwar auch, ist aber flacher — deshalb entscheidet am Ende die Höhe.
     */
    private const val MIN_LIST_WIDTH_FRACTION = 0.8f

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
