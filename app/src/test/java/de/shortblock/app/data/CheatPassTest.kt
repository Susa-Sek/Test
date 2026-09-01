package de.shortblock.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CheatPassTest {

    private val now = 1_800_000_000_000L
    private val today = 20321
    private val wait = CheatPass.WAIT_SECONDS * 1000L
    private val duration = CheatPass.DURATION_MINUTES * 60 * 1000L

    private fun stageAt(offsetMs: Long, armedAt: Long = now, usedOn: Int = today) =
        CheatPass.stage(armedAt, usedOn, today, now + offsetMs)

    @Test
    fun `nothing requested means free`() {
        assertEquals(CheatStage.FREE, CheatPass.stage(0L, 0, today, now))
    }

    /** Der ganze Zeitstrahl an einem Stück — hier fällt jede Verschiebung sofort auf. */
    @Test
    fun `the whole timeline in order`() {
        assertEquals(CheatStage.WAITING, stageAt(0L))
        assertEquals(CheatStage.WAITING, stageAt(wait - 1))
        assertEquals(CheatStage.RUNNING, stageAt(wait))
        assertEquals(CheatStage.RUNNING, stageAt(wait + duration - 1))
        assertEquals(CheatStage.USED, stageAt(wait + duration))
        assertEquals(CheatStage.USED, stageAt(wait + duration + 60_000L))
    }

    @Test
    fun `a new day frees it again`() {
        assertEquals(CheatStage.FREE, CheatPass.stage(now, today - 1, today, now + wait + duration))
    }

    /**
     * Die Falle, die den Cheat sonst endlos machte: Wer die Systemuhr zurückstellt, schiebt den
     * gespeicherten Beginn beliebig weit in die Zukunft. Mehr Vorlauf als die volle Wartezeit
     * kann es nie geben — also gilt das als verbraucht.
     */
    @Test
    fun `a rewound clock ends the cheat instead of extending it`() {
        assertEquals(CheatStage.USED, stageAt(-(60 * 60 * 1000L)))
    }

    @Test
    fun `the waiting countdown runs down to the second`() {
        assertEquals(CheatPass.WAIT_SECONDS, CheatPass.waitRemainingSeconds(now, now))
        assertEquals(1, CheatPass.waitRemainingSeconds(now, now + wait - 1))
        assertEquals(0, CheatPass.waitRemainingSeconds(now, now + wait))
    }

    @Test
    fun `the running countdown only starts after the wait`() {
        assertEquals(0, CheatPass.runRemainingSeconds(now, now))
        assertEquals(300, CheatPass.runRemainingSeconds(now, now + wait))
        assertEquals(1, CheatPass.runRemainingSeconds(now, now + wait + duration - 1))
        assertEquals(0, CheatPass.runRemainingSeconds(now, now + wait + duration))
    }
}
