package de.klarzeit.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Schreibt die Tageshistorie fort.
 *
 * Der Schreibvorgang ist absichtlich stumpf: Bei jedem Durchlauf wird der heutige Stand
 * überschrieben und Altes weggeräumt. Das ist idempotent — ob die App einmal am Tag oder
 * alle fünfzehn Minuten nachsieht, ändert am Ergebnis nichts.
 */
class HistoryRepository(context: Context) {

    private val dataStore = context.applicationContext.klarzeitDataStore

    val history: Flow<Map<Long, DayHistory.DaySummary>> =
        dataStore.data.map { prefs -> DayHistory.decode(prefs[HISTORY]) }

    /** Die letzten sieben Tage, ältester zuerst — mit festen Plätzen auch für leere Tage. */
    val week: Flow<List<Pair<Long, DayHistory.DaySummary>>> =
        history.map { DayHistory.lastDays(it, today()) }

    suspend fun record(summary: DayHistory.DaySummary) {
        dataStore.edit { prefs ->
            val today = today()
            val updated = DayHistory.put(DayHistory.decode(prefs[HISTORY]), today, summary)
            prefs[HISTORY] = DayHistory.encode(DayHistory.prune(updated, today))
        }
    }

    private fun today(): Long = LocalDate.now().toEpochDay()

    private companion object {
        val HISTORY = stringPreferencesKey("day_history")
    }
}
