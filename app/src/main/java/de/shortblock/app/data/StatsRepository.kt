package de.shortblock.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import de.shortblock.app.service.Feature
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Tageszähler je Feature. Kein Verlauf, keine Historie — nur „heute so oft geblockt“.
 *
 * Der Tageswechsel wird lazy behandelt: Zähler werden nicht um Mitternacht zurückgesetzt,
 * sondern beim nächsten Lesen oder Schreiben verworfen, wenn der gespeicherte Tag nicht mehr
 * der heutige ist. Das spart einen Alarm und ist für eine Anzeige völlig ausreichend.
 */
class StatsRepository(context: Context) {

    private val dataStore = context.applicationContext.appDataStore

    val today: Flow<Map<Feature, Int>> = dataStore.data.map { prefs ->
        val storedDay = prefs[DAY] ?: 0L
        if (storedDay != epochDay()) {
            Feature.entries.associateWith { 0 }
        } else {
            Feature.entries.associateWith { prefs[key(it)] ?: 0 }
        }
    }

    suspend fun increment(feature: Feature) {
        dataStore.edit { prefs ->
            val current = epochDay()
            if ((prefs[DAY] ?: 0L) != current) {
                Feature.entries.forEach { prefs[key(it)] = 0 }
                prefs[DAY] = current
            }
            prefs[key(feature)] = (prefs[key(feature)] ?: 0) + 1
        }
    }

    private fun epochDay(): Long = LocalDate.now().toEpochDay()

    private companion object {
        val DAY = longPreferencesKey("stats_day")
        fun key(feature: Feature) = intPreferencesKey("count_${feature.name}")
    }
}
