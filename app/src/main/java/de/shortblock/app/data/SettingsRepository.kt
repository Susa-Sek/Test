package de.shortblock.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import de.shortblock.app.service.Feature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "shortblock")

data class BlockSettings(
    val enabled: Set<Feature>,
    val diagnostics: Boolean,
) {
    companion object {
        /** Alle drei Blocker sind ab Installation an — dafür installiert man die App. */
        val DEFAULT = BlockSettings(enabled = Feature.entries.toSet(), diagnostics = false)
    }
}

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.appDataStore

    val settings: Flow<BlockSettings> = dataStore.data.map { prefs ->
        BlockSettings(
            enabled = Feature.entries.filterTo(mutableSetOf()) { prefs[key(it)] ?: true },
            diagnostics = prefs[DIAGNOSTICS] ?: false,
        )
    }

    suspend fun setFeatureEnabled(feature: Feature, enabled: Boolean) {
        dataStore.edit { it[key(feature)] = enabled }
    }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { it[DIAGNOSTICS] = enabled }
    }

    private companion object {
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics")
        fun key(feature: Feature) = booleanPreferencesKey("feature_${feature.name}")
    }
}
