package de.shortblock.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {

    @Test
    fun `never repeats the previous line`() {
        val count = 6
        (0 until count).forEach { last ->
            (0 until 20).forEach { roll ->
                val picked = Reminders.pickIndex(count, last, roll)
                assertNotEquals("Spruch $last käme zweimal hintereinander", last, picked)
                assertTrue(picked in 0 until count)
            }
        }
    }

    /** Jeder andere Spruch muss erreichbar bleiben — sonst rotieren nur zwei Texte. */
    @Test
    fun `every other line stays reachable`() {
        val count = 4
        val seen = (0 until 30).map { Reminders.pickIndex(count, lastIndex = 2, roll = it) }.toSet()
        assertEquals(setOf(0, 1, 3), seen)
    }

    @Test
    fun `a single line does not break the rule`() {
        assertEquals(0, Reminders.pickIndex(count = 1, lastIndex = 0, roll = 0))
    }

    @Test
    fun `without a previous line any index is fine`() {
        assertEquals(3, Reminders.pickIndex(count = 6, lastIndex = -1, roll = 3))
    }

    @Test
    fun `no lines at all yields nothing`() {
        assertEquals(-1, Reminders.pickIndex(count = 0, lastIndex = -1, roll = 0))
    }
}
