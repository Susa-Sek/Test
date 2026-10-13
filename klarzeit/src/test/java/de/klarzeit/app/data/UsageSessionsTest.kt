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
    fun `a late background event of the same app does not kill the running session`() {
        // Der Ablauf, der v0.1.0 rund ein Viertel der Zeit gekostet hat: YouTube wechselt
        // von der Liste in den Player, und Androids STOPPED der Liste trifft erst ein,
        // wenn der Player laengst laeuft. Wuerde es als "im Hintergrund" gelten, endete
        // die Sitzung hier — und die 25 Minuten Video danach zaehlten nicht mehr.
        //
        // Deshalb wertet UsageEventTypes ACTIVITY_STOPPED gar nicht erst aus; hier steht
        // nur noch das, was uebrig bleibt: PAUSED, dann sofort wieder RESUMED.
        val result = compute(
            listOf(
                fg("youtube", 0),
                bg("youtube", 5),
                fg("youtube", 5),
            ),
            endMinutes = 30,
        )

        assertEquals(30 * minute, result["youtube"])
    }

    @Test
    fun `going to the home screen ends the session even without a pause`() {
        // Ohne STOPPED braucht es einen anderen Abschluss fuer Apps, die kein PAUSED
        // schicken. Der Startbildschirm ist selbst eine App und liefert ihn.
        val result = compute(
            listOf(fg("youtube", 0), fg("launcher", 20)),
            endMinutes = 30,
        )

        assertEquals(20 * minute, result["youtube"])
        assertEquals(10 * minute, result["launcher"])
    }

    @Test
    fun `a double foreground event does not double count`() {
        val result = compute(listOf(fg("insta", 10), fg("insta", 10), bg("insta", 25)), endMinutes = 60)

        assertEquals(15 * minute, result["insta"])
    }
}

/**
 * Griffe und Entsperrungen. Minuten allein sagen wenig — vierzig Griffe zu je zwei Minuten
 * fühlen sich anders an als zwei zu je vierzig.
 */
class UsageGrabsTest {

    private val midnight = 1_000_000_000L
    private val minute = 60_000L
    private fun at(minutes: Long) = midnight + minutes * minute

    private fun fg(pkg: String, m: Long) = UsageSessions.Event(pkg, UsageSessions.Type.FOREGROUND, at(m))
    private fun bg(pkg: String, m: Long) = UsageSessions.Event(pkg, UsageSessions.Type.BACKGROUND, at(m))
    private fun off(m: Long) = UsageSessions.Event("android", UsageSessions.Type.SCREEN_OFF, at(m))
    private fun unlock(m: Long) = UsageSessions.Event("android", UsageSessions.Type.UNLOCK, at(m))

    private fun analyse(events: List<UsageSessions.Event>, endMinutes: Long = 120) =
        UsageSessions.analyse(events, midnight, at(endMinutes))

    @Test
    fun `switching back and forth counts as two grabs`() {
        val usage = analyse(listOf(fg("insta", 0), fg("maps", 10), fg("insta", 20), bg("insta", 30)))

        assertEquals(2, usage.opens["insta"])
        assertEquals(1, usage.opens["maps"])
    }

    @Test
    fun `a rotation is not a second grab`() {
        // Zwei RESUMED derselben App hintereinander — etwa beim Drehen des Bildschirms.
        val usage = analyse(listOf(fg("insta", 0), fg("insta", 0), bg("insta", 10)))

        assertEquals(1, usage.opens["insta"])
    }

    @Test
    fun `picking the phone up again is a new grab`() {
        val usage = analyse(listOf(fg("insta", 0), off(5), fg("insta", 60), bg("insta", 70)))

        assertEquals(2, usage.opens["insta"])
        // Und die Zeit dazwischen zaehlt nicht mit.
        assertEquals(15 * minute, usage.foregroundMillis["insta"])
    }

    @Test
    fun `unlocks are counted, screen-on alone is not`() {
        val usage = analyse(listOf(unlock(10), unlock(60), fg("insta", 61), bg("insta", 65)))

        assertEquals(2, usage.unlocks)
    }

    @Test
    fun `events from the lookbehind count in time but not in grabs`() {
        // Die Sitzung von gestern Abend zaehlt ab Mitternacht in die Zeit — aber der Griff
        // passierte gestern und gehoert nicht in die heutige Haeufigkeit.
        val usage = UsageSessions.analyse(
            listOf(
                UsageSessions.Event("insta", UsageSessions.Type.FOREGROUND, midnight - 30 * minute),
                UsageSessions.Event("android", UsageSessions.Type.UNLOCK, midnight - 31 * minute),
                bg("insta", 20),
            ),
            windowStart = midnight,
            windowEnd = at(120),
        )

        assertEquals(20 * minute, usage.foregroundMillis["insta"])
        assertEquals(null, usage.opens["insta"])
        assertEquals(0, usage.unlocks)
    }

    @Test
    fun `grabs reach the summary`() {
        val usage = analyse(listOf(fg("insta", 0), fg("maps", 10), fg("insta", 20), bg("insta", 30)))
        val summary = UsageSessions.summarize(
            perPackage = usage.foregroundMillis,
            excluded = emptySet(),
            opens = usage.opens,
        )

        assertEquals(2, summary.apps.first { it.packageName == "insta" }.opens)
    }
}
