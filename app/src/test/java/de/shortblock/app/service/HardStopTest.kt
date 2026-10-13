package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der harte Stopp und seine Abklingzeit.
 *
 * Die Abklingzeit ist der eigentliche Gehalt: Ein Stopp ohne sie ist wertlos, weil Instagram
 * neu zu öffnen die freien Beiträge zurücksetzt — beliebig oft.
 */
class HardStopTest {

    private val now = 1_800_000_000_000L

    // --- Mittel ------------------------------------------------------------------------

    @Test
    fun `with the permission the block screen is used`() {
        assertEquals(HardStop.Means.BLOCK_SCREEN, HardStop.means(canShowBlockScreen = true))
    }

    /**
     * Ohne „Über anderen Apps anzeigen" bleibt die Home-Taste.
     *
     * Wichtig ist, dass es diesen Rückfall **gibt**: Eine Erkennung, die ohne Berechtigung
     * still nichts tut, ist der stillste aller Fehler — davon hatte diese App genug.
     */
    @Test
    fun `without the permission it falls back to home`() {
        assertEquals(HardStop.Means.GO_HOME, HardStop.means(canShowBlockScreen = false))
    }

    // --- Abklingzeit -------------------------------------------------------------------

    @Test
    fun `right after a stop the cooldown runs`() {
        assertTrue(HardStop.isInCooldown(now, now))
        assertTrue(HardStop.isInCooldown(now, now + HardStop.COOLDOWN_MS - 1))
    }

    @Test
    fun `after the cooldown the feed is free again`() {
        assertFalse(HardStop.isInCooldown(now, now + HardStop.COOLDOWN_MS))
        assertFalse(HardStop.isInCooldown(now, now + 2 * HardStop.COOLDOWN_MS))
    }

    @Test
    fun `without a stop there is no cooldown`() {
        assertFalse(HardStop.isInCooldown(0L, now))
    }

    /**
     * Zurückgestellte Uhr: Die Abklingzeit gilt als **abgelaufen**.
     *
     * Dieselbe Richtung wie bei `GuardianLock.remainingLockoutSeconds` und `CheatPass` — und
     * bewusst die andere als bei `FeedGuard.isEcho`. Dort schützt „kein Echo" die Bremse; hier
     * würde eine Sperre, die sich durch eine verstellte Uhr verlängert, irgendwann jemanden
     * aussperren.
     */
    @Test
    fun `a rewound clock ends the cooldown instead of extending it`() {
        assertFalse(HardStop.isInCooldown(now, now - 60_000L))
    }

    // --- Restzeit für die Anzeige ------------------------------------------------------

    @Test
    fun `the remaining minutes are rounded up`() {
        assertEquals(10, HardStop.remainingMinutes(now, now))
        assertEquals(9, HardStop.remainingMinutes(now, now + 60_000L))
        // 30 Sekunden Rest sind "1 Minute", nicht "0" — sonst stünde auf dem Bildschirm, der
        // Feed sei schon frei, während er noch zu ist.
        assertEquals(1, HardStop.remainingMinutes(now, now + HardStop.COOLDOWN_MS - 30_000L))
    }

    @Test
    fun `outside the cooldown nothing remains`() {
        assertEquals(0, HardStop.remainingMinutes(now, now + HardStop.COOLDOWN_MS))
        assertEquals(0, HardStop.remainingMinutes(0L, now))
    }
}
