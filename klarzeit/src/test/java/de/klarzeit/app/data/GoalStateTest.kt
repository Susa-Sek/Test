package de.klarzeit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalStateTest {

    private val hour = 3_600_000L
    private val goal = 2 * hour

    @Test
    fun `the three states`() {
        assertEquals(GoalState.Status.UNDER, GoalState.status(hour, goal))
        assertEquals(GoalState.Status.CLOSE, GoalState.status((1.7 * hour).toLong(), goal))
        assertEquals(GoalState.Status.OVER, GoalState.status(3 * hour, goal))
        // Genau auf dem Ziel ist schon drueber.
        assertEquals(GoalState.Status.OVER, GoalState.status(goal, goal))
    }

    @Test
    fun `without a goal there is nothing to exceed`() {
        assertEquals(GoalState.Status.UNDER, GoalState.status(10 * hour, 0))
        assertFalse(GoalState.shouldNotify(10 * hour, 0, "2026-09-06", null))
    }

    @Test
    fun `it warns once and then holds its tongue`() {
        assertTrue(GoalState.shouldNotify(3 * hour, goal, "2026-09-06", lastNotifiedDay = null))
        assertFalse(GoalState.shouldNotify(3 * hour, goal, "2026-09-06", lastNotifiedDay = "2026-09-06"))
        // Naechster Tag, neue Meldung.
        assertTrue(GoalState.shouldNotify(3 * hour, goal, "2026-09-07", lastNotifiedDay = "2026-09-06"))
    }

    @Test
    fun `below the goal it stays quiet`() {
        assertFalse(GoalState.shouldNotify(hour, goal, "2026-09-06", null))
    }

    @Test
    fun `the bar fills but never overflows`() {
        assertEquals(0.5f, GoalState.progress(hour, goal), 0.001f)
        assertEquals(1f, GoalState.progress(5 * hour, goal), 0.001f)
        assertEquals(0f, GoalState.progress(hour, 0), 0.001f)
    }
}
