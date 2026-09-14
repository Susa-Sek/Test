package de.shortblock.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.shortblock.app.R
import de.shortblock.app.data.WatchdogState
import de.shortblock.app.system.SystemSettings
import java.util.concurrent.TimeUnit

/**
 * Der Wächter über die Bedienungshilfe.
 *
 * Er kann den Dienst **nicht** wieder einschalten — das darf keine App, und das ist richtig so.
 * Er kann nur verhindern, dass ein abgeschalteter Dienst tagelang unbemerkt bleibt: eine
 * Meldung, ein Tipp darauf, und man steht direkt in den Bedienungshilfen.
 *
 * WorkManager statt eigener Alarm: Die Planung überlebt einen Neustart, und genau nach einem
 * Neustart verschwindet die Bedienungshilfe auf Hersteller-ROMs am häufigsten.
 */
class ServiceWatchdogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val state = WatchdogState(applicationContext)

        if (SystemSettings.isServiceEnabled(applicationContext)) {
            state.onServiceRunning()
            return Result.success()
        }

        val warn = WatchdogPolicy.shouldWarn(
            everEnabled = state.everEnabled(),
            enabledNow = false,
            alreadyWarned = state.alreadyWarned(),
        )
        if (warn) {
            notifyServiceGone(applicationContext)
            state.onWarned()
        }
        return Result.success()
    }

    private fun notifyServiceGone(context: Context) {
        val granted = android.os.Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.watchdog_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )

        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(context.getString(R.string.watchdog_title))
            .setContentText(context.getString(R.string.watchdog_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.watchdog_text)),
            )
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "watchdog"
        private const val NOTIFICATION_ID = 4711
        private const val WORK_NAME = "service-watchdog"

        /**
         * Zwei Stunden: oft genug, dass ein nächtliches Abschalten am Vormittag auffällt, selten
         * genug, dass es im Akkuverbrauch nicht vorkommt. (Weniger als 15 Minuten lässt
         * WorkManager ohnehin nicht zu.)
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(2, TimeUnit.HOURS)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }
    }
}
