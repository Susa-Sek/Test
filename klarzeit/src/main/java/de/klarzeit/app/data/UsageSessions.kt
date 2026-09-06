package de.klarzeit.app.data

/**
 * Rechnet Androids Nutzungs-Ereignisse in Vordergrundzeit je App um — das Herz der App,
 * ohne Android und damit prüfbar.
 *
 * Warum überhaupt selbst rechnen: `UsageStatsManager` bietet mit `queryUsageStats` eine
 * fertige Summe an, die aber gerundet, je nach Hersteller verschieden und am laufenden Tag
 * unzuverlässig ist. Aus den rohen Ereignissen wird die Zeit exakt so, wie der Bildschirm
 * sie gezeigt hat.
 *
 * Die Fälle, an denen eine naive Schleife scheitert und die hier alle abgefangen sind:
 *
 * - **Kein Ende.** Die App, die gerade offen ist, hat noch kein Hintergrund-Ereignis. Ohne
 *   Abschluss am Fensterende fehlte die aktuelle Sitzung — also genau die, die man sehen will.
 * - **Kein Anfang.** Wer um Mitternacht in einer App steht, hat sein Vordergrund-Ereignis
 *   gestern. Für heute zählt die Zeit ab Fensterbeginn.
 * - **Bildschirm aus.** Ohne dieses Ereignis läuft die zuletzt offene App die ganze Nacht
 *   weiter und meldet morgens acht Stunden Instagram.
 * - **App wechselt ohne Pause.** Android schickt nicht immer ein Hintergrund-Ereignis, bevor
 *   die nächste App in den Vordergrund kommt. Ein neuer Vordergrund schliesst deshalb den
 *   laufenden Abschnitt.
 */
object UsageSessions {

    enum class Type {
        /** App kommt in den Vordergrund (`ACTIVITY_RESUMED`). */
        FOREGROUND,

        /** App verlässt den Vordergrund (`ACTIVITY_PAUSED`, `ACTIVITY_STOPPED`). */
        BACKGROUND,

        /** Bildschirm aus, Sperre an oder Gerät aus — nichts läuft mehr im Vordergrund. */
        SCREEN_OFF,
    }

    data class Event(
        val packageName: String,
        val type: Type,
        val timestampMillis: Long,
    )

    /**
     * @param windowStart Beginn des Zeitraums, üblicherweise Mitternacht.
     * @param windowEnd Ende, üblicherweise jetzt. Alle Abschnitte werden hierauf beschnitten.
     * @return Vordergrund-Millisekunden je Paket; Pakete ohne Zeit fehlen.
     */
    fun foregroundMillis(
        events: List<Event>,
        windowStart: Long,
        windowEnd: Long,
    ): Map<String, Long> {
        if (windowEnd <= windowStart) return emptyMap()

        val totals = HashMap<String, Long>()
        var openPackage: String? = null
        var openSince = 0L

        fun close(at: Long) {
            val pkg = openPackage ?: return
            val from = openSince.coerceIn(windowStart, windowEnd)
            val to = at.coerceIn(windowStart, windowEnd)
            if (to > from) totals[pkg] = (totals[pkg] ?: 0L) + (to - from)
            openPackage = null
        }

        // Die Reihenfolge ist das ganze Verfahren: Ereignisse aus queryEvents kommen zwar
        // sortiert, aber darauf zu bauen hiesse, sich auf jeden Hersteller zu verlassen.
        for (event in events.sortedBy { it.timestampMillis }) {
            if (event.timestampMillis > windowEnd) break

            when (event.type) {
                Type.FOREGROUND -> {
                    close(event.timestampMillis)
                    openPackage = event.packageName
                    openSince = event.timestampMillis
                }

                Type.BACKGROUND -> {
                    // Nur die App schliessen, die auch offen ist: Android meldet
                    // Hintergrund-Ereignisse auch für Apps, die längst weg sind.
                    if (openPackage == event.packageName) close(event.timestampMillis)
                }

                Type.SCREEN_OFF -> close(event.timestampMillis)
            }
        }

        // Was am Ende noch offen ist, läuft bis zum Fensterende weiter.
        close(windowEnd)

        return totals
    }

    data class AppTime(
        val packageName: String,
        val millis: Long,
        /** Zählt diese App in die Netto-Summe? */
        val counted: Boolean,
    )

    data class Summary(
        /** Alles zusammen, so wie Digital Wellbeing es zeigen würde. */
        val totalMillis: Long,
        /** Was nach Abzug der ausgeschlossenen Apps übrig bleibt — die Zahl der App. */
        val countedMillis: Long,
        /** Alle Apps, absteigend nach Zeit, ausgeschlossene eingeschlossen. */
        val apps: List<AppTime>,
    ) {
        val excludedMillis: Long get() = totalMillis - countedMillis

        /** Die grössten Zeitfresser, die auch zählen — das, was aufs Widget kommt. */
        fun topCounted(limit: Int): List<AppTime> =
            apps.asSequence().filter { it.counted }.take(limit).toList()
    }

    /**
     * @param excluded Pakete, die der Nutzer von der Summe ausgenommen hat.
     * @param ignored Pakete, die gar nicht erst auftauchen — Systemoberfläche und Ähnliches.
     *   Die tauchen in keiner Liste auf, weil sie keine Entscheidung sind, die jemand trifft.
     */
    fun summarize(
        perPackage: Map<String, Long>,
        excluded: Set<String>,
        ignored: Set<String> = emptySet(),
    ): Summary {
        val apps = perPackage.asSequence()
            .filter { (pkg, millis) -> millis > 0 && pkg !in ignored }
            .map { (pkg, millis) -> AppTime(pkg, millis, counted = pkg !in excluded) }
            // Bei gleicher Zeit nach Paketnamen, damit die Reihenfolge nicht springt.
            .sortedWith(compareByDescending<AppTime> { it.millis }.thenBy { it.packageName })
            .toList()

        return Summary(
            totalMillis = apps.sumOf { it.millis },
            countedMillis = apps.filter { it.counted }.sumOf { it.millis },
            apps = apps,
        )
    }
}
