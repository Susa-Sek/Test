package de.shortblock.app.service

/**
 * Wann der Wächter Alarm schlagen darf.
 *
 * Hintergrund: Hersteller-ROMs (vor allem Xiaomi/HyperOS, Samsung, Oppo) räumen den Prozess ab
 * und schalten die Bedienungshilfe dabei mit ab — meist über Nacht oder nach einem Neustart.
 * Der Nutzer merkt das erst Tage später, weil nichts passiert, wenn nichts blockt. Genau das
 * ist der Fehlermodus, den eine Blocker-App am wenigsten haben darf.
 *
 * Eine App darf ihre eigene Bedienungshilfe **nicht** wieder einschalten; das verbietet Android
 * aus gutem Grund. Bleibt: früh und genau einmal Bescheid sagen.
 *
 * Reine Funktion, damit die drei Fälle prüfbar sind — die Regel selbst ist die ganze Logik.
 */
object WatchdogPolicy {

    /**
     * @param everEnabled ob der Dienst auf diesem Gerät schon einmal lief. Ohne das würde die
     *   App direkt nach der Installation meckern, bevor überhaupt eingerichtet wurde.
     * @param enabledNow ob Android den Dienst gerade als aktiv führt.
     * @param alreadyWarned ob für dieses Aussetzen schon gewarnt wurde. Verhindert, dass alle
     *   zwei Stunden dieselbe Meldung kommt, wenn jemand den Dienst bewusst ausgelassen hat.
     */
    fun shouldWarn(everEnabled: Boolean, enabledNow: Boolean, alreadyWarned: Boolean): Boolean =
        everEnabled && !enabledNow && !alreadyWarned
}
