package de.shortblock.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Obergrenze, die verhindert, dass ein Fehlalarm eine fremde App schliesst.
 *
 * Der Anlass war real: In v0.11.3 traf die zu weite `reel_`-Regel das Shorts-Regal in der
 * Empfehlungsliste unter normalen Videos. `blockAndGoBack` drückte im 800-ms-Takt Zurück, bis
 * YouTube zu war.
 */
class BackGuardTest {

    private val now = 1_800_000_000_000L

    // --- Kette oder Neuanfang? ---------------------------------------------------------

    @Test
    fun `the first back is never part of a chain`() {
        assertFalse(BackGuard.continuesChain(lastBackAtMs = 0L, nowMs = now))
    }

    @Test
    fun `a back right after the previous one continues the chain`() {
        assertTrue(BackGuard.continuesChain(lastBackAtMs = now - 900L, nowMs = now))
    }

    /** Nach einem richtigen Block ist man draussen; wieder hinein dauert länger als die Lücke. */
    @Test
    fun `a longer pause starts a new chain`() {
        val later = now + BackGuard.CHAIN_GAP_MS + 1
        assertFalse(BackGuard.continuesChain(lastBackAtMs = now, nowMs = later))
    }

    @Test
    fun `exactly at the gap it still counts as one chain`() {
        val later = now + BackGuard.CHAIN_GAP_MS
        assertTrue(BackGuard.continuesChain(lastBackAtMs = now, nowMs = later))
    }

    /** Zurückgestellte Systemuhr: neu anfangen statt die Kette endlos weiterlaufen zu lassen. */
    @Test
    fun `a rewound clock starts a new chain instead of running forever`() {
        assertFalse(BackGuard.continuesChain(lastBackAtMs = now, nowMs = now - 60_000L))
    }

    // --- Wann ist Schluss? --------------------------------------------------------------

    @Test
    fun `up to the limit the back is still allowed`() {
        assertFalse(BackGuard.isRunaway(0))
        assertFalse(BackGuard.isRunaway(1))
        assertFalse(BackGuard.isRunaway(2))
    }

    /** Hilft Zurück dreimal nicht, hilft das vierte auch nicht — es schliesst nur die App. */
    @Test
    fun `the fourth back in a row is refused`() {
        assertTrue(BackGuard.isRunaway(BackGuard.MAX_CONSECUTIVE))
        assertTrue(BackGuard.isRunaway(BackGuard.MAX_CONSECUTIVE + 5))
    }

    /**
     * Der Ablauf am Stück: drei Zurück dicht hintereinander, das vierte reisst die Kette.
     *
     * Bildet nach, was der Dienst in `blockAndGoBack` mit den beiden Funktionen macht.
     */
    @Test
    fun `three quick blocks are allowed, the fourth stops`() {
        var chain = 0
        var lastBackAt = 0L
        var stoppedAt = -1

        for (attempt in 1..4) {
            val at = now + attempt * 800L
            chain = if (BackGuard.continuesChain(lastBackAt, at)) chain + 1 else 0
            if (BackGuard.isRunaway(chain)) {
                stoppedAt = attempt
                break
            }
            lastBackAt = at
        }

        assertTrue("Der vierte Versuch muss abbrechen, nicht früher", stoppedAt == 4)
    }

    /** Und die Gegenprobe: gleiche Anzahl, aber mit Abstand — nie eine Kette. */
    @Test
    fun `blocks far apart never trip the guard`() {
        var chain = 0
        var lastBackAt = 0L

        repeat(10) { attempt ->
            val at = now + attempt * 30_000L
            chain = if (BackGuard.continuesChain(lastBackAt, at)) chain + 1 else 0
            assertFalse(BackGuard.isRunaway(chain))
            lastBackAt = at
        }
    }
}
