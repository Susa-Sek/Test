package de.trimbox.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SenderKeyTest {

    @Test
    fun `display name and address are separated`() {
        val sender = SenderKey.fromHeader("Netflix <no-reply@netflix.com>")

        assertEquals("no-reply@netflix.com", sender?.address)
        assertEquals("Netflix", sender?.displayName)
        assertEquals("netflix.com", sender?.domain)
    }

    @Test
    fun `the same sender in three spellings gives one key`() {
        val a = SenderKey.fromHeader("Netflix <no-reply@netflix.com>")
        val b = SenderKey.fromHeader("\"Netflix\" <No-Reply@Netflix.com>")
        val c = SenderKey.fromHeader("no-reply@NETFLIX.com")

        assertEquals(a?.address, b?.address)
        assertEquals(b?.address, c?.address)
    }

    @Test
    fun `quotes and commas in the name do not break the split`() {
        val sender = SenderKey.fromHeader("\"Müller, Anna\" <anna@example.org>")

        assertEquals("anna@example.org", sender?.address)
        assertEquals("Müller, Anna", sender?.displayName)
    }

    @Test
    fun `the old parenthesis form still works`() {
        val sender = SenderKey.fromHeader("post@example.org (Beispiel AG)")

        assertEquals("post@example.org", sender?.address)
        assertEquals("Beispiel AG", sender?.displayName)
    }

    @Test
    fun `a bare address has no display name`() {
        val sender = SenderKey.fromHeader("post@example.org")

        assertEquals("post@example.org", sender?.address)
        assertEquals("", sender?.displayName)
    }

    @Test
    fun `broken input yields nothing instead of an empty group`() {
        assertNull(SenderKey.fromHeader(null))
        assertNull(SenderKey.fromHeader(""))
        assertNull(SenderKey.fromHeader("   "))
        assertNull(SenderKey.fromHeader("kein-at-zeichen"))
        assertNull(SenderKey.fromHeader("<@example.org>"))
        assertNull(SenderKey.fromHeader("zwei@@example.org"))
        // Eine Domain ohne Punkt ist keine.
        assertNull(SenderKey.fromHeader("post@localhost"))
    }

    @Test
    fun `only the first of several senders counts`() {
        val sender = SenderKey.fromHeader("Erste <eine@example.org>, Zweite <andere@example.org>")

        assertEquals("eine@example.org", sender?.address)
    }

    @Test
    fun `a folded from header is read as one line`() {
        val sender = SenderKey.fromHeader("Beispiel AG\r\n <post@example.org>")

        assertEquals("post@example.org", sender?.address)
        assertEquals("Beispiel AG", sender?.displayName)
    }
}
