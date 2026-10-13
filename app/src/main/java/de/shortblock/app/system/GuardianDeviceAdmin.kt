package de.shortblock.app.system

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import de.shortblock.app.R

/**
 * Der Geräteadministrator — einzig dafür da, die Deinstallation zu verweigern.
 *
 * **Ohne eine einzige Richtlinie**, und das ist der Punkt: Android verweigert die Deinstallation
 * schon deshalb, *weil* ein Admin aktiv ist, nicht wegen irgendeiner Befugnis. Die Datei
 * `res/xml/device_admin.xml` hat deshalb ein leeres `<uses-policies/>`. Weniger Rechte zu
 * verlangen als nötig ist hier die ganze Kunst — eine App, die den Bildschirm fremder Apps
 * liest, soll nicht auch noch das Gerät löschen oder Passwörter erzwingen dürfen.
 *
 * Was das **nicht** kann: den Admin selbst festhalten. Wer ihn in *Einstellungen → Sicherheit →
 * Geräteadmin-Apps* deaktiviert, kann danach deinstallieren. Es ist eine zweite Tür, kein
 * Tresor — im abgesicherten Modus, per `adb` und beim Zurücksetzen geht die App immer weg. Der
 * Einrichtungstext sagt das so, damit sich niemand auf mehr verlässt, als da ist.
 */
class GuardianDeviceAdmin : DeviceAdminReceiver() {

    /**
     * Wird vor dem Deaktivieren angezeigt.
     *
     * Kein Versuch, jemanden aufzuhalten — nur die ehrliche Auskunft, was der Klick bewirkt.
     * Die Sperre in der App bleibt davon unberührt; nur der Deinstallationsschutz fällt.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        context.getString(R.string.guardian_admin_disable_warning)
}
