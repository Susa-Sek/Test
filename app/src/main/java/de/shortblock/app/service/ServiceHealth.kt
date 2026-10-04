package de.shortblock.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lebenszeichen des Dienstes.
 *
 * Hintergrund: Der Dienst hörte nach einigen Stunden auf zu blocken, und erst Aus- und
 * Wiedereinschalten half. Dafür gibt es zwei plausible Zustände, die von außen gleich aussehen:
 * Der Prozess wurde von der Energieverwaltung abgeräumt, oder er lebt noch, bekommt aber keine
 * Ereignisse mehr. Ohne Messung rät man beim nächsten Mal wieder — deshalb dieses Objekt.
 *
 * Der belastbare Befund ist [Snapshot.connectedAtMs]: Bedienungshilfe und Oberfläche laufen im
 * selben Prozess. Sagt das System „Dienst ist an", steht hier aber 0, dann existiert das
 * Dienst-Objekt in diesem Prozess nicht — genau der Zustand, den nur Aus/Ein behebt.
 */
object ServiceHealth {

    /** Ab dieser Stille gilt der Dienst als untätig — nicht als kaputt. */
    const val IDLE_AFTER_MS = 30 * 60 * 1000L

    /**
     * So kurz nacheinander wird ein Ereignis nicht erneut vermerkt.
     *
     * Bei Videowiedergabe kämen sonst dutzende Flow-Emissionen pro Sekunde, die niemand braucht.
     */
    const val MIN_EVENT_GAP_MS = 1_000L

    /**
     * Der gemessene Zustand — und die vier Übergänge als **reine Funktionen**.
     *
     * Die Übergänge stehen hier und nicht in den Methoden unten, weil genau an ihnen der
     * Fehler „Letztes Ereignis: nie" hing. Was prüfbar sein muss, braucht kein Android.
     */
    data class Snapshot(
        val connectedAtMs: Long = 0L,
        val lastEventAtMs: Long = 0L,
        val lastRefreshAtMs: Long = 0L,
    ) {
        /**
         * Der Dienst ist (wieder) verbunden — **nur die Verbindungszeit wird gesetzt**.
         *
         * Bis v0.15.0 stand hier ein frischer `Snapshot(connectedAtMs = nowMs)`, der die
         * Messung mitwegwarf. Android baut das Dienst-Objekt neu, ohne den Prozess zu
         * beenden; [BlockLog] überlebt das, dieser Zustand nicht. Genau so entstand die
         * gemeldete Anzeige „Letztes Ereignis: nie" **neben einem gefüllten Protokoll**.
         * Schlimmer als die falsche Anzeige war die Folge: [WakeRepair.needsRepair] gibt bei
         * `lastEventAtMs == 0` immer `false` zurück — die Aufwach-Reparatur, der ganze Zweck
         * von v0.11.1, war nach jedem Neuverbinden abgeschaltet.
         */
        fun connected(nowMs: Long): Snapshot = copy(connectedAtMs = nowMs)

        /**
         * Das Dienst-Objekt ist weg — aber was es gemessen hat, bleibt.
         *
         * Gelöscht wird allein [connectedAtMs]; darauf stützt [classifyHealth] den Fall
         * [Health.NOT_CONNECTED]. Die beiden anderen Felder sind Messwerte der Vergangenheit:
         * Dass um 8:14 ein Ereignis kam, hört nicht auf wahr zu sein, weil der Dienst um 8:20
         * neu gebunden wurde.
         */
        fun disconnected(): Snapshot = copy(connectedAtMs = 0L)

        /**
         * Ein Ereignis ist angekommen.
         *
         * Die Entprellung lässt eine **zurückgestellte Systemuhr** ausdrücklich durch: Wäre
         * `nowMs` kleiner als der gespeicherte Wert, stünde dort eine Zeit in der Zukunft, und
         * eine einfache `< MIN_EVENT_GAP_MS`-Prüfung hätte sie bis zum Einholen festgeschrieben.
         * Dann zeigt der Kasten „nie" (die Oberfläche hält eine Zukunftszeit für unbekannt) und
         * [WakeRepair] rechnet mit negativer Stille — dieselbe Lehre wie bei [CheatPass] und
         * [FeedGuard.isEcho]: Eine zurückgestellte Uhr darf nie die Messung einfrieren.
         */
        fun withEvent(nowMs: Long): Snapshot {
            val since = nowMs - lastEventAtMs
            if (since in 0L until MIN_EVENT_GAP_MS) return this
            return copy(lastEventAtMs = nowMs)
        }

        /** Die Ereignis-Konfiguration wurde neu gesetzt (die Aufwach-Reparatur). */
        fun refreshed(nowMs: Long): Snapshot = copy(lastRefreshAtMs = nowMs)
    }

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun onConnected(nowMs: Long = System.currentTimeMillis()) {
        _state.value = _state.value.connected(nowMs)
    }

    fun onEvent(nowMs: Long = System.currentTimeMillis()) {
        _state.value = _state.value.withEvent(nowMs)
    }

    fun onRefreshed(nowMs: Long = System.currentTimeMillis()) {
        _state.value = _state.value.refreshed(nowMs)
    }

    fun onDisconnected() {
        _state.value = _state.value.disconnected()
    }
}

/** Wie es dem Dienst geht — in der Reihenfolge, in der die Oberfläche darauf reagieren soll. */
enum class Health {
    /** Bedienungshilfe ist gar nicht eingeschaltet. */
    OFF,

    /**
     * Das System meldet den Dienst als eingeschaltet, aber in diesem Prozess läuft kein
     * Dienst-Objekt. Das ist der Fehlerfall, den nur Aus- und Wiedereinschalten behebt.
     */
    NOT_CONNECTED,

    /** Läuft, hat aber länger nichts gesehen. Völlig normal, wenn man die Apps nicht öffnet. */
    IDLE,

    /** Läuft und hat kürzlich Ereignisse verarbeitet. */
    HEALTHY,
}

/**
 * Reine Ableitung, damit sie testbar ist.
 *
 * Wichtig: [Health.IDLE] ist ausdrücklich **kein** Fehler. Der Dienst bekommt nur Ereignisse
 * aus den überwachten Apps; wer vier Stunden nicht auf Instagram war, hat vier Stunden Stille.
 * Diese Stille als Warnung zu zeigen, wäre ein Fehlalarm — und die App verlöre ihre
 * Glaubwürdigkeit bei der einen Warnung, auf die es ankommt.
 */
fun classifyHealth(
    serviceEnabled: Boolean,
    snapshot: ServiceHealth.Snapshot,
    nowMs: Long,
): Health = when {
    !serviceEnabled -> Health.OFF
    snapshot.connectedAtMs <= 0L -> Health.NOT_CONNECTED
    snapshot.lastEventAtMs <= 0L -> Health.IDLE
    nowMs - snapshot.lastEventAtMs > ServiceHealth.IDLE_AFTER_MS -> Health.IDLE
    else -> Health.HEALTHY
}
