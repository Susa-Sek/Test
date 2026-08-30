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
     * TikTok läuft unter zwei Paketnamen: weltweit `musically`, in einzelnen Regionen `trill`.
     * Wer nur einen einträgt, hat auf der Hälfte der Geräte eine App, die scheinbar nichts tut.
     */
    const val TIKTOK_GLOBAL = "com.zhiliaoapp.musically"
    const val TIKTOK_REGIONAL = "com.ss.android.ugc.trill"

    val TIKTOK = setOf(TIKTOK_GLOBAL, TIKTOK_REGIONAL)

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
            requireSelected = true,
        ),

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

        /** Titeltext, wenn der gefilterte Feed bereits aktiv ist — dann ist nichts zu tun. */
        val FOLLOWING_TITLES = listOf(
            "following",
            "folge ich",
            "favorites",
            "favoriten",
        )

        /** Titeltext des algorithmischen Feeds. Dann wird umgeschaltet. */
        val ALGORITHMIC_TITLES = listOf(
            "instagram",
            "for you",
            "für dich",
        )

        /** Eintrag im aufklappenden Menü, der angetippt werden soll. */
        val MENU_FOLLOWING_ENTRIES = listOf(
            "following",
            "folge ich",
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
     * Muster für TikToks obere Tab-Leiste.
     *
     * Anders als bei Instagram und YouTube gibt es hier NICHTS Stabiles an View-IDs: TikToks
     * Oberfläche ist verschleiert, die IDs sind generierte Kürzel, die sich je Version ändern.
     * Bleibt der sichtbare Text — der ist übersetzt, deshalb beide Sprachen.
     */
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
