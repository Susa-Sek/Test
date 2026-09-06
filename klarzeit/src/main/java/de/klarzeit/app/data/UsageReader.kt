package de.klarzeit.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holt die rohen Nutzungs-Ereignisse bei Android ab. Dünne Hülle — gerechnet wird in
 * [UsageSessions].
 */
class UsageReader(context: Context) {

    private val appContext = context.applicationContext

    private val usageStats: UsageStatsManager?
        get() = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Ob der Nutzer den Zugriff erteilt hat.
     *
     * `PACKAGE_USAGE_STATS` ist keine gewöhnliche Berechtigung, deshalb sagt
     * `checkSelfPermission` hier nichts aus — gefragt wird der AppOps-Dienst.
     */
    fun hasPermission(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        // Der Aufruf wurde in Android 10 umbenannt. Unter minSdk 26 laeuft die App auch
        // auf Android 8 und 9, dort heisst er noch checkOpNoThrow — ohne diese Weiche
        // stuerzt sie dort beim ersten Start ab.
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Vordergrundzeit je Paket seit Mitternacht. */
    suspend fun today(now: Long = System.currentTimeMillis()): Map<String, Long> =
        withContext(Dispatchers.IO) {
            val start = startOfDay(now)
            UsageSessions.foregroundMillis(readEvents(start, now), start, now)
        }

    private fun readEvents(from: Long, to: Long): List<UsageSessions.Event> {
        val manager = usageStats ?: return emptyList()
        val stream = runCatching { manager.queryEvents(from, to) }.getOrNull() ?: return emptyList()

        val events = mutableListOf<UsageSessions.Event>()
        val event = UsageEvents.Event()
        while (stream.hasNextEvent()) {
            stream.getNextEvent(event)
            val type = typeOf(event.eventType) ?: continue
            events += UsageSessions.Event(
                packageName = event.packageName.orEmpty(),
                type = type,
                timestampMillis = event.timeStamp,
            )
        }
        return events.filter { it.packageName.isNotEmpty() }
    }

    /**
     * Nur diese Ereignisse sagen etwas über Bildschirmzeit aus; alles andere — Meldungen,
     * Konfigurationswechsel, Sichtbarkeit von Fenstern — würde die Rechnung nur verrauschen.
     */
    private fun typeOf(eventType: Int): UsageSessions.Type? = when (eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> UsageSessions.Type.FOREGROUND
        UsageEvents.Event.ACTIVITY_PAUSED,
        UsageEvents.Event.ACTIVITY_STOPPED,
        -> UsageSessions.Type.BACKGROUND

        // Bildschirm aus oder Sperre an: Was gerade offen war, läuft nicht weiter.
        UsageEvents.Event.SCREEN_NON_INTERACTIVE,
        UsageEvents.Event.KEYGUARD_SHOWN,
        UsageEvents.Event.DEVICE_SHUTDOWN,
        -> UsageSessions.Type.SCREEN_OFF

        else -> null
    }

    companion object {
        /** Mitternacht des Tages, in dem [now] liegt — in der Zeitzone des Geräts. */
        fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /** Tagesschlüssel für die "einmal pro Tag"-Regel der Zielmeldung. */
        fun dayKey(now: Long): String = Calendar.getInstance().run {
            timeInMillis = now
            "%04d-%02d-%02d".format(
                get(Calendar.YEAR),
                get(Calendar.MONTH) + 1,
                get(Calendar.DAY_OF_MONTH),
            )
        }
    }
}
