package de.klarzeit.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.klarzeit.app.data.GoalState
import de.klarzeit.app.data.ScreenTimeRepository
import de.klarzeit.app.data.SettingsRepository
import de.klarzeit.app.data.UsageReader
import java.util.concurrent.TimeUnit

/**
 * Hält das Widget aktuell und prüft dabei das Tagesziel.
 *
 * Ein Viertelstundentakt ist das Engste, was WorkManager zulässt — und mehr braucht eine
 * Bildschirmzeit auch nicht. Der eigene Takt ist nötig, weil `updatePeriodMillis` eines
 * Widgets frühestens alle 30 Minuten feuert; auf einem halbstündlich springenden Wert
 * würde niemand vertrauen.
 */
class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val today = runCatching { ScreenTimeRepository(context).load() }.getOrNull()
            ?: return Result.success()

        KlarzeitWidget.refresh(context)

        if (today.hasPermission) {
            val settings = SettingsRepository(context)
            val day = UsageReader.dayKey(System.currentTimeMillis())
            val shouldNotify = GoalState.shouldNotify(
                countedMillis = today.summary.countedMillis,
                goalMillis = today.goalMillis,
                today = day,
                lastNotifiedDay = settings.lastNotifiedDay(),
            )
            if (shouldNotify) {
                GoalNotifier.notifyGoalReached(context, today.summary.countedMillis, today.goalMillis)
                settings.setLastNotifiedDay(day)
            }
        }

        return Result.success()
    }

    companion object {
        private const val NAME = "klarzeit-widget"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                // KEEP: ein laufender Takt soll bei jedem App-Start nicht neu anfangen.
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
