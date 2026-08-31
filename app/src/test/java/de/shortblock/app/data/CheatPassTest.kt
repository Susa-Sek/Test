package de.shortblock.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatPassTest {

    private val now = 1_800_000_000_000L
    private val fiveMinutes = 5 * 60 * 1000L

    @Test
    fun `free until redeemed today`() {
        assertTrue(CheatPass.isAvailable(usedOnDay = 20320, today = 20321))
        assertFalse(CheatPass.isAvailable(usedOnDay = 20321, today = 20321))
    }

    /** Frisch installiert: noch nie eingelöst, also frei. */
    @Test
    fun `free on a fresh install`() {
        assertTrue(CheatPass.isAvailable(usedOnDay = 0, today = 20321))
    }

    @Test
    fun `runs until the end and not a second longer`() {
        val until = CheatPass.endsAt(now)
        assertTrue(CheatPass.isActive(until, now))
        assertTrue(CheatPass.isActive(until, now + fiveMinutes - 1))
        assertFalse(CheatPass.isActive(until, now + fiveMinutes))
        assertFalse(CheatPass.isActive(until, now + fiveMinutes + 1))
    }

    @Test
    fun `nothing is running before the first redemption`() {
        assertFalse(CheatPass.isActive(untilMillis = 0L, nowMillis = now))
    }

    /**
     * Die Falle, die einen Cheat sonst unendlich macht: Wer die Systemuhr zurückstellt,
     * schiebt das gespeicherte Ende beliebig weit in die Zukunft. Mehr als eine volle
     * Cheat-Dauer Rest kann es nie geben — also gilt das als beendet.
     */
    @Test
    fun `a rewound clock ends the cheat instead of extending it`() {
        val until = CheatPass.endsAt(now)
        assertFalse(CheatPass.isActive(until, now - 60 * 60 * 1000L))
    }

    @Test
    fun `remaining time rounds up to full seconds`() {
        val until = CheatPass.endsAt(now)
        assertEquals(300, CheatPass.remainingSeconds(until, now))
        assertEquals(1, CheatPass.remainingSeconds(until, now + fiveMinutes - 1))
        assertEquals(0, CheatPass.remainingSeconds(until, now + fiveMinutes))
    }
}
