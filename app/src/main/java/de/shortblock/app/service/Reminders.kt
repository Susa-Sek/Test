package de.shortblock.app.service

import kotlin.random.Random

/**
 * Welcher Spruch als Nächstes kommt.
 *
 * Die Texte selbst stehen in den `values`-Ordnern — hier steht nur die Auswahl, damit sie
 * prüfbar ist. Die eine Regel, die zählt: **nie zweimal hintereinander derselbe.** Ein Spruch,
 * den man schon kennt, wird nicht gelesen, und ein Popup, das nicht gelesen wird, ist nur eine
 * Verzögerung.
 */
object Reminders {

    /**
     * @param count Anzahl verfügbarer Sprüche.
     * @param lastIndex zuletzt gezeigter Spruch, oder -1 für „noch keiner“.
     * @param roll Zufallszahl in `0 until count` (bzw. `count - 1`, wenn ein Spruch wegfällt).
     */
    fun pickIndex(count: Int, lastIndex: Int, roll: Int): Int {
        if (count <= 0) return -1
        if (count == 1) return 0
        if (lastIndex !in 0 until count) return roll.mod(count)
        // Aus den count-1 anderen wählen und die Lücke überspringen — so ist jeder andere
        // Spruch gleich wahrscheinlich, ohne Schleife und ohne Neuwürfeln.
        val other = roll.mod(count - 1)
        return if (other >= lastIndex) other + 1 else other
    }

    fun next(count: Int, lastIndex: Int, random: Random = Random.Default): Int =
        pickIndex(count, lastIndex, if (count > 1) random.nextInt(count) else 0)
}
