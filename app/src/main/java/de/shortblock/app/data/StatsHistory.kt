package de.shortblock.app.data

import de.shortblock.app.service.Feature
import org.json.JSONObject

/** Was an einem Tag für ein Feature zusammenkam. */
data class FeatureStat(val count: Int = 0, val seconds: Int = 0)

/** Ein Tag im Wochenverlauf. */
data class DayStat(val epochDay: Long, val stats: Map<Feature, FeatureStat>) {
    val total: Int get() = stats.values.sumOf { it.count }
    val totalSeconds: Int get() = stats.values.sumOf { it.seconds }
}

/**
 * Die Historie der Tageswerte — reine Funktionen auf Strings, kein Android.
 *
 * Hier stecken die Fehler, die man auf einem Gerät erst Tage später bemerkt: Tageswechsel,
 * Beschneiden, kaputtes JSON nach einem Absturz, Formatwechsel beim Update. Deshalb liegt das
 * getrennt vom DataStore und ist als JVM-Test prüfbar — dasselbe Muster wie `service/UiNode.kt`.
 */
object StatsHistory {

    /** So lange wird aufgehoben. Angezeigt werden sieben Tage; der Rest ist Puffer. */
    const val KEEP_DAYS = 14

    /**
     * Schätzung für die Zeitersparnis in der Übersicht.
     * Bewusst konservativ und in der Oberfläche offengelegt: ein kurzes Video plus Wischen.
     */
    const val SECONDS_PER_BLOCK = 25

    private const val KEY_COUNT = "n"
    private const val KEY_SECONDS = "s"

    fun decode(raw: String?): Map<Long, Map<Feature, FeatureStat>> {
        if (raw.isNullOrBlank()) return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = mutableMapOf<Long, Map<Feature, FeatureStat>>()
        for (dayKey in root.keys()) {
            val day = dayKey.toLongOrNull() ?: continue
            val dayObject = root.optJSONObject(dayKey) ?: continue
            val stats = mutableMapOf<Feature, FeatureStat>()
            for (featureKey in dayObject.keys()) {
                val feature = runCatching { Feature.valueOf(featureKey) }.getOrNull() ?: continue
                val stat = readStat(dayObject, featureKey)
                if (stat.count > 0 || stat.seconds > 0) stats[feature] = stat
            }
            if (stats.isNotEmpty()) result[day] = stats
        }
        return result
    }

    /**
     * Liest beide Formate.
     *
     * Bis v0.3 stand hier nur eine Zahl (der Zähler). Wer aktualisiert, soll seine Woche nicht
     * verlieren — eine blanke Zahl wird deshalb als Zähler ohne Sehdauer gelesen.
     */
    private fun readStat(dayObject: JSONObject, key: String): FeatureStat {
        dayObject.optJSONObject(key)?.let { nested ->
            return FeatureStat(
                count = nested.optInt(KEY_COUNT),
                seconds = nested.optInt(KEY_SECONDS),
            )
        }
        return FeatureStat(count = dayObject.optInt(key), seconds = 0)
    }

    fun encode(history: Map<Long, Map<Feature, FeatureStat>>): String {
        val root = JSONObject()
        history.forEach { (day, stats) ->
            val dayObject = JSONObject()
            stats.forEach { (feature, stat) ->
                if (stat.count <= 0 && stat.seconds <= 0) return@forEach
                dayObject.put(
                    feature.name,
                    JSONObject().put(KEY_COUNT, stat.count).put(KEY_SECONDS, stat.seconds),
                )
            }
            if (dayObject.length() > 0) root.put(day.toString(), dayObject)
        }
        return root.toString()
    }

    /** Wirft alles weg, was älter ist als [KEEP_DAYS] — und alles aus der Zukunft. */
    fun prune(
        history: Map<Long, Map<Feature, FeatureStat>>,
        today: Long,
        keepDays: Int = KEEP_DAYS,
    ): Map<Long, Map<Feature, FeatureStat>> =
        history.filterKeys { day -> day <= today && day > today - keepDays }

    fun incrementCount(
        history: Map<Long, Map<Feature, FeatureStat>>,
        today: Long,
        feature: Feature,
    ): Map<Long, Map<Feature, FeatureStat>> = update(history, today, feature) { stat ->
        stat.copy(count = stat.count + 1)
    }

    fun addSeconds(
        history: Map<Long, Map<Feature, FeatureStat>>,
        today: Long,
        feature: Feature,
        seconds: Int,
    ): Map<Long, Map<Feature, FeatureStat>> {
        if (seconds <= 0) return history
        return update(history, today, feature) { stat -> stat.copy(seconds = stat.seconds + seconds) }
    }

    private fun update(
        history: Map<Long, Map<Feature, FeatureStat>>,
        today: Long,
        feature: Feature,
        change: (FeatureStat) -> FeatureStat,
    ): Map<Long, Map<Feature, FeatureStat>> {
        val dayStats = history[today].orEmpty().toMutableMap()
        dayStats[feature] = change(dayStats[feature] ?: FeatureStat())
        return prune(history + (today to dayStats), today)
    }

    fun countsFor(history: Map<Long, Map<Feature, FeatureStat>>, day: Long): Map<Feature, Int> =
        Feature.entries.associateWith { history[day]?.get(it)?.count ?: 0 }

    fun secondsFor(history: Map<Long, Map<Feature, FeatureStat>>, day: Long): Map<Feature, Int> =
        Feature.entries.associateWith { history[day]?.get(it)?.seconds ?: 0 }

    /**
     * Die letzten [days] Tage, älteste zuerst.
     *
     * Tage ohne Einträge kommen als Null zurück statt zu fehlen — sonst hätte der Wochenbalken
     * mal fünf und mal sieben Balken, je nachdem wie fleißig geblockt wurde.
     */
    fun lastDays(
        history: Map<Long, Map<Feature, FeatureStat>>,
        today: Long,
        days: Int = 7,
    ): List<DayStat> = (days - 1 downTo 0).map { back ->
        val day = today - back
        DayStat(epochDay = day, stats = history[day].orEmpty())
    }

    /** Geschätzte gesparte Minuten, abgerundet. */
    fun savedMinutes(blocks: Int): Int = blocks * SECONDS_PER_BLOCK / 60
}
