package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun next(
        tapTargetFound: Boolean = false,
        tapsTried: Int = 0,
        brakesUsed: Int = 0,
        withinFreePosts: Boolean = false,
        inCooldown: Boolean = false,
    ) = FeedGuard.next(tapTargetFound, tapsTried, brakesUsed, withinFreePosts, inCooldown)

    @Test
    fun `with a tap target the tap comes first`() {
        assertEquals(FeedGuard.Step.TAP_FOLLOWING, next(tapTargetFound = true))
    }

    /**
     * Getippt wird auch **während** der freien Beiträge.
     *
     * Auf „Gefolgt" umzuschalten löst das Problem, statt es zu begrenzen — das ist der beste
     * Ausgang, und darauf zu warten, bis die Freigabe aufgebraucht ist, wäre verschenkt.
     */
    @Test
    fun `the tap is allowed during the free posts`() {
        assertEquals(
            FeedGuard.Step.TAP_FOLLOWING,
            next(tapTargetFound = true, withinFreePosts = true),
        )
    }

    /** Ohne Tippziel — Instagram zeigt die alte Kopfzeile — beginnt es bei der Bremse. */
    @Test
    fun `without a tap target it starts at the brake`() {
        assertEquals(FeedGuard.Step.BRAKE, next())
    }

    /** Solange frei gescrollt werden darf, passiert ohne Tippziel gar nichts. */
    @Test
    fun `within the free posts nothing happens`() {
        assertNull(next(withinFreePosts = true))
    }

    /** Greift der Tipp zweimal nicht, greift er hier nicht. Dann übernimmt die Bremse. */
    @Test
    fun `after the tap limit the brake takes over`() {
        assertEquals(
            FeedGuard.Step.BRAKE,
            next(tapTargetFound = true, tapsTried = FeedGuard.TAP_LIMIT),
        )
    }

    @Test
    fun `after the brake limit the feed is closed`() {
        assertEquals(FeedGuard.Step.HARD_STOP, next(brakesUsed = FeedGuard.BRAKE_LIMIT))
    }

    /**
     * Die Abklingzeit: keine sanfte Stufe mehr.
     *
     * Ohne das wäre der harte Stopp wertlos — Instagram neu öffnen, und die freien Beiträge
     * begännen beliebig oft von vorn. Dieselbe Lücke hatte die Ausnahme „einmal ansehen", weil
     * ein Scan ohne Treffer ihren Zustand zurücksetzte.
     */
    @Test
    fun `during the cooldown the brake is skipped`() {
        assertEquals(FeedGuard.Step.HARD_STOP, next(inCooldown = true))
        assertEquals(
            FeedGuard.Step.HARD_STOP,
            next(brakesUsed = 0, inCooldown = true),
        )
    }

    /**
     * Aber die Freigabe liegt **über** der Abklingzeit.
     *
     * Wer Instagram für eine Nachricht öffnet, soll auch in der Abklingzeit durchkommen. Der
     * Startfeed steht dabei zwangsläufig kurz vorne; ihn dafür sofort zuzumachen wäre genau
     * der Fehlalarm, der teurer ist als die Lücke.
     */
    @Test
    fun `the free posts survive the cooldown`() {
        assertNull(next(withinFreePosts = true, inCooldown = true))
    }

    /** Der ganze Weg am Stück — kein Sprung über eine Stufe. */
    @Test
    fun `the whole escalation runs gentle to hard`() {
        val steps = (0..FeedGuard.BRAKE_LIMIT).map { brakes -> next(brakesUsed = brakes) }

        assertTrue(steps.dropLast(1).all { it == FeedGuard.Step.BRAKE })
        assertEquals(FeedGuard.Step.HARD_STOP, steps.last())
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

/**
 * Die Freigabe: ein, zwei Beiträge laufen durch, dann greift die Bremse.
 *
 * Gezählt wird der Listenindex aus dem Scroll-Ereignis — buchstäblich die Nummer des Beitrags,
 * nicht eine geschätzte Scroll-Strecke.
 */
class FeedGuardFreePostsTest {

    @Test
    fun `the first posts are free`() {
        assertTrue(FeedGuard.isWithinFreePosts(0, scrollEvents = 0))
        assertTrue(FeedGuard.isWithinFreePosts(1, scrollEvents = 99))
    }

    @Test
    fun `after that the brake engages`() {
        assertFalse(FeedGuard.isWithinFreePosts(FeedGuard.FREE_POSTS, scrollEvents = 0))
        assertFalse(FeedGuard.isWithinFreePosts(FeedGuard.FREE_POSTS + 5, scrollEvents = 0))
    }

    /**
     * Ein gemeldeter Index schlägt den Rückfall — in **beide** Richtungen.
     *
     * Sonst stünde die Freigabe bei einer Liste, die ihren Index meldet, nach vier Ereignissen
     * zu, obwohl noch der erste Beitrag zu sehen ist (ein Fling erzeugt mehrere Ereignisse).
     */
    @Test
    fun `a reported index beats the fallback`() {
        assertTrue(FeedGuard.isWithinFreePosts(0, scrollEvents = 500))
        assertFalse(FeedGuard.isWithinFreePosts(9, scrollEvents = 0))
    }

    /**
     * Der Rückfall ohne Index — und die stille Falle, die er schliesst.
     *
     * Bis v0.15.1 hiess „kein Index" **immer** „noch frei". Meldet eine Liste nie einen Index
     * — und das tun Listen ausserhalb von `RecyclerView` regelmässig —, war die Freigabe damit
     * dauerhaft offen und die Bremse griff kein einziges Mal. Dieselbe Sorte Bedingung wie
     * `yt_shorts_player`: auf dem Papier richtig, auf dem Gerät nie zutreffend.
     */
    @Test
    fun `without an index the scroll count takes over`() {
        assertTrue(FeedGuard.isWithinFreePosts(-1, scrollEvents = 0))
        assertTrue(FeedGuard.isWithinFreePosts(-1, scrollEvents = FeedGuard.FREE_SCROLLS - 1))
        assertFalse(FeedGuard.isWithinFreePosts(-1, scrollEvents = FeedGuard.FREE_SCROLLS))
        assertFalse(FeedGuard.isWithinFreePosts(-1, scrollEvents = 100))
    }
}
