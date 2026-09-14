package de.klarzeit.app.data

import org.json.JSONObject

/**
 * Die Tageshistorie — was an den letzten Tagen zusammenkam.
 *
 * Bewusst **eigene** Speicherung, statt bei jedem Blick aus den Nutzungsdaten zu rechnen:
 * Android hebt die rohen Ereignisse nur wenige Tage auf. Wer die Historie daraus ableitet,
 * verliert sie rückwirkend — und eine Wochenansicht, die je nach Laune des Systems Lücken
 * hat, ist schlimmer als keine.
 *
 * Aufbau übernommen von ShortBlocks `data/StatsHistory.kt`, das dasselbe Problem schon
 * gelöst hat: reine Funktionen über `Map<epochDay, …>`, als JSON in DataStore.
 */
object DayHistory {

    /** Zwei Wochen. Genug für „war letzte Woche besser?", wenig genug für DataStore. */
    const val KEEP_DAYS = 14

    data class DaySummary(
        val countedMillis: Long = 0L,
        val totalMillis: Long = 0L,
        val opens: Int = 0,
        val unlocks: Int = 0,
    )

    private const val KEY_COUNTED = "c"
    private const val KEY_TOTAL = "t"
    private const val KEY_OPENS = "o"
    private const val KEY_UNLOCKS = "u"

    /** Kaputtes oder fehlendes JSON ergibt eine leere Historie, nie einen Absturz. */
    fun decode(raw: String?): Map<Long, DaySummary> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()

        return root.keys().asSequence().mapNotNull { key ->
            val day = key.toLongOrNull() ?: return@mapNotNull null
            val entry = root.optJSONObject(key) ?: return@mapNotNull null
            day to DaySummary(
                countedMillis = entry.optLong(KEY_COUNTED),
                totalMillis = entry.optLong(KEY_TOTAL),
                opens = entry.optInt(KEY_OPENS),
                unlocks = entry.optInt(KEY_UNLOCKS),
            )
        }.toMap()
    }

    fun encode(history: Map<Long, DaySummary>): String {
        val root = JSONObject()
        history.forEach { (day, summary) ->
            root.put(
                day.toString(),
                JSONObject()
                    .put(KEY_COUNTED, summary.countedMillis)
                    .put(KEY_TOTAL, summary.totalMillis)
                    .put(KEY_OPENS, summary.opens)
                    .put(KEY_UNLOCKS, summary.unlocks),
            )
        }
        return root.toString()
    }

    /**
     * Den heutigen Stand fortschreiben.
     *
     * **Überschreiben, nicht addieren.** Der Wert ist jedes Mal die Tagessumme von Mitternacht
     * bis jetzt; addieren würde ihn bei jedem Durchlauf vervielfachen.
     */
    fun put(
        history: Map<Long, DaySummary>,
        day: Long,
        summary: DaySummary,
    ): Map<Long, DaySummary> = history + (day to summary)

    fun prune(history: Map<Long, DaySummary>, today: Long, keepDays: Int = KEEP_DAYS): Map<Long, DaySummary> =
        history.filterKeys { it > today - keepDays && it <= today }

    /**
     * Die letzten [days] Tage, ältester zuerst — auch die ohne Eintrag.
     *
     * Lücken als leere Tage zu liefern statt sie wegzulassen, ist der Unterschied zwischen
     * einer Balkenreihe mit sieben festen Plätzen und einer, die je nach Datenlage springt.
     */
    fun lastDays(
        history: Map<Long, DaySummary>,
        today: Long,
        days: Int = 7,
    ): List<Pair<Long, DaySummary>> =
        ((today - days + 1)..today).map { day -> day to (history[day] ?: DaySummary()) }
}
