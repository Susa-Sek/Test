package de.klarzeit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageWindowTest {

    private val midnight = 1_700_000_000_000L
    private val now = midnight + 9 * 60 * 60 * 1000L

    @Test
    fun `the query starts earlier than the calculation`() {
        // DER Test, der bei dem gefundenen Fehler rot gewesen waere: Bis v0.2.0 begann beides
        // um Mitternacht, und eine Sitzung von gestern Abend war damit unsichtbar.
        val window = UsageWindow.forDay(midnight, now)

        assertTrue(
            "Abfrage begann nicht vor dem Tag — Sitzungen ueber Mitternacht gehen verloren",
            window.queryStart < window.start,
        )
    }

    @Test
    fun `the calculation window is the day itself`() {
        val window = UsageWindow.forDay(midnight, now)

        assertEquals(midnight, window.start)
        assertEquals(now, window.end)
    }

    @Test
    fun `the lookbehind covers a session running past midnight`() {
        val window = UsageWindow.forDay(midnight, now)
        // Um 23:50 geoeffnet: Das Ereignis muss im Abfragefenster liegen.
        val openedLastNight = midnight - 10 * 60 * 1000L

        assertTrue(openedLastNight > window.queryStart)
    }
}
