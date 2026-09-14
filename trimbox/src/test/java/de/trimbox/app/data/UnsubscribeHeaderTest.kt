package de.trimbox.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsubscribeHeaderTest {

    private fun parse(header: String, post: String? = null) =
        UnsubscribeHeader.parse(listOf(header), post?.let { listOf(it) } ?: emptyList())

    @Test
    fun `both ways in one header are picked apart`() {
        val options = parse("<https://example.org/u/abc>, <mailto:unsub@example.org>")

        assertEquals("https://example.org/u/abc", options.httpsUrl)
        assertEquals("unsub@example.org", options.mailto?.address)
        assertFalse(options.isEmpty)
    }

    @Test
    fun `one-click needs the post header and an https address`() {
        val withPost = parse("<https://example.org/u/abc>", "List-Unsubscribe=One-Click")
        assertTrue(withPost.oneClickAllowed)
        assertEquals(
            UnsubscribeHeader.Route.OneClick("https://example.org/u/abc"),
            UnsubscribeHeader.route(withPost),
        )

        // Ohne die Zusage bleibt nur der Browser.
        val withoutPost = parse("<https://example.org/u/abc>")
        assertFalse(withoutPost.oneClickAllowed)
        assertEquals(
            UnsubscribeHeader.Route.OpenInBrowser("https://example.org/u/abc"),
            UnsubscribeHeader.route(withoutPost),
        )
    }

    @Test
    fun `one-click over plain http is refused`() {
        // RFC 8058 verlangt https. Ueber http traegt die Abmeldung eine Kennung im Klartext.
        val options = parse("<http://example.org/u/abc>", "List-Unsubscribe=One-Click")

        assertFalse(options.oneClickAllowed)
        assertEquals(
            UnsubscribeHeader.Route.OpenInBrowser("http://example.org/u/abc"),
            UnsubscribeHeader.route(options),
        )
    }

    @Test
    fun `the automatic mail beats the manual browser link`() {
        val options = parse("<https://example.org/u/abc>, <mailto:unsub@example.org>")

        val route = UnsubscribeHeader.route(options)
        assertTrue(route is UnsubscribeHeader.Route.SendMail)
        assertEquals("unsub@example.org", (route as UnsubscribeHeader.Route.SendMail).target.address)
    }

    @Test
    fun `a folded header survives the line break`() {
        val options = parse("<https://example.org/u/abc>,\r\n <mailto:unsub@example.org>")

        assertEquals("https://example.org/u/abc", options.httpsUrl)
        assertEquals("unsub@example.org", options.mailto?.address)
    }

    @Test
    fun `missing angle brackets do not lose the address`() {
        val options = parse("https://example.org/u/abc")

        assertEquals("https://example.org/u/abc", options.httpsUrl)
    }

    @Test
    fun `subject and body come out of the mailto query`() {
        val options = parse("<mailto:unsub@example.org?subject=Bitte%20abmelden&body=Danke>")

        assertEquals("unsub@example.org", options.mailto?.address)
        assertEquals("Bitte abmelden", options.mailto?.subject)
        assertEquals("Danke", options.mailto?.body)
    }

    @Test
    fun `a plus in the address stays a plus`() {
        // URLDecoder wuerde daraus ein Leerzeichen machen und die Kennung zerstoeren.
        val options = parse("<mailto:unsub+a1b2c3@example.org>")

        assertEquals("unsub+a1b2c3@example.org", options.mailto?.address)
    }

    @Test
    fun `umlauts survive percent decoding`() {
        val options = parse("<mailto:unsub@example.org?subject=Abmelden%20f%C3%BCr%20M%C3%BCller>")

        assertEquals("Abmelden für Müller", options.mailto?.subject)
    }

    @Test
    fun `case does not matter anywhere`() {
        val options = parse("<HTTPS://example.org/U/abc>", "list-unsubscribe=one-click")

        assertTrue(options.oneClickAllowed)
    }

    @Test
    fun `an empty or nonsense header offers no way out`() {
        assertTrue(parse("").isEmpty)
        assertTrue(parse("   ").isEmpty)
        assertEquals(UnsubscribeHeader.Route.None, UnsubscribeHeader.route(parse("")))
        assertEquals(UnsubscribeHeader.Route.None, UnsubscribeHeader.route(parse("<>")))
        // Ein mailto ohne @ ist kein Ziel.
        assertNull(parse("<mailto:kaputt>").mailto)
    }

    @Test
    fun `the first https wins when several are offered`() {
        val options = parse("<https://a.example.org/1>, <https://b.example.org/2>")

        assertEquals("https://a.example.org/1", options.httpsUrl)
    }
}
