package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

/**
 * Die vier Übergänge — und vor allem: was ein Neuverbinden **nicht** darf.
 *
 * Gemeldet wurde „Letztes Ereignis: nie" neben einem **gefüllten Protokoll**. Beides liegt im
 * selben Prozess; ein abgeräumter Prozess hätte auch das Protokoll geleert. Übrig blieb genau
 * eine Erklärung: Android baut das Dienst-Objekt neu, und `onConnected` warf dabei die Messung
 * weg. Das war nicht nur eine falsche Anzeige — [WakeRepair] schaltet bei `lastEventAtMs == 0`
 * ab, also war die Aufwach-Reparatur nach jedem Neuverbinden aus.
 */
class ServiceHealthTransitionsTest {

    private val now = 1_800_000_000_000L

    private val gemessen = ServiceHealth.Snapshot(
        connectedAtMs = now - 60 * 60 * 1000L,
        lastEventAtMs = now - 90_000L,
        lastRefreshAtMs = now - 120_000L,
    )

    /** Der Fehler, um den es geht. */
    @Test
    fun `reconnecting keeps the measurement`() {
        val danach = gemessen.disconnected().connected(now)

        assertEquals(now, danach.connectedAtMs)
        assertEquals(gemessen.lastEventAtMs, danach.lastEventAtMs)
        assertEquals(gemessen.lastRefreshAtMs, danach.lastRefreshAtMs)
    }

    /**
     * Die Folge, die schwerer wog als die Anzeige: Nach einem Neuverbinden muss die
     * Aufwach-Reparatur weiter scharf sein.
     */
    @Test
    fun `wake repair stays armed across a reconnect`() {
        val lange = ServiceHealth.Snapshot(
            connectedAtMs = now - 10 * 60 * 60 * 1000L,
            lastEventAtMs = now - 9 * 60 * 60 * 1000L,
        )

        val danach = lange.disconnected().connected(now)

        assertTrue(
            "Nach dem Neuverbinden muss die Nachtstille noch messbar sein",
            WakeRepair.needsRepair(danach.lastEventAtMs, danach.lastRefreshAtMs, now),
        )
    }

    /** Getrennt heisst getrennt — aber nur das. */
    @Test
    fun `disconnecting clears only the connection`() {
        val danach = gemessen.disconnected()

        assertEquals(0L, danach.connectedAtMs)
        assertEquals(gemessen.lastEventAtMs, danach.lastEventAtMs)
        assertEquals(
            Health.NOT_CONNECTED,
            classifyHealth(serviceEnabled = true, danach, now),
        )
    }

    // --- Entprellung ---------------------------------------------------------------------

    @Test
    fun `events within the same second are not written twice`() {
        val erst = ServiceHealth.Snapshot().withEvent(now)
        val dann = erst.withEvent(now + 200L)

        assertEquals(now, dann.lastEventAtMs)
        assertEquals(erst, dann)
    }

    @Test
    fun `a later event is written`() {
        val danach = ServiceHealth.Snapshot()
            .withEvent(now)
            .withEvent(now + ServiceHealth.MIN_EVENT_GAP_MS)

        assertEquals(now + ServiceHealth.MIN_EVENT_GAP_MS, danach.lastEventAtMs)
    }

    /**
     * Zurückgestellte Systemuhr: Der neue, kleinere Wert gilt.
     *
     * Sonst stünde in `lastEventAtMs` eine Zeit in der **Zukunft**, und die Entprellung hielte
     * sie fest, bis die Wanduhr sie eingeholt hat. Die Oberfläche zeigt für eine Zukunftszeit
     * „nie" — also wieder genau der gemeldete Befund — und [WakeRepair] rechnete mit negativer
     * Stille. Dieselbe Lehre wie bei [FeedGuard.isEcho] und [CheatPass].
     */
    @Test
    fun `a rewound clock does not freeze the measurement`() {
        val danach = ServiceHealth.Snapshot(lastEventAtMs = now).withEvent(now - 60 * 60 * 1000L)

        assertEquals(now - 60 * 60 * 1000L, danach.lastEventAtMs)
    }
}
