package de.klarzeit.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    private val minute = 60_000L

    @Test
    fun `hours and minutes`() {
        assertEquals("2h 14m", TimeFormat.short(134 * minute))
        assertEquals("1h 0m", TimeFormat.short(60 * minute))
        assertEquals("14m", TimeFormat.short(14 * minute))
        assertEquals("0m", TimeFormat.short(0))
    }

    @Test
    fun `seconds are rounded down, never up`() {
        // 89 Sekunden sind eine Minute. Eine Bildschirmzeit darf nie groesser
        // aussehen als sie war.
        assertEquals("1m", TimeFormat.short(89_000))
        assertEquals("0m", TimeFormat.short(59_000))
    }

    @Test
    fun `negative input does not produce nonsense`() {
        assertEquals("0m", TimeFormat.short(-5000))
    }

    @Test
    fun `the long form gets its singulars right`() {
        assertEquals("2 Stunden 14 Minuten", TimeFormat.long(134 * minute))
        assertEquals("1 Stunde 1 Minute", TimeFormat.long(61 * minute))
        assertEquals("0 Minuten", TimeFormat.long(0))
        assertEquals("45 Minuten", TimeFormat.long(45 * minute))
    }
}
