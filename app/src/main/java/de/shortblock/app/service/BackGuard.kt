package de.shortblock.app.service

/**
 * Obergrenze für aufeinanderfolgende Zurück-Aktionen.
 *
 * **Warum es das gibt:** `blockAndGoBack` drückt Zurück und wartet `BACK_COOLDOWN_MS` = 800 ms.
 * Trifft die Regel danach wieder, drückt es erneut — ohne Ende. Bei einem korrekten Block ist
 * das harmlos, weil das erste Zurück den Bildschirm verlässt. Bei einem **Fehlalarm** ist es
 * fatal: In v0.11.3 traf `yt_shorts_fullscreen` das Shorts-Regal in der Empfehlungsliste unter
 * normalen Videos, und die Kette drückte Zurück, bis YouTube zu war.
 *
 * Die Regel dahinter ist einfach: Hilft Zurück dreimal hintereinander nicht, hilft es auch beim
 * vierten Mal nicht — es schliesst nur die App. Dann lieber aufhören und es **sichtbar machen**.
 * Genau daran fehlte es bisher: Ein Fehlalarm war still, man flog raus und das Protokoll zeigte
 * den Regel-Namen wie einen gewollten Block.
 *
 * Reine Regel, kein Android; der Zustand liegt im Dienst — wie bei [WakeRepair].
 */
object BackGuard {

    /** So viele Zurück in Folge sind erlaubt. Das nächste wäre das, das die App schliesst. */
    const val MAX_CONSECUTIVE = 3

    /**
     * Liegen zwei Zurück weiter auseinander, ist es keine Kette mehr.
     *
     * Drei Sekunden trennen zuverlässig: Nach einem richtigen Block ist man draussen, und wieder
     * in Shorts hineinzukommen dauert länger. Eine Kette wächst nur, wenn Zurück den Bildschirm
     * gar nicht verlässt — und das ist der Fall, um den es hier geht.
     */
    const val CHAIN_GAP_MS = 3_000L

    /** Wie lange nach einer gerissenen Kette nicht mehr eingegriffen wird. */
    const val PAUSE_MS = 30_000L

    /**
     * Gehört dieses Zurück noch zur laufenden Kette, oder beginnt eine neue?
     *
     * @param lastBackAtMs Zeitpunkt des letzten Zurück; 0 heisst „es gab noch keins“.
     */
    fun continuesChain(lastBackAtMs: Long, nowMs: Long, gapMs: Long = CHAIN_GAP_MS): Boolean {
        if (lastBackAtMs <= 0L) return false
        val sinceLast = nowMs - lastBackAtMs
        // Negativer Abstand heisst zurückgestellte Systemuhr. Dann neu anfangen statt eine
        // Kette endlos weiterlaufen zu lassen — dieselbe Vorsicht wie in CheatPass.
        if (sinceLast < 0L) return false
        return sinceLast <= gapMs
    }

    /** Ist die Kette zu lang geworden — hilft Zurück hier offensichtlich nicht? */
    fun isRunaway(chainLength: Int, max: Int = MAX_CONSECUTIVE): Boolean = chainLength >= max
}
