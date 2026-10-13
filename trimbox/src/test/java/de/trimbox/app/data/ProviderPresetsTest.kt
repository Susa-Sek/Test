package de.trimbox.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPresetsTest {

    @Test
    fun `known providers are filled in`() {
        val gmail = ProviderPresets.forAddress("jemand@gmail.com")
        assertEquals("imap.gmail.com", gmail?.imapHost)
        assertEquals(993, gmail?.imapPort)
        assertEquals("smtp.gmail.com", gmail?.smtpHost)
        assertEquals(587, gmail?.smtpPort)
        assertTrue(gmail?.smtpStartTls == true)

        assertEquals("imap.gmx.net", ProviderPresets.forAddress("jemand@gmx.de")?.imapHost)
        assertEquals("posteo.de", ProviderPresets.forAddress("jemand@posteo.de")?.imapHost)
    }

    @Test
    fun `t-online uses TLS from the first byte`() {
        val preset = ProviderPresets.forAddress("jemand@t-online.de")

        assertEquals(465, preset?.smtpPort)
        assertFalse(preset?.smtpStartTls == true)
    }

    @Test
    fun `capitals and spaces in the address do not matter`() {
        assertEquals("imap.gmail.com", ProviderPresets.forAddress("  Jemand@GMail.com ")?.imapHost)
    }

    @Test
    fun `an unknown provider is left blank instead of guessed wrong`() {
        assertNull(ProviderPresets.forAddress("jemand@sehr-eigene-domain.de"))
        assertNull(ProviderPresets.forAddress("keine-adresse"))
    }

    @Test
    fun `microsoft accounts are flagged, not silently attempted`() {
        assertTrue(ProviderPresets.needsOAuth("jemand@outlook.de"))
        assertTrue(ProviderPresets.needsOAuth("jemand@hotmail.com"))
        assertFalse(ProviderPresets.needsOAuth("jemand@gmail.com"))
    }
}
