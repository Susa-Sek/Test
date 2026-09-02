package de.shortblock.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
    /** Darf der Tages-Cheat überhaupt eingelöst werden? */
    val cheatEnabled: Boolean = true,
    /** Ein bewusst angetipptes Reel oder Short einmal ansehen dürfen — Weiterwischen blockt. */
    val allowSingleClip: Boolean = true,
    /** Tag (epochDay), an dem der Cheat zuletzt eingelöst wurde. 0 = noch nie. */
    val cheatUsedOnDay: Int = 0,
    /**
     * Wann der Cheat angefordert wurde, in Millisekunden seit Epoche. 0 = nie.
     *
     * Der einzige gespeicherte Wert des Cheats: Wartezeit, Laufzeit und Ende rechnet
     * [CheatPass] daraus aus. So überlebt eine laufende Wartezeit jedes Abräumen des Dienstes.
     */
    val cheatArmedAtMillis: Long = 0L,
) {
    fun budgetMinutes(feature: Feature): Int = budgets[feature] ?: 0

    /**
     * Wie die „Für dich“-Zeile zum Ganz-Block steht.
     *
     * Steht hier statt in der Oberfläche, weil die drei Fälle seit dem Kontingent auf dem
     * Ganz-Block nicht mehr offensichtlich sind — und weil sich eine reine Funktion prüfen
     * lässt, eine Composable nicht.
     */
    fun tiktokFypRelation(): FypRelation = when {
        Feature.TIKTOK_ALL !in enabled -> FypRelation.INDEPENDENT
        budgetMinutes(Feature.TIKTOK_ALL) > 0 -> FypRelation.DURING_BUDGET
        else -> FypRelation.OVERRIDDEN
    }

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
         * Features, für die ein Zeitkontingent sinnvoll ist.
         *
         * `TIKTOK_ALL` stand hier bis v0.4.1 bewusst nicht drin — ein Kontingent auf „App ganz
         * sperren“ galt als Widerspruch. Das war zu eng gedacht: Es heißt schlicht *TikTok ist
         * zu, außer X Minuten am Tag*, und ist damit dieselbe Halbierung wie bei Reels und
         * Shorts, nur eine Ebene höher. Ohne sie hat die TikTok-Gruppe je nach Schalterstellung
         * gar keine Zeitgrenze.
         *
         * Der Feed-Filter `INSTAGRAM_FEED` bleibt draußen: Das ist eine Grundsatzentscheidung
         * über die Auswahl der Beiträge, keine Sehdauer.
         */
        val BUDGETABLE = listOf(
            Feature.INSTAGRAM_REELS,
            Feature.YOUTUBE_SHORTS,
            Feature.TIKTOK_FYP,
            Feature.TIKTOK_ALL,
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

/** Verhältnis der „Für dich“-Zeile zum Schalter „TikTok ganz blocken“. */
enum class FypRelation {
    /** Ganz-Block aus — die Zeile wirkt für sich. */
    INDEPENDENT,

    /** Ganz-Block an, aber mit Kontingent: Die Zeile greift innerhalb der erlaubten Minuten. */
    DURING_BUDGET,

    /** Ganz-Block ohne Kontingent — TikTok geht gar nicht erst auf. */
    OVERRIDDEN,
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
            cheatEnabled = prefs[CHEAT_ENABLED] ?: true,
            allowSingleClip = prefs[ALLOW_SINGLE_CLIP] ?: true,
            cheatUsedOnDay = prefs[CHEAT_USED_ON_DAY] ?: 0,
            cheatArmedAtMillis = prefs[CHEAT_ARMED_AT] ?: 0L,
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

    suspend fun setCheatEnabled(enabled: Boolean) {
        dataStore.edit { it[CHEAT_ENABLED] = enabled }
    }

    suspend fun setAllowSingleClip(enabled: Boolean) {
        dataStore.edit { it[ALLOW_SINGLE_CLIP] = enabled }
    }

    /**
     * Cheat anfordern — Tag und Zeitstempel in einem Schreibvorgang.
     *
     * Zusammen, damit nicht ein abgebrochener Schreibvorgang einen Cheat hinterlässt, der nie
     * als „heute verbraucht“ gilt. Ab hier läuft erst die Wartezeit, nicht der Cheat.
     */
    suspend fun armCheat(nowMillis: Long, today: Int) {
        dataStore.edit {
            it[CHEAT_USED_ON_DAY] = today
            it[CHEAT_ARMED_AT] = nowMillis
        }
    }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { it[DIAGNOSTICS] = enabled }
    }

    private companion object {
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics")
        val KEEP_ALIVE = booleanPreferencesKey("keep_alive")
        val CHEAT_ENABLED = booleanPreferencesKey("cheat_enabled")
        // Der Schlüssel behält seinen alten Namen, damit die Einstellung das Update
        // übersteht — umbenannt wurde nur, was die Sache heute bedeutet.
        val ALLOW_SINGLE_CLIP = booleanPreferencesKey("allow_shared_clips")
        val CHEAT_USED_ON_DAY = intPreferencesKey("cheat_used_on_day")
        // Bewusst ein neuer Schlüssel: Der alte hielt das Ende, dieser den Beginn. Ein beim
        // Update laufender Cheat geht verloren — fünf Minuten, einmalig, keine Migration wert.
        val CHEAT_ARMED_AT = longPreferencesKey("cheat_armed_at")
        fun budgetKey(feature: Feature) = intPreferencesKey("budget_${feature.name}")
        fun key(feature: Feature) = booleanPreferencesKey("feature_${feature.name}")
    }
}
