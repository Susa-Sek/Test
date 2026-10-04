package de.klarzeit.app.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.klarzeit.app.R
import de.klarzeit.app.data.TimeFormat

/**
 * Die eine Meldung am Tag, an dem das Ziel fällt.
 *
 * Bewusst ohne Ton, ohne Wiederholung und ohne Aufforderung. Eine App, die im Minutentakt
 * meldet, dass man immer noch über dem Ziel liegt, wird nach zwei Tagen stummgeschaltet —
 * und ist damit wirkungslos.
 */
object GoalNotifier {

    private const val CHANNEL_ID = "goal"
    private const val NOTIFICATION_ID = 1

    fun notifyGoalReached(context: Context, countedMillis: Long, goalMillis: Long) {
        // Die Pruefung steht absichtlich hier und nicht in einer Hilfsfunktion: Lint folgt
        // dem Aufruf sonst nicht und haelt das notify() unten fuer ungeprueft.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.goal_notification_title))
            .setContentText(
                context.getString(
                    R.string.goal_notification_body,
                    TimeFormat.short(countedMillis),
                    TimeFormat.short(goalMillis),
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.goal_channel_name),
            // LOW: erscheint in der Leiste, schiebt sich aber nicht ueber den Bildschirm.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.goal_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
