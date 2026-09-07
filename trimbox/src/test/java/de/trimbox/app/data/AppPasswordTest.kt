package de.trimbox.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPasswordTest {

    @Test
    fun `a pasted google app password loses its spaces`() {
        // So zeigt Google es an, und so landet es beim Einfuegen im Feld.
        assertEquals("abcdefghijklmnop", AppPassword.normalize("abcd efgh ijkl mnop"))
        assertEquals("a1b2c3d4e5f6g7h8", AppPassword.normalize("a1b2 c3d4 e5f6 g7h8"))
    }

    @Test
    fun `surrounding whitespace always goes`() {
        assertEquals("geheim", AppPassword.normalize("  geheim  "))
        assertEquals("abcdefghijklmnop", AppPassword.normalize("  abcd efgh ijkl mnop \n"))
    }

    @Test
    fun `a real password with a space keeps it`() {
        // Nur das Vierergruppen-Muster wird angefasst, sonst nichts.
        assertEquals("mein geheimes Wort", AppPassword.normalize("mein geheimes Wort"))
        assertEquals("correct horse battery staple", AppPassword.normalize("correct horse battery staple"))
        // Vier Gruppen, aber nicht sechzehn Zeichen: nicht das Anbietermuster.
        assertEquals("abc def ghi jkl", AppPassword.normalize("abc def ghi jkl"))
        // Sechzehn Zeichen, aber anders gruppiert.
        assertEquals("abcde fghij klmnop", AppPassword.normalize("abcde fghij klmnop"))
    }

    @Test
    fun `passwords without spaces come back untouched`() {
        assertEquals("abcdefghijklmnop", AppPassword.normalize("abcdefghijklmnop"))
        assertEquals("P@ssw0rd!", AppPassword.normalize("P@ssw0rd!"))
        assertEquals("", AppPassword.normalize(""))
    }

    @Test
    fun `dashes are not spaces`() {
        // Apple zeigt seine Passwoerter mit Bindestrichen; die gehoeren zum Passwort.
        assertEquals("abcd-efgh-ijkl-mnop", AppPassword.normalize("abcd-efgh-ijkl-mnop"))
    }
}
