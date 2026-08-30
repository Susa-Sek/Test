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
}

object Packages {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"

    val WATCHED = setOf(INSTAGRAM, YOUTUBE)
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
) {
    fun matches(node: UiNode, windowArea: Long): Boolean {
        if (!node.isVisible) return false
        if (requireSelected && !node.isSelected) return false
        if (!hasRequiredSize(node, windowArea)) return false

        val viewId = normalizeForMatch(node.viewId)
        if (viewId != null && viewIdContains.any { viewId.contains(it) }) return true

        val description = normalizeForMatch(node.contentDescription)
        if (description != null && contentDescriptionEquals.any { description == it }) return true

        val text = normalizeForMatch(node.text)
        if (text != null && textContains.any { text.contains(it) }) return true

        return false
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
}
