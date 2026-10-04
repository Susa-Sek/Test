package de.shortblock.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.shortblock.app.MainActivity
import de.shortblock.app.R

/**
 * Freiwilliger Vordergrunddienst, der nur einen Zweck hat: den Prozess am Leben halten.
 *
 * Bedienungshilfe und dieser Dienst teilen sich den Prozess. Räumt die Energieverwaltung des
 * Herstellers den Prozess ab, stirbt die Bedienungshilfe mit — genau das Verhalten, bei dem
 * ShortBlock nach ein paar Stunden verstummt und erst Aus/Ein wieder hilft. Ein Vordergrunddienst
 * ist für Android ein starkes Signal, den Prozess zu verschonen.
 *
 * Der Preis ist eine dauerhaft sichtbare Benachrichtigung — deshalb ist das ein Schalter und
 * keine Voreinstellung. Die Benachrichtigung läuft auf der niedrigsten Stufe, ohne Ton.
 */
class KeepAliveService : android.app.Service() {

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // START_STICKY: Wird der Dienst trotzdem beendet, soll Android ihn wieder starten.
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager?.getNotificationChannel(CHANNEL_ID) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.keep_alive_channel),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply { setShowBadge(false) },
            )
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.keep_alive_title))
            .setContentText(getString(R.string.keep_alive_text))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "keep_alive"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, KeepAliveService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, KeepAliveService::class.java)) }
        }

        /** Ab Android 13 darf die Benachrichtigung ohne Erlaubnis nicht erscheinen. */
        fun needsNotificationPermission(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
}
