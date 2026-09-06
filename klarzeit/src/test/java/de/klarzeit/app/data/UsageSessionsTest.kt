package de.klarzeit.app.data

import de.klarzeit.app.data.UsageSessions.Event
import de.klarzeit.app.data.UsageSessions.Type
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSessionsTest {

    private val midnight = 1_000_000_000L
    private val minute = 60_000L
    private fun at(minutes: Long) = midnight + minutes * minute

    private fun fg(pkg: String, minutes: Long) = Event(pkg, Type.FOREGROUND, at(minutes))
    private fun bg(pkg: String, minutes: Long) = Event(pkg, Type.BACKGROUND, at(minutes))
    private fun off(minutes: Long) = Event("android", Type.SCREEN_OFF, at(minutes))

    private fun compute(events: List<Event>, endMinutes: Long) =
        UsageSessions.foregroundMillis(events, midnight, at(endMinutes))

    @Test
    fun `a plain session is counted`() {
        val result = compute(listOf(fg("insta", 10), bg("insta", 25)), endMinutes = 60)

        assertEquals(15 * minute, result["insta"])
    }

    @Test
    fun `the app still open right now runs until the window ends`() {
        // Ohne diesen Fall fehlte genau die Sitzung, die man gerade sehen will.
        val result = compute(listOf(fg("insta", 10)), endMinutes = 40)

        assertEquals(30 * minute, result["insta"])
    }

    @Test
    fun `a session started yesterday counts from midnight`() {
        // Vordergrund-Ereignis vor dem Fenster: nur der Teil ab Fensterbeginn zaehlt.
        val result = UsageSessions.foregroundMillis(
            listOf(Event("insta", Type.FOREGROUND, midnight - 30 * minute), bg("insta", 20)),
            windowStart = midnight,
            windowEnd = at(60),
        )

        assertEquals(20 * minute, result["insta"])
    }

    @Test
    fun `screen off ends the session`() {
        // Ohne SCREEN_OFF liefe die App die ganze Nacht weiter.
        val result = compute(listOf(fg("insta", 10), off(25)), endMinutes = 480)

        assertEquals(15 * minute, result["insta"])
    }

    @Test
    fun `a switch without a pause closes the previous app`() {
        // Android schickt nicht zuverlaessig ein Hintergrund-Ereignis vor dem Wechsel.
        val result = compute(listOf(fg("insta", 0), fg("maps", 10), bg("maps", 30)), endMinutes = 60)

        assertEquals(10 * minute, result["insta"])
        assertEquals(20 * minute, result["maps"])
    }

    @Test
    fun `a background event for an app that is not open changes nothing`() {
        val result = compute(
            listOf(fg("insta", 10), bg("maps", 15), bg("insta", 25)),
            endMinutes = 60,
        )

        assertEquals(15 * minute, result["insta"])
        assertEquals(null, result["maps"])
    }

    @Test
    fun `several sessions of the same app add up`() {
        val result = compute(
            listOf(
                fg("insta", 0), bg("insta", 10),
                fg("insta", 30), bg("insta", 45),
            ),
            endMinutes = 60,
        )

        assertEquals(25 * minute, result["insta"])
    }

    @Test
    fun `events out of order are sorted first`() {
        val result = compute(listOf(bg("insta", 25), fg("insta", 10)), endMinutes = 60)

        assertEquals(15 * minute, result["insta"])
    }

    @Test
    fun `events after the window are ignored`() {
        val result = compute(listOf(fg("insta", 10), bg("insta", 20), fg("maps", 90)), endMinutes = 60)

        assertEquals(10 * minute, result["insta"])
        assertEquals(null, result["maps"])
    }

    @Test
    fun `nothing at all is an empty map, not a crash`() {
        assertTrue(compute(emptyList(), endMinutes = 60).isEmpty())
        assertTrue(UsageSessions.foregroundMillis(emptyList(), midnight, midnight).isEmpty())
        // Ein rueckwaerts laufendes Fenster darf keine negative Zeit erzeugen.
        assertTrue(UsageSessions.foregroundMillis(listOf(fg("insta", 5)), at(60), midnight).isEmpty())
    }

    @Test
    fun `a double foreground event does not double count`() {
        val result = compute(listOf(fg("insta", 10), fg("insta", 10), bg("insta", 25)), endMinutes = 60)

        assertEquals(15 * minute, result["insta"])
    }
}
