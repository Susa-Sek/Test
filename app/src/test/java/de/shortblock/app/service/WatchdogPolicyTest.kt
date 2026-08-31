package de.shortblock.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchdogPolicyTest {

    @Test
    fun `warns once when the service disappears after having run`() {
        assertTrue(WatchdogPolicy.shouldWarn(everEnabled = true, enabledNow = false, alreadyWarned = false))
    }

    @Test
    fun `does not repeat the warning`() {
        assertFalse(WatchdogPolicy.shouldWarn(everEnabled = true, enabledNow = false, alreadyWarned = true))
    }

    /** Frisch installiert und noch nicht eingerichtet ist kein Fehler, sondern der Normalfall. */
    @Test
    fun `stays quiet before the service ever ran`() {
        assertFalse(WatchdogPolicy.shouldWarn(everEnabled = false, enabledNow = false, alreadyWarned = false))
    }

    @Test
    fun `stays quiet while the service is running`() {
        assertFalse(WatchdogPolicy.shouldWarn(everEnabled = true, enabledNow = true, alreadyWarned = false))
    }
}
