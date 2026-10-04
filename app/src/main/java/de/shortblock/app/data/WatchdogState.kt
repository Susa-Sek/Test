package de.shortblock.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first

/**
 * Der kleine Merkzettel des Wächters.
 *
 * Bewusst getrennt von [BlockSettings]: Das hier sind keine Einstellungen, die jemand trifft,
 * sondern Zustand, den nur der Wächter liest und schreibt.
 */
class WatchdogState(context: Context) {

    private val dataStore = context.applicationContext.appDataStore

    /** Lief der Dienst auf diesem Gerät schon einmal? */
    suspend fun everEnabled(): Boolean = dataStore.data.first()[EVER_ENABLED] ?: false

    /** Wurde für das aktuelle Aussetzen schon gewarnt? */
    suspend fun alreadyWarned(): Boolean = dataStore.data.first()[WARNED] ?: false

    /** Der Dienst läuft: merken und die Warnsperre für das nächste Mal zurücksetzen. */
    suspend fun onServiceRunning() {
        dataStore.edit {
            it[EVER_ENABLED] = true
            it[WARNED] = false
        }
    }

    suspend fun onWarned() {
        dataStore.edit { it[WARNED] = true }
    }

    private companion object {
        val EVER_ENABLED = booleanPreferencesKey("watchdog_ever_enabled")
        val WARNED = booleanPreferencesKey("watchdog_warned")
    }
}
