package de.klarzeit.app.data

/**
 * Welcher Zeitraum abgefragt und welcher gerechnet wird.
 *
 * Das sind zwei verschiedene Dinge, und ihre Verwechslung war ein echter Zählfehler: Bis
 * v0.2.0 begann **beides** um Mitternacht. Wer um 23:50 eine App öffnete und bis 00:20
 * weiterscrollte, dessen Vordergrund-Ereignis lag vor dem Abfragefenster — `queryEvents`
 * liefert es dann nie. Für die App begann die Sitzung nicht, also zählte sie nicht, und die
 * Minuten seit Mitternacht fehlten still.
 *
 * Besonders tückisch: Die Rechenfunktion [UsageSessions.foregroundMillis] behandelt den Fall
 * korrekt und hat dafür einen grünen Test. Der prüfte nur nie mit Daten, wie sie das Gerät
 * überhaupt liefern kann.
 *
 * Deshalb hier getrennt und als reine Funktion: **Die Abfrage muss früher beginnen als die
 * Rechnung.** Genau das hält [UsageWindowTest] fest, damit es niemand „aufräumt".
 */
object UsageWindow {

    /**
     * Wie weit vor Tagesbeginn zusätzlich abgefragt wird.
     *
     * Sechs Stunden decken jede realistische Sitzung ab, die über Mitternacht hinausgeht,
     * ohne die Ereignisliste unnötig aufzublähen — Ereignisse sind klein, aber ein ganzer Tag
     * Vorlauf wäre Verschwendung für einen Fall, der selten mehr als eine Stunde dauert.
     */
    const val LOOKBEHIND_MS = 6 * 60 * 60 * 1000L

    /** Der Zeitraum, über den gerechnet wird: der Tag selbst. */
    data class Window(val start: Long, val end: Long) {
        /** Der Zeitraum, der abgefragt wird — mit Vorlauf. */
        val queryStart: Long get() = start - LOOKBEHIND_MS
    }

    fun forDay(dayStart: Long, now: Long): Window = Window(start = dayStart, end = now)
}
