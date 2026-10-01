package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Eskalation gegen den „Für dich“-Feed.
 *
 * Der wichtigste Test steht unten unter „Echo“: Ohne dieses Gatter bremst die App gegen ihr
 * eigenes Bremsen, und Instagram lässt sich gar nicht mehr bedienen.
 */
class FeedGuardTest {

    private val now = 1_800_000_000_000L

    // --- Reihenfolge: erst sanft, dann hart ---------------------------------------------

    @Test
    fun `with a tap target the tap comes first`() {
        assertEquals(
            FeedGuard.Step.TAP_FOLLOWING,
            FeedGuard.next(tapTargetFound = true, tapsTried = 0, brakesUsed = 0),
        )
    }

    /** Ohne Tippziel — Instagram zeigt die alte Kopfzeile — beginnt es bei der Bremse. */
    @Test
    fun `without a tap target it starts at the brake`() {
        assertEquals(
            FeedGuard.Step.BRAKE,
            FeedGuard.next(tapTargetFound = false, tapsTried = 0, brakesUsed = 0),
        )
    }

    /** Greift der Tipp zweimal nicht, greift er hier nicht. Dann übernimmt die Bremse. */
    @Test
    fun `after the tap limit the brake takes over`() {
        assertEquals(
            FeedGuard.Step.BRAKE,
            FeedGuard.next(tapTargetFound = true, tapsTried = FeedGuard.TAP_LIMIT, brakesUsed = 0),
        )
    }

    @Test
    fun `after the brake limit instagram is closed`() {
        assertEquals(
            FeedGuard.Step.LEAVE,
            FeedGuard.next(
                tapTargetFound = false,
                tapsTried = 0,
                brakesUsed = FeedGuard.BRAKE_LIMIT,
            ),
        )
    }

    /** Der ganze Weg am Stück — kein Sprung über eine Stufe. */
    @Test
    fun `the whole escalation runs gentle to hard`() {
        val steps = (0..FeedGuard.BRAKE_LIMIT).map { brakes ->
            FeedGuard.next(tapTargetFound = false, tapsTried = 0, brakesUsed = brakes)
        }

        assertTrue(steps.dropLast(1).all { it == FeedGuard.Step.BRAKE })
        assertEquals(FeedGuard.Step.LEAVE, steps.last())
    }

    // --- Echo: der Test gegen die Endlosschleife -----------------------------------------

    /**
     * Ein Zurück-Scroll erzeugt selbst ein Scroll-Ereignis.
     *
     * Ohne dieses Gatter bremst die App dagegen, das erzeugt wieder ein Ereignis, und so fort —
     * Instagram wäre unbedienbar. Genau diese Sorte Schleife hat in v0.11.4 schon einmal eine
     * ganze App zugedrückt.
     */
    @Test
    fun `our own scroll back is recognised as an echo`() {
        assertTrue(FeedGuard.isEcho(lastBrakeAtMs = now, nowMs = now))
        assertTrue(FeedGuard.isEcho(lastBrakeAtMs = now, nowMs = now + 100L))
        assertTrue(FeedGuard.isEcho(lastBrakeAtMs = now, nowMs = now + FeedGuard.ECHO_MS))
    }

    @Test
    fun `a later scroll is the user, not an echo`() {
        assertFalse(FeedGuard.isEcho(lastBrakeAtMs = now, nowMs = now + FeedGuard.ECHO_MS + 1))
        assertFalse(FeedGuard.isEcho(lastBrakeAtMs = now, nowMs = now + 5_000L))
    }

    @Test
    fun `without a previous brake nothing is an echo`() {
        assertFalse(FeedGuard.isEcho(lastBrakeAtMs = 0L, nowMs = now))
    }

    /**
     * Zurückgestellte Systemuhr: **kein** Echo.
     *
     * Die Gegenrichtung zu [CheatPass] und [BackGuard], und zwar mit Absicht: Eine Bremse, die
     * sich für ihr eigenes Echo hält, bremst nie wieder. Hier ist die sichere Seite „bremsen“.
     */
    @Test
    fun `a rewound clock does not silence the brake`() {
        assertFalse(FeedGuard.isEcho(lastBrakeAtMs = now, nowMs = now - 60_000L))
    }
}
