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

    data class Snapshot(
        val connectedAtMs: Long = 0L,
        val lastEventAtMs: Long = 0L,
        val lastRefreshAtMs: Long = 0L,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun onConnected(nowMs: Long = System.currentTimeMillis()) {
        _state.value = Snapshot(connectedAtMs = nowMs)
    }

    fun onEvent(nowMs: Long = System.currentTimeMillis()) {
        val current = _state.value
        // Nur schreiben, wenn sich die Sekunde geändert hat: Bei Videowiedergabe kämen sonst
        // dutzende Flow-Emissionen pro Sekunde, die niemand braucht.
        if (nowMs - current.lastEventAtMs < 1_000L) return
        _state.value = current.copy(lastEventAtMs = nowMs)
    }

    fun onRefreshed(nowMs: Long = System.currentTimeMillis()) {
        _state.value = _state.value.copy(lastRefreshAtMs = nowMs)
    }

    fun onDisconnected() {
        _state.value = Snapshot()
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
