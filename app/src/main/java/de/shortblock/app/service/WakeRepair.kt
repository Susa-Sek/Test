package de.shortblock.app.service

/**
 * Wann die Bedienungshilfe geweckt werden muss.
 *
 * Hintergrund, und zwar der ärgerlichste Fehlermodus dieser App: Morgens greift die Sperre
 * manchmal nicht. Der Grund ist, dass die einzige Reparatur — `refreshServiceInfo()` — bis
 * v0.11 ausschliesslich am Herzschlag hing, einem `delay(5 min)` in einer Koroutine. Genau
 * solche Timer setzt Android im Doze-Modus über Nacht aus. Über acht Stunden Schlaf lief
 * dadurch unter Umständen keine einzige Reparatur, und der erste Griff zum Telefon traf auf
 * eine eingeschlafene Ereignis-Pipeline.
 *
 * Repariert wird deshalb jetzt beim **Aufwachen** statt nach Uhr: beim Entsperren, und beim
 * ersten Ereignis nach langer Stille.
 *
 * Reine Funktion, damit die Randfälle prüfbar sind — sie sind der ganze Inhalt.
 */
object WakeRepair {

    /**
     * Ab dieser Stille gilt die Pipeline als möglicherweise eingeschlafen.
     *
     * Zehn Minuten sind bewusst grosszügig: Ein Wechsel zwischen zwei Apps, ein Telefonat,
     * ein Blick in die Nachrichten — all das darf keine Reparatur auslösen. Gesucht ist die
     * Nachtruhe, nicht die Kaffeepause.
     */
    const val GAP_MS = 10 * 60 * 1000L

    /** So kurz nach einer Reparatur lohnt keine zweite. */
    const val COOLDOWN_MS = 60 * 1000L

    /**
     * @param lastEventAtMs wann zuletzt ein Ereignis kam; 0, wenn noch nie.
     * @param lastRepairAtMs wann zuletzt repariert wurde; 0, wenn noch nie.
     */
    fun needsRepair(
        lastEventAtMs: Long,
        lastRepairAtMs: Long,
        nowMs: Long,
        gapMs: Long = GAP_MS,
    ): Boolean {
        // Noch nie ein Ereignis: Das ist ein Anfang, kein Aufwachen. Beim Verbinden wird
        // ohnehin frisch aufgesetzt.
        if (lastEventAtMs <= 0L) return false

        val silence = nowMs - lastEventAtMs
        // Rückwärts laufende Uhr — Zeitzonenwechsel oder von Hand gestellt. Eine negative
        // Stille ist keine Stille; im Zweifel nichts tun.
        if (silence < 0L) return false
        if (silence < gapMs) return false

        val sinceRepair = nowMs - lastRepairAtMs
        if (lastRepairAtMs > 0L && sinceRepair in 0L until COOLDOWN_MS) return false

        return true
    }
}
