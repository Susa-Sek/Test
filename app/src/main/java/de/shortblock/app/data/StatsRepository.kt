package de.shortblock.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.shortblock.app.service.Feature
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Dünne DataStore-Hülle um [StatsHistory]. Die gesamte Logik — Tageswechsel, Beschneiden,
 * Kodierung — liegt dort und ist damit ohne Gerät testbar.
 */
class StatsRepository(context: Context) {

    private val dataStore = context.applicationContext.appDataStore

    private val history: Flow<Map<Long, Map<Feature, Int>>> =
        dataStore.data.map { prefs -> StatsHistory.decode(prefs[HISTORY]) }

    val today: Flow<Map<Feature, Int>> =
        history.map { StatsHistory.countsFor(it, epochDay()) }

    val week: Flow<List<DayStat>> =
        history.map { StatsHistory.lastDays(it, epochDay()) }

    suspend fun increment(feature: Feature) {
        dataStore.edit { prefs ->
            val current = StatsHistory.decode(prefs[HISTORY])
            prefs[HISTORY] = StatsHistory.encode(
                StatsHistory.increment(current, epochDay(), feature),
            )
        }
    }

    private fun epochDay(): Long = LocalDate.now().toEpochDay()

    private companion object {
        val HISTORY = stringPreferencesKey("stats_history")
    }
}
