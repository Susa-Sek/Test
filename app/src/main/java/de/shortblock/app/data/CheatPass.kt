package de.shortblock.app.data

/**
 * Der Tages-Cheat: einmal am Tag fünf Minuten — aber nicht geschenkt.
 *
 * In v0.5 war er ein einziger Tipp und lief sofort. Genau die Geste, die man aus Reflex macht,
 * und genau in dem Moment, in dem der Reflex am stärksten ist: direkt nachdem geblockt wurde.
 * Seit v0.8 stehen drei Hürden davor — ein Satz zum Abtippen, eine Wartezeit, und die Minuten
 * zählen auf das Tageskontingent.
 *
 * **Alle Phasen hängen an einem einzigen gespeicherten Zeitstempel.** Das ist der Kern: Sonst
 * bräuchte es einen Wecker, der genau dann feuert, wenn die Wartezeit endet — und ein Dienst,
 * den der Hersteller zwischendurch abräumt, verlöre ihn. Aus `armedAt` lässt sich jede Phase
 * jederzeit neu ausrechnen.
 *
 * Reine Rechenregel, kein Android.
 */
object CheatPass {

    /** Die Wartezeit gegen den Impuls. Wer eine Minute wartet, will es meistens nicht mehr. */
    const val WAIT_SECONDS = 60

    const val DURATION_MINUTES = 5

    private const val WAIT_MS = WAIT_SECONDS * 1000L
    private const val DURATION_MS = DURATION_MINUTES * 60 * 1000L

    /** Wann der Cheat nach dem Anfordern zu laufen beginnt. */
    fun startsAt(armedAtMs: Long): Long = armedAtMs + WAIT_MS

    /** Wann er endet. */
    fun endsAt(armedAtMs: Long): Long = startsAt(armedAtMs) + DURATION_MS

    fun stage(armedAtMs: Long, usedOnDay: Int, today: Int, nowMs: Long): CheatStage {
        // Nie angefordert, oder an einem anderen Tag — dann ist heute wieder frei.
        if (armedAtMs <= 0L || usedOnDay != today) return CheatStage.FREE

        val untilStart = startsAt(armedAtMs) - nowMs
        // Mehr Vorlauf als die volle Wartezeit kann es nie geben: Die Systemuhr wurde
        // zurückgestellt. Dann gilt der Cheat als beendet, nie als endlos. Im Zweifel blocken.
        if (untilStart > WAIT_MS) return CheatStage.USED
        if (untilStart > 0L) return CheatStage.WAITING
        if (endsAt(armedAtMs) - nowMs > 0L) return CheatStage.RUNNING
        return CheatStage.USED
    }

    /** Sekunden bis zum Start, aufgerundet. 0, wenn gerade nicht gewartet wird. */
    fun waitRemainingSeconds(armedAtMs: Long, nowMs: Long): Int =
        remaining(startsAt(armedAtMs) - nowMs, WAIT_MS)

    /** Sekunden bis zum Ende, aufgerundet. 0, wenn gerade nichts läuft. */
    fun runRemainingSeconds(armedAtMs: Long, nowMs: Long): Int {
        if (startsAt(armedAtMs) - nowMs > 0L) return 0
        return remaining(endsAt(armedAtMs) - nowMs, DURATION_MS)
    }

    private fun remaining(deltaMs: Long, maxMs: Long): Int {
        if (deltaMs <= 0L || deltaMs > maxMs) return 0
        return ((deltaMs + 999L) / 1000L).toInt()
    }
}

/** Die vier Phasen des Cheats. `OFF` kommt nicht von hier, sondern vom Schalter. */
enum class CheatStage {
    /** Heute noch nicht angefordert. */
    FREE,

    /** Angefordert, die Wartezeit läuft — geblockt wird weiter. */
    WAITING,

    /** Die fünf Minuten laufen. */
    RUNNING,

    /** Für heute vorbei. */
    USED,
}
