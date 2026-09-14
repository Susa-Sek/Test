package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceHealthTest {

    // Echte Epoch-Millisekunden, damit „vor 10 Stunden" nicht negativ wird.
    private val now = 1_800_000_000_000L

    @Test
    fun `service switched off is reported as off`() {
        val snapshot = ServiceHealth.Snapshot(connectedAtMs = now - 5_000, lastEventAtMs = now)
        assertEquals(Health.OFF, classifyHealth(serviceEnabled = false, snapshot, now))
    }

    /**
     * Der Befund, um den es bei dem Fehler geht: Android meldet den Dienst als eingeschaltet,
     * aber in diesem Prozess läuft kein Dienst-Objekt. Genau dieser Zustand verschwindet nur
     * durch Aus- und Wiedereinschalten.
     */
    @Test
    fun `enabled but never connected in this process is the failure case`() {
        val snapshot = ServiceHealth.Snapshot(connectedAtMs = 0L)
        assertEquals(Health.NOT_CONNECTED, classifyHealth(serviceEnabled = true, snapshot, now))
    }

    @Test
    fun `a recent event means healthy`() {
        val snapshot = ServiceHealth.Snapshot(connectedAtMs = now - 60_000, lastEventAtMs = now - 5_000)
        assertEquals(Health.HEALTHY, classifyHealth(serviceEnabled = true, snapshot, now))
    }

    /**
     * Stille ist kein Fehler.
     *
     * Der Dienst bekommt nur Ereignisse aus den überwachten Apps. Wer vier Stunden nicht auf
     * Instagram war, hat vier Stunden Stille — das als Warnung zu zeigen wäre ein Fehlalarm,
     * und nach dem dritten Fehlalarm liest niemand mehr die eine Warnung, auf die es ankommt.
     */
    @Test
    fun `long silence is idle, not broken`() {
        val snapshot = ServiceHealth.Snapshot(
            connectedAtMs = now - 10 * 60 * 60 * 1000L,
            lastEventAtMs = now - 4 * 60 * 60 * 1000L,
        )
        assertEquals(Health.IDLE, classifyHealth(serviceEnabled = true, snapshot, now))
    }

    @Test
    fun `connected but no event yet is idle`() {
        val snapshot = ServiceHealth.Snapshot(connectedAtMs = now - 1_000, lastEventAtMs = 0L)
        assertEquals(Health.IDLE, classifyHealth(serviceEnabled = true, snapshot, now))
    }
}
