package de.shortblock.app.service

/**
 * Der harte Stopp gegen den Feed — und die Abklingzeit danach.
 *
 * **Warum nicht mehr „Zurück drücken".** Zurück ist das falsche Werkzeug für einen Startfeed:
 * Es verlässt den Feed nicht, es blättert in Instagram eine Ebene zurück — und steht der Feed
 * danach wieder vorne, drückt die App erneut. Genau diese Kette hat in v0.11.4 YouTube
 * zugedrückt, und [BackGuard] ist bis heute nur der Verband darauf.
 *
 * Deshalb zwei Mittel, beide ohne Schleifenrisiko:
 *
 * 1. **Ein eigener Bildschirm davor** ([de.shortblock.app.ui.BlockScreenActivity]). Das ist der
 *    Weg, den die ausgereiften Blocker gehen, und er ist aus einem strukturellen Grund besser
 *    als die Wand aus v0.11: Eine Activity ist kein frei schwebendes Fenster. Das System
 *    verwaltet ihren Lebenslauf, Home und Zurück funktionieren immer, und stirbt unser Prozess
 *    mitten darin, ist sie weg. Die Wand dagegen hing über fremden Apps, weil ihr Abräumen an
 *    einem Ereignis hing, das nie kam. **Was aufgeräumt werden muss, darf nicht uns gehören.**
 *    Dafür braucht es „Über anderen Apps anzeigen": Ohne diese Berechtigung lässt Android eine
 *    App aus dem Hintergrund keine Activity starten.
 * 2. **Nach Hause** (`GLOBAL_ACTION_HOME`), wenn die Berechtigung fehlt. Einmal gedrückt, und
 *    der Feed ist weg — ohne Kette, weil der Startbildschirm die Regel nicht mehr erfüllt.
 *
 * Reine Rechenregel, kein Android — wie [FeedGuard], [BackGuard] und [WakeRepair].
 */
object HardStop {

    /**
     * Wie lange der Feed nach einem harten Stopp zu bleibt.
     *
     * Ohne Abklingzeit ist der Stopp wertlos: Instagram wieder öffnen, und die zwei freien
     * Beiträge beginnen von vorn — beliebig oft. Das ist dieselbe Lücke wie beim Zurücksetzen
     * der Sehdauer („weil ein Scan ohne Treffer den Zustand zurücksetzte, begannen die fünf
     * Minuten danach von vorn"), nur an einer anderen Stelle.
     *
     * In der Abklingzeit bleiben die freien Beiträge erhalten — wer Instagram nur für eine
     * Nachricht öffnet, kommt weiter durch. Gescrollt wird nicht mehr: Der erste Beitrag nach
     * der Freigabe führt direkt zum Stopp, ohne die sanfte Stufe.
     */
    const val COOLDOWN_MS = 10 * 60 * 1000L

    /** Was beim harten Stopp geschieht. */
    enum class Means {
        /** Eigener Bildschirm davor — braucht „Über anderen Apps anzeigen". */
        BLOCK_SCREEN,

        /** Rückfall ohne diese Berechtigung: einmal nach Hause. */
        GO_HOME,
    }

    fun means(canShowBlockScreen: Boolean): Means =
        if (canShowBlockScreen) Means.BLOCK_SCREEN else Means.GO_HOME

    /**
     * Läuft die Abklingzeit noch?
     *
     * @param lastStopAtMs Wanduhr-Zeit des letzten harten Stopps; 0 heisst „es gab keinen".
     */
    fun isInCooldown(lastStopAtMs: Long, nowMs: Long, cooldownMs: Long = COOLDOWN_MS): Boolean {
        if (lastStopAtMs <= 0L) return false
        val since = nowMs - lastStopAtMs
        // Zurückgestellte Uhr: Die Abklingzeit gilt als abgelaufen, nie als endlos — dieselbe
        // Richtung wie bei GuardianLock.remainingLockoutSeconds und CheatPass. Eine Sperre, die
        // sich durch eine verstellte Uhr verlängert, sperrt irgendwann jemanden aus.
        if (since < 0L) return false
        return since < cooldownMs
    }

    /** Restliche Abklingzeit in Minuten, aufgerundet — für die Anzeige auf dem Blockbildschirm. */
    fun remainingMinutes(lastStopAtMs: Long, nowMs: Long, cooldownMs: Long = COOLDOWN_MS): Int {
        if (!isInCooldown(lastStopAtMs, nowMs, cooldownMs)) return 0
        val remaining = cooldownMs - (nowMs - lastStopAtMs)
        return ((remaining + 59_999L) / 60_000L).toInt()
    }
}
