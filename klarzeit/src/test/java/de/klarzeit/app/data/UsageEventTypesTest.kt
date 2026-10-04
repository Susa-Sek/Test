package de.klarzeit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageEventTypesTest {

    @Test
    fun `the events that matter`() {
        assertEquals(UsageSessions.Type.FOREGROUND, UsageEventTypes.of(UsageEventTypes.ACTIVITY_RESUMED))
        assertEquals(UsageSessions.Type.BACKGROUND, UsageEventTypes.of(UsageEventTypes.ACTIVITY_PAUSED))
        assertEquals(
            UsageSessions.Type.SCREEN_OFF,
            UsageEventTypes.of(UsageEventTypes.SCREEN_NON_INTERACTIVE),
        )
        assertEquals(UsageSessions.Type.SCREEN_OFF, UsageEventTypes.of(UsageEventTypes.KEYGUARD_SHOWN))
        assertEquals(UsageSessions.Type.SCREEN_OFF, UsageEventTypes.of(UsageEventTypes.DEVICE_SHUTDOWN))
    }

    @Test
    fun `ACTIVITY_STOPPED is deliberately ignored`() {
        // Es kommt verspaetet, nachdem die naechste Ansicht derselben App schon laeuft.
        // Wer es auswertet, beendet die gerade offene Sitzung — das hat in v0.1.0 rund
        // ein Viertel der Bildschirmzeit gekostet.
        assertNull(UsageEventTypes.of(UsageEventTypes.ACTIVITY_STOPPED))
    }

    @Test
    fun `screen on alone says nothing about usage`() {
        // Der Bildschirm geht auch bei einer Benachrichtigung an, ohne dass jemand etwas
        // benutzt. Erst das Entsperren ist ein Griff zum Telefon.
        assertNull(UsageEventTypes.of(UsageEventTypes.SCREEN_INTERACTIVE))
    }

    @Test
    fun `unlocking is a grab`() {
        assertEquals(UsageSessions.Type.UNLOCK, UsageEventTypes.of(UsageEventTypes.KEYGUARD_HIDDEN))
    }

    @Test
    fun `unknown events are dropped instead of guessed`() {
        assertNull(UsageEventTypes.of(0))
        assertNull(UsageEventTypes.of(7))
        assertNull(UsageEventTypes.of(999))
    }
}
