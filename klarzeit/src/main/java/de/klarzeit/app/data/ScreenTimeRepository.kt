package de.klarzeit.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Bringt Nutzungsdaten und Einstellungen zusammen — die eine Stelle, an der die Zahl
 * entsteht, die App und Widget gleichermassen zeigen.
 */
class ScreenTimeRepository(context: Context) {

    private val reader = UsageReader(context)
    private val settings = SettingsRepository(context)
    private val catalog = AppCatalog(context)
    private val historyRepository = HistoryRepository(context)

    data class Today(
        val summary: UsageSessions.Summary,
        val goalMillis: Long,
        val hasPermission: Boolean,
        /** Wie oft heute zum Telefon gegriffen wurde. */
        val unlocks: Int = 0,
    ) {
        /** Griffe über alle mitzählenden Apps. */
        val opens: Int get() = summary.apps.filter { it.counted }.sumOf { it.opens }

        val status: GoalState.Status get() = GoalState.status(summary.countedMillis, goalMillis)
    }

    private val _today = MutableStateFlow<Today?>(null)
    val today: StateFlow<Today?> = _today.asStateFlow()

    fun hasPermission(): Boolean = reader.hasPermission()

    suspend fun refresh(now: Long = System.currentTimeMillis()): Today {
        val result = load(now)
        _today.value = result
        return result
    }

    /** Die letzten sieben Tage, für die Balkenreihe. */
    val week = historyRepository.week

    /** Ohne Zustand, für das Widget und den Hintergrundlauf. */
    suspend fun load(now: Long = System.currentTimeMillis()): Today {
        if (!reader.hasPermission()) {
            return Today(
                summary = UsageSessions.Summary(0, 0, emptyList()),
                goalMillis = settings.goalMillis.first(),
                hasPermission = false,
            )
        }

        val usage = reader.today(now)
        val today = Today(
            summary = UsageSessions.summarize(
                perPackage = usage.foregroundMillis,
                excluded = settings.excluded.first(),
                ignored = DefaultExclusions.ALWAYS_IGNORED +
                    catalog.homeScreenPackages() +
                    ownPackage,
                opens = usage.opens,
            ),
            goalMillis = settings.goalMillis.first(),
            hasPermission = true,
            unlocks = usage.unlocks,
        )

        // Bei jedem Durchlauf fortschreiben — überschreibend, nie addierend.
        historyRepository.record(
            DayHistory.DaySummary(
                countedMillis = today.summary.countedMillis,
                totalMillis = today.summary.totalMillis,
                opens = today.opens,
                unlocks = today.unlocks,
            ),
        )

        return today
    }

    /**
     * Klarzeit zählt sich nicht selbst: Wer die App öffnet, um seine Bildschirmzeit zu prüfen,
     * soll sie dabei nicht erhöhen.
     */
    private val ownPackage = setOf(context.applicationContext.packageName)
}
