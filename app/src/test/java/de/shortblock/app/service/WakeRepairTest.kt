package de.shortblock.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeRepairTest {

    private val minute = 60_000L
    private val now = 1_000_000_000L

    @Test
    fun `a night of silence needs a repair`() {
        // Der eigentliche Fall: acht Stunden Doze, dann der erste Griff zum Telefon.
        assertTrue(
            WakeRepair.needsRepair(
                lastEventAtMs = now - 8 * 60 * minute,
                lastRepairAtMs = now - 8 * 60 * minute,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `a short break does not`() {
        // App-Wechsel, Telefonat, Blick in die Nachrichten — nichts davon ist Nachtruhe.
        assertFalse(WakeRepair.needsRepair(now - 2 * minute, now - 30 * minute, now))
        assertFalse(WakeRepair.needsRepair(now - 9 * minute, 0L, now))
    }

    @Test
    fun `just repaired means no second repair`() {
        // Sonst liefe nach jeder Pause bei jedem einzelnen Ereignis eine Reparatur.
        assertFalse(
            WakeRepair.needsRepair(
                lastEventAtMs = now - 60 * minute,
                lastRepairAtMs = now - 10_000L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `after the cooldown a repair is allowed again`() {
        assertTrue(
            WakeRepair.needsRepair(
                lastEventAtMs = now - 60 * minute,
                lastRepairAtMs = now - 5 * minute,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `the very first events are a start, not a wake-up`() {
        assertFalse(WakeRepair.needsRepair(lastEventAtMs = 0L, lastRepairAtMs = 0L, nowMs = now))
    }

    @Test
    fun `a clock running backwards changes nothing`() {
        // Zeitzonenwechsel oder von Hand gestellte Uhr: negative Stille ist keine Stille.
        assertFalse(WakeRepair.needsRepair(lastEventAtMs = now + 60 * minute, lastRepairAtMs = 0L, nowMs = now))
        // Und eine Reparatur, die in der Zukunft liegt, darf nicht ewig sperren.
        assertTrue(WakeRepair.needsRepair(now - 60 * minute, lastRepairAtMs = now + minute, nowMs = now))
    }

    @Test
    fun `the threshold is honoured exactly`() {
        assertFalse(WakeRepair.needsRepair(now - WakeRepair.GAP_MS + 1, 0L, now))
        assertTrue(WakeRepair.needsRepair(now - WakeRepair.GAP_MS, 0L, now))
    }
}
