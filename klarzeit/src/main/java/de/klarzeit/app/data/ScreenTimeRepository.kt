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

    data class Today(
        val summary: UsageSessions.Summary,
        val goalMillis: Long,
        val hasPermission: Boolean,
    ) {
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

    /** Ohne Zustand, für das Widget und den Hintergrundlauf. */
    suspend fun load(now: Long = System.currentTimeMillis()): Today {
        if (!reader.hasPermission()) {
            return Today(
                summary = UsageSessions.Summary(0, 0, emptyList()),
                goalMillis = settings.goalMillis.first(),
                hasPermission = false,
            )
        }

        return Today(
            summary = UsageSessions.summarize(
                perPackage = reader.today(now),
                excluded = settings.excluded.first(),
                ignored = DefaultExclusions.ALWAYS_IGNORED + catalog.homeScreenPackages(),
            ),
            goalMillis = settings.goalMillis.first(),
            hasPermission = true,
        )
    }
}
