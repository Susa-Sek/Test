package de.shortblock.app.service

/**
 * ALLE Erkennungsmuster der App stehen in dieser Datei — mit Absicht.
 *
 * Instagram und YouTube ändern ihre View-IDs mit jedem größeren Update. Wenn die App
 * irgendwann nichts mehr blockt, ist das hier die einzige Datei, die angefasst werden muss.
 * Der Diagnose-Screen in der App zeigt die aktuell vorhandenen IDs an, damit man sie ohne
 * Laptop und ohne adb ablesen kann.
 *
 * Leitsatz für jede Regel hier: **Ein Fehlalarm ist teurer als eine Lücke.** Wer versehentlich
 * aus Instagram fliegt, kann die App nicht mehr benutzen; wer ein Reel zu viel sieht, ärgert
 * sich kurz. Im Zweifel also lieber nicht blocken.
 */
enum class Feature {
    INSTAGRAM_REELS,
    INSTAGRAM_FEED,

    /**
     * Das Vorschlagsraster hinter der Lupe. Die Suche selbst bleibt bedienbar — sie ist
     * derselbe Tab, aber nicht dasselbe Ziel.
     */
    INSTAGRAM_EXPLORE,
    YOUTUBE_SHORTS,

    /** Nur den „Für dich“-Algorithmus abschalten, „Folge ich“ bleibt nutzbar. */
    TIKTOK_FYP,

    /** TikTok komplett zu. Hat Vorrang vor [TIKTOK_FYP]. */
    TIKTOK_ALL,
}

object Packages {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"

    /**
     * TikTok läuft unter mehreren Paketnamen — und wer einen davon vergisst, bei dem tut die App
     * auf dem betroffenen Gerät schlicht nichts. `musically.go` ist **TikTok Lite** und war der
     * Grund, warum „TikTok ganz blocken“ bei manchen Installationen wirkungslos blieb.
     *
     * Wenn hier je wieder etwas fehlt: Diagnose einschalten, TikTok öffnen, unter „zuletzt
     * gesehene Apps“ steht der echte Paketname.
     */
    const val TIKTOK_GLOBAL = "com.zhiliaoapp.musically"
    const val TIKTOK_LITE = "com.zhiliaoapp.musically.go"
    const val TIKTOK_REGIONAL = "com.ss.android.ugc.trill"
    const val TIKTOK_AWEME = "com.ss.android.ugc.aweme"

    val TIKTOK = setOf(TIKTOK_GLOBAL, TIKTOK_LITE, TIKTOK_REGIONAL, TIKTOK_AWEME)

    /**
     * Browser. Ohne die ist jeder Blocker eine Papiertür: youtube.com/shorts im Browser
     * liefert dieselbe Endlosschleife wie die App.
     */
    val BROWSERS = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
    )

    val WATCHED = setOf(INSTAGRAM, YOUTUBE) + TIKTOK + BROWSERS
}

/**
 * Eine Regel trifft zu, sobald *irgendein sichtbarer* Knoten im Baum *eines* ihrer Muster
 * erfüllt.
 *
 * [requireSelected] gilt für die gesamte Regel: sie feuert dann nur an einem Knoten, der
 * gerade ausgewählt ist. Das ist für Tab-Leisten nötig — der Shorts-Tab ist immer im Baum,
 * relevant ist er nur, wenn er aktiv ist.
 *
 * [minAreaFraction] verlangt, dass der Treffer mindestens diesen Anteil des Fensters einnimmt.
 * Damit unterscheidet sich ein Vollbild-Viewer von einer eingebetteten Vorschau im Feed, die
 * zufällig dieselbe View-ID trägt. Ist die Größe unbekannt, wird NICHT geblockt.
 *
 * [matchAnyWindow] greift ohne jede Inhaltsprüfung, sobald das Paket im Vordergrund ist. Nur
 * für „App ganz sperren“ gedacht — dort ist es genau richtig und unkaputtbar, überall sonst
 * wäre es ein Holzhammer.
 *
 * [viewIdMustContain] ist ein UND-Gatter: Der Knoten muss zusätzlich zu Text oder Beschreibung
 * auch diese View-ID tragen. Gebraucht wird das für Browser — „youtube.com/shorts“ steht sonst
 * auch in jedem Suchergebnis, und die App würde einen aus der Google-Suche werfen.
 */
data class Rule(
    val id: String,
    val feature: Feature,
    val packageName: String,
    val viewIdContains: List<String> = emptyList(),
    val contentDescriptionEquals: List<String> = emptyList(),
    val textContains: List<String> = emptyList(),
    val requireSelected: Boolean = false,
    val minAreaFraction: Float = 0f,
    val matchAnyWindow: Boolean = false,
    val viewIdMustContain: List<String> = emptyList(),
) {
    fun matches(node: UiNode, windowArea: Long): Boolean {
        // Fenster-Regeln werden vor der Traversierung ausgewertet, nicht je Knoten.
        if (matchAnyWindow) return false
        if (!node.isVisible) return false
        if (requireSelected && !node.isSelected) return false
        if (!hasRequiredSize(node, windowArea)) return false
        if (!passesViewIdGate(node)) return false

        val viewId = normalizeForMatch(node.viewId)
        if (viewId != null && viewIdContains.any { viewId.contains(it) }) return true

        val description = normalizeForMatch(node.contentDescription)
        if (description != null && contentDescriptionEquals.any { description == it }) return true

        val text = normalizeForMatch(node.text)
        if (text != null && textContains.any { text.contains(it) }) return true

        return false
    }

    private fun passesViewIdGate(node: UiNode): Boolean {
        if (viewIdMustContain.isEmpty()) return true
        val viewId = normalizeForMatch(node.viewId) ?: return false
        return viewIdMustContain.any { viewId.contains(it) }
    }

    private fun hasRequiredSize(node: UiNode, windowArea: Long): Boolean {
        if (minAreaFraction <= 0f) return true
        if (windowArea <= 0L) return false
        val area = node.bounds?.area ?: return false
        return area >= windowArea * minAreaFraction
    }
}

object Rules {

    /**
     * Adressleisten der gängigen Browser. Chrome-Ableger teilen sich `url_bar`, Firefox und
     * Samsung Internet kochen eigene Süppchen.
     */
    private val BROWSER_URL_BAR_IDS = listOf(
        "url_bar",
        "mozac_browser_toolbar_url_view",
        "location_bar_edit_text",
        "omnibartextinput",
        "search_bar",
    )

    // Hinweis für später: Diese Liste MUSS vor BLOCK_RULES stehen. Kotlin initialisiert die
    // Eigenschaften eines object in Textreihenfolge; steht sie danach, ist sie beim Aufbau der
    // Regeln noch null und die gesamte Klasse schlägt beim Laden fehl.

    /**
     * Regeln, die unmittelbar zum Zurücknavigieren führen.
     *
     * Der Instagram-Feed steht bewusst NICHT hier: er braucht eine Zustandsbetrachtung
     * (welcher Feed ist aktiv?) statt eines einzelnen Treffers und liegt deshalb in
     * [InstagramFeed] plus [FeedPolicy].
     *
     * Jedes Muster hat eine eigene Regel-ID. Das ist kein Selbstzweck: Wenn ein Fehlalarm
     * auftritt, nennt der Home-Screen genau die ID, die gefeuert hat — sonst rätselt man,
     * welches von vier Mustern schuld war.
     */
    val BLOCK_RULES: List<Rule> = listOf(

        // --- Instagram Reels -------------------------------------------------------------
        //
        // ACHTUNG, häufigste Fehlerquelle: Instagram nennt Reels intern "clips".
        // "reel_*" bezeichnet bei Instagram die STORIES (reel_tray, reel_viewer_...).
        // Ein Muster "reel_" würde hier also Stories blocken statt Reels — deshalb
        // matchen wir ausschließlich auf "clips_".
        //
        // Die Größenschranke trennt den Vollbild-Viewer von eingebetteten Clips-Containern
        // in Feed-Beiträgen. Ein Feed-Medium ist etwa quadratisch und belegt selten mehr als
        // die Hälfte des Fensters; der Viewer belegt praktisch das ganze.
        Rule(
            id = "ig_clips_viewer",
            feature = Feature.INSTAGRAM_REELS,
            packageName = Packages.INSTAGRAM,
            viewIdContains = listOf("clips_viewer"),
            minAreaFraction = 0.6f,
        ),

        // --- YouTube Shorts --------------------------------------------------------------
        //
        // Umgekehrt zu Instagram: YouTube nennt Shorts intern "reel".
        //
        // Bewusst eng gehalten: Auf der YouTube-Startseite liegt ein Shorts-Regal mitten
        // im normalen Feed. Ein zu weites Muster („shorts_…“) würde den Nutzer beim
        // Scrollen aus der Startseite werfen.
        Rule(
            id = "yt_shorts_player",
            feature = Feature.YOUTUBE_SHORTS,
            packageName = Packages.YOUTUBE,
            viewIdContains = listOf(
                "reel_recycler",
                "reel_watch_fragment",
                "reel_player_page_container",
            ),
        ),
        Rule(
            id = "yt_shorts_tab_selected",
            feature = Feature.YOUTUBE_SHORTS,
            packageName = Packages.YOUTUBE,
            contentDescriptionEquals = listOf("shorts"),
            // Manche Fassungen tragen die Beschriftung als Text statt als Beschreibung. Das
            // UND-Gatter requireSelected bleibt und trägt weiterhin die ganze Sicherheit:
            // Ein nicht ausgewählter Knoten mit „Shorts“ darin löst nichts aus.
            textContains = listOf("shorts"),
            requireSelected = true,
        ),

        // HIER STAND `yt_shorts_fullscreen` — und es war ein Fehlalarm. Nicht wieder einbauen.
        //
        // v0.11.2 ergänzte hier eine dritte Regel: `viewIdContains = ["reel_"]` ab 60 %
        // Fensterfläche, als Netz gegen umbenannte Kennungen. Sie blieb zunächst wirkungslos,
        // weil `allowsSingleClip` damals jeden Shorts-Treffer durchwinkte. Als v0.11.3 dieses
        // Loch schloss, blockte sie zum ersten Mal wirklich — und zwar **normale Videos**.
        //
        // Warum: `reel_` trifft als Präfix auch `reel_shelf_*`, das Shorts-Regal, und das steht
        // auf der Startseite UND in der Empfehlungsliste unter jedem normalen Video. Die
        // Grössenschranke rettet das nicht: `getBoundsInScreen` liefert die **gelegten** Bounds,
        // nicht den sichtbaren Ausschnitt. Ein scrollbarer Regal-Container ist höher als der
        // Bildschirm und reisst die 60 % damit, während nur ein Streifen zu sehen ist —
        // `isVisibleToUser` bleibt dabei wahr.
        //
        // Der Vergleich mit `ig_clips_viewer` war deshalb falsch: Der matcht auf einen
        // **genauen Namen**, nicht auf ein Präfix. Gleiche Mechanik, andere Musterbreite.
        //
        // Ein drittes Bein darf wiederkommen — aber mit den Kennungen, die der Diagnose-Schirm
        // auf einem echten Gerät zeigt, nicht mit geratenen Präfixen.
        // --- TikTok komplett ---------------------------------------------------------------
        //
        // Kein Muster: matchAnyWindow greift auf jedem Fenster des Pakets. Das ist die einzige
        // Regel der App, die nichts erkennen muss — und damit die einzige, die ein TikTok-Update
        // nicht brechen kann.
    ) + Packages.TIKTOK.map { tiktokPackage ->
        Rule(
            id = "tiktok_all",
            feature = Feature.TIKTOK_ALL,
            packageName = tiktokPackage,
            matchAnyWindow = true,
        )
    } + Packages.BROWSERS.flatMap { browser -> browserRules(browser) }

    /**
     * Browser-Regeln: dieselben drei Schalter, nur über die Adressleiste statt über die App.
     *
     * Entscheidend ist das UND-Gatter auf die Adressleisten-IDs. Ohne das würde die Regel auch
     * auf einem Suchergebnis greifen, in dem „youtube.com/shorts“ bloß als Text steht — und
     * einen mitten aus der Google-Suche werfen.
     */
    private fun browserRules(browserPackage: String): List<Rule> = listOf(
        Rule(
            id = "browser_yt_shorts",
            feature = Feature.YOUTUBE_SHORTS,
            packageName = browserPackage,
            viewIdMustContain = BROWSER_URL_BAR_IDS,
            textContains = listOf("youtube.com/shorts", "m.youtube.com/shorts"),
        ),
        Rule(
            id = "browser_ig_reels",
            feature = Feature.INSTAGRAM_REELS,
            packageName = browserPackage,
            viewIdMustContain = BROWSER_URL_BAR_IDS,
            textContains = listOf("instagram.com/reel"),
        ),
        Rule(
            id = "browser_ig_explore",
            feature = Feature.INSTAGRAM_EXPLORE,
            packageName = browserPackage,
            viewIdMustContain = BROWSER_URL_BAR_IDS,
            textContains = listOf("instagram.com/explore"),
        ),
        Rule(
            id = "browser_tiktok",
            feature = Feature.TIKTOK_ALL,
            packageName = browserPackage,
            viewIdMustContain = BROWSER_URL_BAR_IDS,
            textContains = listOf("tiktok.com"),
        ),
    )


    /**
     * Muster für den Instagram-Startfeed.
     *
     * Strategie: Instagram hat weiterhin den chronologischen „Folge ich“-Feed, der
     * ausschließlich Beiträge gefolgter Accounts enthält. Den zu erzwingen ist deutlich
     * robuster, als jeden einzelnen Vorschlags-Beitrag zu erkennen und zu überdecken.
     */
    object InstagramFeed {

        /** Der Startfeed ist vorne, wenn einer dieser Knoten sichtbar ist. */
        val FEED_ROOT_VIEW_IDS = listOf(
            "feed_recycler_view",
            "main_feed_recycler",
        )

        /**
         * Der Home-Tab in der unteren Leiste. Anders als die Recycler-IDs ist dieser Knoten
         * IMMER im Baum — auch auf Explore oder im Profil. Er zählt deshalb nur als Beleg für
         * den Startfeed, wenn er ausgewählt ist.
         */
        val FEED_TAB_VIEW_IDS = listOf(
            "feed_tab",
        )

        /** Der antippbare Titel oben links, der den Feed umschaltet. */
        val TITLE_VIEW_IDS = listOf(
            "action_bar_title",
            "action_bar_textview_title",
            "action_bar_large_title",
            "feed_type_menu_button",
        )

        /**
         * Titeltext, wenn der gefilterte Feed bereits aktiv ist — dann ist nichts zu tun.
         *
         * „Gefolgt“ ist die Beschriftung, die Instagram seit Herbst 2026 in der deutschen
         * Fassung benutzt; „Folge ich“ steht daneben, weil ältere Fassungen sie noch tragen.
         * Fehlte die neue, hielt die App den schon umgeschalteten Feed für einen unbekannten
         * Titel und tat gar nichts mehr — auch das Feed-Ende wurde nie erkannt.
         */
        val FOLLOWING_TITLES = listOf(
            "following",
            "folge ich",
            "gefolgt",
            "favorites",
            "favoriten",
        )

        /** Titeltext des algorithmischen Feeds. Dann wird umgeschaltet. */
        val ALGORITHMIC_TITLES = listOf(
            "instagram",
            "for you",
            "für dich",
        )

        /**
         * Eintrag im aufklappenden Menü, der angetippt werden soll.
         *
         * Nur Beschriftungen, die in Instagram sonst nirgends als Knopf vorkommen. Sie dürfen
         * ohne weiteren Nachweis angetippt werden.
         */
        val MENU_FOLLOWING_ENTRIES = listOf(
            "following",
            "folge ich",
        )

        /**
         * Dasselbe Ziel, aber mit einer Beschriftung, die anderswo gefährlich ist.
         *
         * **„Gefolgt“ ist auch der Zustand des Folgen-Knopfes an einem Beitrag.** Wer das Wort
         * blind in [MENU_FOLLOWING_ENTRIES] legt, riskiert, dass die App im „Für dich“-Feed
         * auf den Knopf eines vorgeschlagenen Beitrags tippt — und dem Nutzer stillschweigend
         * ein Abo kündigt. Ein Fehlalarm ist teurer als eine Lücke, und dieser hier wäre
         * teurer als die meisten.
         *
         * Deshalb wird ein solcher Eintrag nur angetippt, wenn [MENU_COMPANION_ENTRIES]
         * daneben sichtbar ist — dann steht fest, dass ein Menü offen ist und kein Beitrag.
         */
        val MENU_AMBIGUOUS_FOLLOWING_ENTRIES = listOf(
            "gefolgt",
        )

        /**
         * Der jeweils andere Eintrag des Umschaltmenüs. Seine Anwesenheit ist der Beweis, dass
         * das Menü offen ist: Ein Folgen-Knopf an einem Beitrag hat kein „Favoriten“ neben sich.
         */
        val MENU_COMPANION_ENTRIES = listOf(
            "favorites",
            "favoriten",
        )

        /**
         * Beschriftung des Algorithmus-Tabs, falls Instagram eine Tab-Leiste zeigt statt des
         * Aufklappmenüs.
         *
         * Die neueren Oberflächen setzen „Für dich“ und „Folge ich“ nebeneinander, wie TikTok
         * es tut. Ohne diese Liste findet [FeedPolicy] weder Titel noch Menü und tut still gar
         * nichts — der stillste aller Fehler.
         */
        val TAB_FOR_YOU_LABELS = listOf(
            "for you",
            "für dich",
        )

        /**
         * Ende des „Folge ich“-Feeds. Ab hier schiebt Instagram wieder Fremd-Inhalte nach.
         *
         * Zwei Bedingungen, beide notwendig, beide je einmal schmerzhaft gelernt:
         * 1. Der Folge-ich-Feed muss aktiv sein — sonst wirft das Label „Vorgeschlagen für
         *    dich“ an einem einzelnen Beitrag im normalen Feed sofort aus der App.
         * 2. Der Marker muss SICHTBAR sein. Der Baum enthält auch Knoten weit unterhalb des
         *    Bildschirms; ohne diese Prüfung gilt der Feed als beendet, bevor man ihn
         *    überhaupt gesehen hat.
         */
        val END_MARKERS = listOf(
            "you're all caught up",
            "all caught up",
            "du bist auf dem neuesten stand",
            "auf dem neuesten stand",
            "suggested posts",
            "suggested for you",
            "vorgeschlagene beiträge",
            "vorschläge für dich",
        )
    }

    /**
     * Muster für Instagrams Explore-Tab — das Raster hinter der Lupe.
     *
     * Der Startfeed lässt sich auf „Folge ich“ zwingen; für Explore gibt es kein Gegenstück,
     * dort ist alles Vorschlag. Deshalb wird hier nicht umgeschaltet, sondern verlassen.
     *
     * Die Schwierigkeit ist, dass Explore und Suche derselbe Tab sind. Wer das Raster
     * blockt, nimmt einem auch die Profilsuche — deshalb die Unterscheidung in
     * [ExplorePolicy]: Sobald Instagram in den Suchmodus wechselt, ist das Raster weg und
     * es wird nichts mehr geblockt.
     */
    object InstagramExplore {

        /**
         * Das Vorschlagsraster. Mehrere Kandidaten, weil Instagram den Namen über die Jahre
         * mehrfach geändert hat und je nach Fassung ein anderer im Baum steht.
         */
        val GRID_VIEW_IDS = listOf(
            "explore_grid",
            "explore_recycler_view",
            "explore_feed_recycler",
            "discover_grid",
            "discovery_recycler_view",
        )

        /**
         * Zeichen dafür, dass gerade gesucht wird. Dann liegt kein Raster mehr vorn und die
         * App hält still.
         *
         * Bewusst **nicht** in dieser Liste: die Suchleiste selbst. Die steht auch über dem
         * Raster, und als Suchbeleg gewertet würde sie jede Sperre aushebeln.
         */
        val SEARCH_MODE_VIEW_IDS = listOf(
            "search_results",
            "recent_searches",
            "search_recycler",
            "typeahead",
        )

        /** Dieselbe Aussage über Text, falls die View-ID nicht passt. */
        val SEARCH_MODE_LABELS = listOf(
            "recent",
            "zuletzt gesucht",
            "kürzlich gesucht",
            "letzte suchanfragen",
        )

        /**
         * Mindestanteil am Fenster, damit ein Knoten als Raster zählt.
         *
         * Dieselbe Überlegung wie beim Reels-Viewer: Ein eingebettetes Raster in einem
         * Profil ist klein, das Explore-Raster füllt den Bildschirm.
         */
        const val MIN_GRID_AREA_FRACTION = 0.35f
    }

    /**
     * Muster für TikToks obere Tab-Leiste.
     *
     * Anders als bei Instagram und YouTube gibt es hier NICHTS Stabiles an View-IDs: TikToks
     * Oberfläche ist verschleiert, die IDs sind generierte Kürzel, die sich je Version ändern.
     * Bleibt der sichtbare Text — der ist übersetzt, deshalb beide Sprachen.
     */
    /**
     * Muster für die Ausnahme „geteiltes Video einmal ansehen“.
     *
     * **Getrennt nach App, und das ist keine Ordnungsliebe.** Bei YouTube heisst Shorts intern
     * `reel`; bei Instagram sind `reel_*` die **Stories**. Eine gemeinsame Liste hiesse, dass
     * ein Story-Wisch bei Instagram als Reels-Wisch zählt — genau der Fehler, vor dem
     * CLAUDE.md seit v0.1 warnt.
     */
    object SharedClip {

        /**
         * Der Reels-Tab in Instagrams unterer Leiste. Ist er ausgewählt, wählt der Algorithmus
         * das Video — dann greift die Ausnahme „ein Video, kein Feed“ nicht.
         */
        val INSTAGRAM_TAB_VIEW_IDS = listOf(
            "clips_tab",
            "reels_tab",
        )

        /** Dieselbe Sache über die Beschriftung, falls die ID wieder einmal wechselt. */
        val INSTAGRAM_TAB_LABELS = setOf(
            "reels",
            "reel",
        )

        /**
         * Die senkrechte Seitenliste des Instagram-Viewers.
         *
         * Nur Scroll-Ereignisse aus ihr zählen als Wisch zum nächsten Video. Der
         * Kommentar-Bereich scrollt ebenfalls und darf ausdrücklich nicht auslösen.
         */
        val INSTAGRAM_PAGER_VIEW_IDS = listOf(
            "clips_viewer",
            "clips_view_pager",
        )

        /**
         * Der Shorts-Tab bei YouTube — **nur** über die Beschriftung.
         *
         * Hier wird bewusst keine View-ID geraten. Beschriftung plus `isSelected` ist dasselbe
         * UND-Gatter, das `yt_shorts_tab_selected` seit jeher trägt, und das hat sich als
         * fehlalarmfest erwiesen.
         */
        val YOUTUBE_TAB_VIEW_IDS = emptyList<String>()

        val YOUTUBE_TAB_LABELS = setOf(
            "shorts",
        )

        /** Die Seitenliste des Shorts-Players. */
        val YOUTUBE_PAGER_VIEW_IDS = listOf(
            "reel_recycler",
            "reel_player_page_container",
        )

        fun tabViewIds(packageName: String): List<String> = when (packageName) {
            Packages.YOUTUBE -> YOUTUBE_TAB_VIEW_IDS
            else -> INSTAGRAM_TAB_VIEW_IDS
        }

        fun tabLabels(packageName: String): Set<String> = when (packageName) {
            Packages.YOUTUBE -> YOUTUBE_TAB_LABELS
            else -> INSTAGRAM_TAB_LABELS
        }

        fun pagerViewIds(packageName: String): List<String> = when (packageName) {
            Packages.YOUTUBE -> YOUTUBE_PAGER_VIEW_IDS
            else -> INSTAGRAM_PAGER_VIEW_IDS
        }
    }

    object TikTokFeed {

        /** Der Tab des Algorithmus. Ist er aktiv, wird umgeschaltet. */
        val FOR_YOU_LABELS = listOf(
            "für dich",
            "for you",
            "fyp",
        )

        /** Der Zieltab. Ist er aktiv, ist nichts zu tun. */
        val FOLLOWING_LABELS = listOf(
            "folge ich",
            "following",
            "abos",
        )
    }
}
