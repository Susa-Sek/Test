package de.shortblock.app.data

/**
 * Rechenregeln für das Tageskontingent — reine Funktionen, ohne Android, ohne Zustand.
 *
 * Die Idee, die die Berechtigung „Nutzungsdaten“ erspart: Die Uhr läuft nur, solange eine
 * Blockregel gerade zuträfe. Der Dienst scannt im Takt von 150 ms, der Abstand zwischen zwei
 * Treffern ist also normalerweise ein Bruchteil einer Sekunde.
 */
object WatchBudget {

    /**
     * Größter Abstand, der noch als „durchgehend geschaut“ zählt.
     *
     * Das ist der wichtigste Wert hier. Wechselt jemand die App oder schaltet den Bildschirm
     * aus, bekommt der Dienst keine Ereignisse mehr — der nächste Abstand wäre sonst Minuten
     * oder Stunden groß, und das Kontingent wäre nach einer Nacht Standby aufgebraucht, ohne
     * dass jemand ein einziges Reel gesehen hat. Über dieser Grenze wird deshalb nichts
     * gutgeschrieben, nur der Zeitstempel neu gesetzt.
     *
     * Die Uhr kann damit nur zu wenig zählen, nie zu viel. Das ist die richtige Richtung: Wer
     * geschenkte Sekunden bekommt, ärgert sich nicht; wer für Standby bezahlt, wirft die App weg.
     */
    const val MAX_STEP_MS = 2_000L

    /**
     * Wie viel Zeit seit dem letzten Treffer angerechnet werden darf.
     *
     * Gibt 0 zurück bei zu großem Abstand, beim allerersten Treffer und bei rückwärts laufender
     * Uhr — Letzteres kann nach einem Zeitzonen- oder Zeitsprung tatsächlich vorkommen.
     */
    fun creditableDelta(lastTickAtMs: Long, nowMs: Long, maxStepMs: Long = MAX_STEP_MS): Long {
        if (lastTickAtMs <= 0L) return 0L
        val delta = nowMs - lastTickAtMs
        if (delta <= 0L || delta > maxStepMs) return 0L
        return delta
    }

    /** Kein Kontingent gesetzt heißt: sofort blocken, so wie vor dieser Version. */
    fun hasBudget(budgetMinutes: Int): Boolean = budgetMinutes > 0

    fun remainingSeconds(spentSeconds: Int, budgetMinutes: Int): Int =
        (budgetMinutes * 60 - spentSeconds).coerceAtLeast(0)

    /**
     * Soll geblockt werden?
     *
     * Ohne Kontingent immer — das ist die Voreinstellung und das bisherige Verhalten.
     */
    fun isExhausted(spentSeconds: Int, budgetMinutes: Int): Boolean =
        !hasBudget(budgetMinutes) || remainingSeconds(spentSeconds, budgetMinutes) <= 0
}
