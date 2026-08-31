package de.shortblock.app.data

/**
 * Der Tages-Cheat: einmal am Tag fünf Minuten bewusst erlaubt.
 *
 * Warum es das neben dem Tageskontingent gibt: Ein Kontingent läuft ab, sobald man die App
 * öffnet — und ist damit weg, bevor das eine Video kommt, für das man es aufheben wollte. Der
 * Cheat ist das Gegenteil: Es läuft nichts, bis man ihn ausdrücklich einlöst.
 *
 * Reine Rechenregel, kein Android. Der Zustand liegt in [BlockSettings].
 */
object CheatPass {

    const val DURATION_MINUTES = 5
    private const val DURATION_MS = DURATION_MINUTES * 60 * 1000L

    /** Heute noch nicht eingelöst? */
    fun isAvailable(usedOnDay: Int, today: Int): Boolean = usedOnDay != today

    /**
     * Läuft gerade ein Cheat?
     *
     * Die zweite Bedingung ist die Uhr-Falle, dieselbe Klasse Fehler wie die Schrittgrenze in
     * [WatchBudget]: Liegt das Ende weiter als eine volle Cheat-Dauer in der Zukunft, wurde die
     * Systemuhr zurückgestellt. Dann gilt der Cheat als **beendet** und nicht als endlos — im
     * Zweifel blocken.
     */
    fun isActive(untilMillis: Long, nowMillis: Long): Boolean {
        val remaining = untilMillis - nowMillis
        return remaining in 1..DURATION_MS
    }

    fun remainingSeconds(untilMillis: Long, nowMillis: Long): Int {
        if (!isActive(untilMillis, nowMillis)) return 0
        return ((untilMillis - nowMillis + 999L) / 1000L).toInt()
    }

    /** Endzeitpunkt beim Einlösen. */
    fun endsAt(nowMillis: Long): Long = nowMillis + DURATION_MS
}
