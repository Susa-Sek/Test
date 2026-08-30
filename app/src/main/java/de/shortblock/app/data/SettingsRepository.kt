package de.shortblock.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.shortblock.app.service.Feature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "shortblock")

data class BlockSettings(
    val enabled: Set<Feature>,
    val diagnostics: Boolean,
    /** Tageskontingent je Feature in Minuten. 0 = kein Kontingent, also immer blocken. */
    val budgets: Map<Feature, Int> = emptyMap(),
    /** Freiwilliger Vordergrunddienst gegen Hersteller-Energieverwaltung. */
    val keepAlive: Boolean = false,
) {
    fun budgetMinutes(feature: Feature): Int = budgets[feature] ?: 0

    companion object {
        /**
         * Voreinstellung: alles an — außer „TikTok ganz blocken“.
         *
         * Das ist der einzige Schalter, der eine App vollständig unbenutzbar macht. So etwas
         * schaltet man selbst ein; ungefragt wäre es eine Zumutung.
         */
        val DEFAULT_ENABLED: Set<Feature> = Feature.entries.toSet() - Feature.TIKTOK_ALL

        fun isEnabledByDefault(feature: Feature): Boolean = feature in DEFAULT_ENABLED

        /**
         * Features, für die ein Zeitkontingent überhaupt sinnvoll ist: Kurzvideo-Sehdauer.
         * Der Feed-Filter und „TikTok ganz blocken“ sind Grundsatzentscheidungen, keine
         * Sehdauer — dort wäre ein Budget sinnlos.
         */
        val BUDGETABLE = listOf(
            Feature.INSTAGRAM_REELS,
            Feature.YOUTUBE_SHORTS,
            Feature.TIKTOK_FYP,
        )

        /**
         * Features ohne eigene Block-Regel, die stattdessen eine Policy durchsetzt.
         *
         * Diese Liste existiert, damit `EnforcementCoverageTest` prüfen kann, dass jedes Feature
         * genau einen Durchsetzungsweg hat. Ohne sie war `TIKTOK_FYP` budgetierbar, ohne dass
         * irgendwo ein Budget ausgewertet wurde — die Chips in der Oberfläche waren Attrappe.
         */
        val POLICY_ENFORCED = setOf(Feature.INSTAGRAM_FEED, Feature.TIKTOK_FYP)

        /** Auswahl in der Oberfläche; 0 steht für „Immer blocken“. */
        val BUDGET_CHOICES = listOf(0, 5, 10, 20, 30)

        val DEFAULT = BlockSettings(enabled = DEFAULT_ENABLED, diagnostics = false)
    }
}

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.appDataStore

    val settings: Flow<BlockSettings> = dataStore.data.map { prefs ->
        BlockSettings(
            enabled = Feature.entries.filterTo(mutableSetOf()) {
                prefs[key(it)] ?: BlockSettings.isEnabledByDefault(it)
            },
            diagnostics = prefs[DIAGNOSTICS] ?: false,
            budgets = BlockSettings.BUDGETABLE.associateWith { prefs[budgetKey(it)] ?: 0 },
            keepAlive = prefs[KEEP_ALIVE] ?: false,
        )
    }

    suspend fun setFeatureEnabled(feature: Feature, enabled: Boolean) {
        dataStore.edit { it[key(feature)] = enabled }
    }

    suspend fun setBudgetMinutes(feature: Feature, minutes: Int) {
        dataStore.edit { it[budgetKey(feature)] = minutes.coerceAtLeast(0) }
    }

    suspend fun setKeepAlive(enabled: Boolean) {
        dataStore.edit { it[KEEP_ALIVE] = enabled }
    }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { it[DIAGNOSTICS] = enabled }
    }

    private companion object {
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics")
        val KEEP_ALIVE = booleanPreferencesKey("keep_alive")
        fun budgetKey(feature: Feature) = intPreferencesKey("budget_${feature.name}")
        fun key(feature: Feature) = booleanPreferencesKey("feature_${feature.name}")
    }
}
