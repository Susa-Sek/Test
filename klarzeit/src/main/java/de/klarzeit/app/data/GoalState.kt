package de.klarzeit.app.data

/**
 * Verhältnis zwischen Netto-Zeit und Tagesziel.
 *
 * Die eine Regel, die hier zählt: **eine Meldung pro Tag.** Eine App, die im Minutentakt
 * meldet, dass man immer noch über dem Ziel liegt, wird stummgeschaltet und ist damit
 * nutzlos — deshalb merkt sich [shouldNotify] den Tag der letzten Meldung.
 */
object GoalState {

    enum class Status {
        /** Deutlich unter dem Ziel. */
        UNDER,

        /** Ab 80 Prozent — das Widget färbt sich, gemeldet wird noch nichts. */
        CLOSE,

        /** Ziel überschritten. */
        OVER,
    }

    private const val CLOSE_FRACTION = 0.8

    fun status(countedMillis: Long, goalMillis: Long): Status = when {
        goalMillis <= 0L -> Status.UNDER
        countedMillis >= goalMillis -> Status.OVER
        countedMillis >= goalMillis * CLOSE_FRACTION -> Status.CLOSE
        else -> Status.UNDER
    }

    /**
     * @param today Tagesschlüssel, etwa `2026-09-06`.
     * @param lastNotifiedDay Tag, an dem zuletzt gemeldet wurde; `null`, wenn noch nie.
     */
    fun shouldNotify(
        countedMillis: Long,
        goalMillis: Long,
        today: String,
        lastNotifiedDay: String?,
    ): Boolean =
        goalMillis > 0L &&
            countedMillis >= goalMillis &&
            today != lastNotifiedDay

    /** Wie weit der Balken gefüllt ist, gedeckelt bei 1. */
    fun progress(countedMillis: Long, goalMillis: Long): Float =
        if (goalMillis <= 0L) 0f else (countedMillis.toFloat() / goalMillis).coerceIn(0f, 1f)
}
