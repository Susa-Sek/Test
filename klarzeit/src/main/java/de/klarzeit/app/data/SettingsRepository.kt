package de.klarzeit.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.klarzeitDataStore: DataStore<Preferences> by preferencesDataStore(name = "klarzeit")

/** Ausschlussliste, Tagesziel und der Merker für die einmalige Zielmeldung. */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.klarzeitDataStore

    /**
     * Beim allerersten Start gilt der Vorschlag aus [DefaultExclusions]; danach zählt, was
     * der Nutzer gewählt hat — auch die leere Menge. Ohne den Merker [SEEDED] käme der
     * Vorschlag jedes Mal zurück, sobald jemand alle Haken entfernt.
     */
    val excluded: Flow<Set<String>> = dataStore.data.map { prefs ->
        if (prefs[SEEDED] == true) {
            prefs[EXCLUDED].orEmpty()
        } else {
            DefaultExclusions.DEFAULT_EXCLUDED
        }
    }

    val goalMillis: Flow<Long> = dataStore.data.map { prefs -> prefs[GOAL] ?: DEFAULT_GOAL_MILLIS }

    suspend fun setExcluded(packages: Set<String>) {
        dataStore.edit { prefs ->
            prefs[EXCLUDED] = packages
            prefs[SEEDED] = true
        }
    }

    suspend fun toggleExcluded(packageName: String) {
        val current = excluded.first()
        setExcluded(if (packageName in current) current - packageName else current + packageName)
    }

    suspend fun setGoalMillis(millis: Long) {
        dataStore.edit { it[GOAL] = millis.coerceAtLeast(0L) }
    }

    suspend fun lastNotifiedDay(): String? = dataStore.data.first()[LAST_NOTIFIED]

    suspend fun setLastNotifiedDay(day: String) {
        dataStore.edit { it[LAST_NOTIFIED] = day }
    }

    companion object {
        /** Zwei Stunden als Startwert — ein Ziel, das man senken kann, statt bei null zu stehen. */
        const val DEFAULT_GOAL_MILLIS = 2 * 60 * 60 * 1000L

        private val EXCLUDED = stringSetPreferencesKey("excluded")
        private val SEEDED = booleanPreferencesKey("excluded_seeded")
        private val GOAL = longPreferencesKey("goal_millis")
        private val LAST_NOTIFIED = stringPreferencesKey("last_notified_day")
    }
}
