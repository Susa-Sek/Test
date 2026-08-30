package de.shortblock.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchBudgetTest {

    @Test
    fun `a normal scan interval is credited`() {
        assertEquals(150L, WatchBudget.creditableDelta(lastTickAtMs = 1_000L, nowMs = 1_150L))
    }

    /**
     * Der wichtigste Test dieser Datei.
     *
     * Wer Instagram schließt und zwei Stunden später zurückkommt, hat in der Zwischenzeit keine
     * Reels gesehen. Ohne die Schrittgrenze wäre das Kontingent nach einer Nacht Standby
     * aufgebraucht, ohne dass ein einziges Video lief.
     */
    @Test
    fun `a long gap is not credited at all`() {
        val twoHours = 2 * 60 * 60 * 1000L
        assertEquals(0L, WatchBudget.creditableDelta(lastTickAtMs = 1_000L, nowMs = 1_000L + twoHours))
        assertEquals(0L, WatchBudget.creditableDelta(lastTickAtMs = 1_000L, nowMs = 1_000L + 2_001L))
    }

    @Test
    fun `exactly at the limit still counts`() {
        assertEquals(2_000L, WatchBudget.creditableDelta(1_000L, 3_000L))
    }

    @Test
    fun `the very first tick credits nothing`() {
        assertEquals(0L, WatchBudget.creditableDelta(lastTickAtMs = 0L, nowMs = 5_000L))
    }

    /** Zeitzonenwechsel und Zeitsprünge lassen die Wanduhr rückwärts laufen. */
    @Test
    fun `a backwards clock credits nothing`() {
        assertEquals(0L, WatchBudget.creditableDelta(lastTickAtMs = 5_000L, nowMs = 1_000L))
    }

    // --- Kontingent ----------------------------------------------------------------------

    @Test
    fun `no budget means block immediately`() {
        assertFalse(WatchBudget.hasBudget(0))
        assertTrue(WatchBudget.isExhausted(spentSeconds = 0, budgetMinutes = 0))
    }

    @Test
    fun `an unused budget is not exhausted`() {
        assertFalse(WatchBudget.isExhausted(spentSeconds = 0, budgetMinutes = 10))
        assertEquals(600, WatchBudget.remainingSeconds(0, 10))
    }

    @Test
    fun `a budget runs out exactly at its limit`() {
        assertFalse(WatchBudget.isExhausted(spentSeconds = 599, budgetMinutes = 10))
        assertTrue(WatchBudget.isExhausted(spentSeconds = 600, budgetMinutes = 10))
    }

    /** Nie negative Restzeit anzeigen — „noch -3 Min“ wäre schlicht kaputt. */
    @Test
    fun `remaining time never goes negative`() {
        assertEquals(0, WatchBudget.remainingSeconds(spentSeconds = 9_000, budgetMinutes = 10))
    }
}
