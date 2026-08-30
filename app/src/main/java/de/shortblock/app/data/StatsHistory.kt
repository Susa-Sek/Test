package de.shortblock.app.data

import de.shortblock.app.service.Feature
import org.json.JSONObject

/** Ein Tag im Wochenverlauf. */
data class DayStat(val epochDay: Long, val counts: Map<Feature, Int>) {
    val total: Int get() = counts.values.sum()
}

/**
 * Die Historie der Tageszähler — reine Funktionen auf Strings, kein Android.
 *
 * Hier stecken die Fehler, die man auf einem Gerät erst Tage später bemerkt: Tageswechsel,
 * Beschneiden, kaputtes JSON nach einem Absturz. Deshalb liegt das getrennt vom DataStore
 * und ist als JVM-Test prüfbar — dasselbe Muster wie `service/UiNode.kt`.
 */
object StatsHistory {

    /** So lange wird aufgehoben. Angezeigt werden sieben Tage; der Rest ist Puffer. */
    const val KEEP_DAYS = 14

    /**
     * Schätzung für die Zeitersparnis.
     *
     * Bewusst konservativ und in der Oberfläche offengelegt: Ein kurzes Video plus Wischen.
     * Eine Zahl ohne sichtbare Annahme ist eine Behauptung, keine Messung.
     */
    const val SECONDS_PER_BLOCK = 25

    fun decode(raw: String?): Map<Long, Map<Feature, Int>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<Long, Map<Feature, Int>>()
        for (dayKey in root.keys()) {
            val day = dayKey.toLongOrNull() ?: continue
            val dayObject = root.optJSONObject(dayKey) ?: continue
            val counts = mutableMapOf<Feature, Int>()
            for (featureKey in dayObject.keys()) {
                val feature = runCatching { Feature.valueOf(featureKey) }.getOrNull() ?: continue
                val value = dayObject.optInt(featureKey)
                if (value > 0) counts[feature] = value
            }
            if (counts.isNotEmpty()) result[day] = counts
        }
        return result
    }

    fun encode(history: Map<Long, Map<Feature, Int>>): String {
        val root = JSONObject()
        history.forEach { (day, counts) ->
            if (counts.isEmpty()) return@forEach
            val dayObject = JSONObject()
            counts.forEach { (feature, value) -> if (value > 0) dayObject.put(feature.name, value) }
            if (dayObject.length() > 0) root.put(day.toString(), dayObject)
        }
        return root.toString()
    }

    /** Wirft alles weg, was älter ist als [KEEP_DAYS] — und alles aus der Zukunft. */
    fun prune(
        history: Map<Long, Map<Feature, Int>>,
        today: Long,
        keepDays: Int = KEEP_DAYS,
    ): Map<Long, Map<Feature, Int>> =
        history.filterKeys { day -> day <= today && day > today - keepDays }

    fun increment(
        history: Map<Long, Map<Feature, Int>>,
        today: Long,
        feature: Feature,
    ): Map<Long, Map<Feature, Int>> {
        val dayCounts = history[today].orEmpty().toMutableMap()
        dayCounts[feature] = (dayCounts[feature] ?: 0) + 1
        return prune(history + (today to dayCounts), today)
    }

    fun countsFor(history: Map<Long, Map<Feature, Int>>, day: Long): Map<Feature, Int> =
        Feature.entries.associateWith { history[day]?.get(it) ?: 0 }

    /**
     * Die letzten [days] Tage, älteste zuerst.
     *
     * Tage ohne Einträge kommen als Null zurück statt zu fehlen — sonst hätte der Wochenbalken
     * mal fünf und mal sieben Balken, je nachdem wie fleißig geblockt wurde.
     */
    fun lastDays(
        history: Map<Long, Map<Feature, Int>>,
        today: Long,
        days: Int = 7,
    ): List<DayStat> = (days - 1 downTo 0).map { back ->
        val day = today - back
        DayStat(epochDay = day, counts = history[day].orEmpty())
    }

    /** Geschätzte gesparte Minuten, abgerundet. */
    fun savedMinutes(blocks: Int): Int = blocks * SECONDS_PER_BLOCK / 60
}
