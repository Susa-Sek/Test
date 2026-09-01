package de.shortblock.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheatPhraseTest {

    private val expected = "Das hier ist eine Entscheidung, kein Reflex."

    @Test
    fun `the exact sentence passes`() {
        assertTrue(CheatPhrase.matches(expected, expected))
    }

    /** Die Hürde soll die Gedankenlosigkeit treffen, nicht die Feinmotorik. */
    @Test
    fun `case and extra whitespace are forgiven`() {
        assertTrue(CheatPhrase.matches("  das hier IST eine   Entscheidung,  kein reflex. ", expected))
    }

    @Test
    fun `a missing word does not pass`() {
        assertFalse(CheatPhrase.matches("Das hier ist eine Entscheidung, kein.", expected))
    }

    /** Ohne Satzzeichen schrumpfte der Satz beim Tippen unbemerkt — und die Hürde mit ihm. */
    @Test
    fun `punctuation counts`() {
        assertFalse(CheatPhrase.matches("Das hier ist eine Entscheidung kein Reflex", expected))
    }

    @Test
    fun `empty input never passes`() {
        assertFalse(CheatPhrase.matches("", expected))
        assertFalse(CheatPhrase.matches("   ", expected))
        assertFalse(CheatPhrase.matches("", ""))
    }
}
