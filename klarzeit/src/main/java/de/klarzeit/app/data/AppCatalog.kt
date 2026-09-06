package de.klarzeit.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * Übersetzt Paketnamen in das, was der Nutzer auf seinem Startbildschirm liest.
 *
 * Fällt der Name nicht auf — etwa weil die App inzwischen deinstalliert wurde, ihre Zeit
 * aber noch in den Nutzungsdaten steht — bleibt der Paketname stehen. Das ist hässlich,
 * aber ehrlicher als die Zeile wegzulassen und die Summe unerklärt zu lassen.
 */
class AppCatalog(context: Context) {

    private val packageManager: PackageManager = context.applicationContext.packageManager
    private val labels = HashMap<String, String>()

    fun label(packageName: String): String = labels.getOrPut(packageName) {
        runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrDefault(packageName)
    }

    fun icon(packageName: String): Drawable? = runCatching {
        packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    /**
     * Der Startbildschirm selbst.
     *
     * Er taucht in den Nutzungsdaten als ganz normale App auf und sammelt beachtliche
     * Zeit — jeder Blick aufs Telefon geht durch ihn hindurch. Als Zeitfresser aufgeführt
     * zu werden hat er trotzdem nicht verdient: Der Startbildschirm ist der Weg zu einer
     * App, nicht das Ziel. Googles eigene Anzeige führt ihn aus demselben Grund nicht.
     *
     * Ermittelt statt festgeschrieben, weil jeder Hersteller einen anderen mitbringt und
     * viele Leute einen eigenen installieren.
     */
    fun homeScreenPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Alle Apps mit Symbol im Startmenü, alphabetisch. Nur diese kann der Nutzer bewusst
     * öffnen, und nur über die lohnt eine Entscheidung in der Ausschlussliste.
     */
    fun launchableApps(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
                .sortedBy { label(it).lowercase() }
        }.getOrDefault(emptyList())
    }
}
