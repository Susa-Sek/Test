package de.shortblock.app.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import de.shortblock.app.service.BlockerAccessibilityService

/**
 * Alles, was mit den System-Einstellungsseiten zu tun hat.
 *
 * Der Einrichtungsweg ist der häufigste Grund, warum Blocker-Apps „nicht funktionieren“:
 * Ab Android 13 ist der Bedienungshilfe-Schalter für sideloadete Apps ausgegraut, bis in der
 * App-Info einmal „Eingeschränkte Einstellungen zulassen“ gewählt wurde. Ob das geschehen ist,
 * lässt sich nicht abfragen — deshalb ist Schritt 1 im Onboarding ein manueller Schritt mit
 * Direktlink, und erst Schritt 2 ist prüfbar.
 */
object SystemSettings {

    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        val expected = ComponentName(context.packageName, BlockerAccessibilityService::class.java.name)
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val service = info.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(service.packageName, service.name) == expected
            }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun openAccessibilitySettings(context: Context) {
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openAppInfo(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
    }

    fun openBatterySettings(context: Context) {
        @Suppress("BatteryLife")
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", context.packageName, null))
        if (!start(context, direct)) {
            start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** Gibt zurück, ob die Seite geöffnet werden konnte — Hersteller-ROMs lassen einzelne weg. */
    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
