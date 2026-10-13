package de.shortblock.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Partnersperre.
 *
 * Der wichtigste Gedanke beim Lesen dieser Tests: Diese Sperre kann jemanden **aussperren**.
 * Deshalb steht hier nicht nur „richtiges Passwort passt", sondern vor allem, was bei kaputten
 * Daten passiert — nämlich `false` statt einer Ausnahme.
 */
class GuardianLockTest {

    // Wenige Runden, damit die Tests schnell bleiben. Geprüft wird die Mechanik, nicht die
    // Rechenzeit; die echte Rundenzahl steht in GuardianLock.ITERATIONS.
    private val fastSalt = ByteArray(16) { it.toByte() }

    private fun store(password: String) = GuardianLock.store(password, fastSalt, iterations = 1_000)

    // --- Passwort ----------------------------------------------------------------------

    @Test
    fun `the right password fits`() {
        val stored = store("Rosenmontag!7")
        assertTrue(GuardianLock.verify("Rosenmontag!7", stored))
    }

    @Test
    fun `a wrong password does not`() {
        val stored = store("Rosenmontag!7")
        assertFalse(GuardianLock.verify("rosenmontag!7", stored))
        assertFalse(GuardianLock.verify("Rosenmontag!", stored))
        assertFalse(GuardianLock.verify("", stored))
    }

    /**
     * Das Salz tut seine Arbeit.
     *
     * Ohne eigenes Salz je Sperre ergäbe dasselbe Passwort überall denselben Hash — dann verrät
     * schon der Vergleich zweier Geräte, dass dieselbe Person dasselbe gesetzt hat.
     */
    @Test
    fun `the same password stored twice gives different values`() {
        val first = GuardianLock.store("gleiches Passwort")
        val second = GuardianLock.store("gleiches Passwort")

        assertNotEquals(first, second)
        assertTrue(GuardianLock.verify("gleiches Passwort", first))
        assertTrue(GuardianLock.verify("gleiches Passwort", second))
    }

    @Test
    fun `the stored value carries its own iteration count`() {
        val stored = store("egal")
        assertEquals("1000", stored.substringBefore(':'))
        // Und es lässt sich weiterhin prüfen, obwohl ITERATIONS inzwischen höher steht.
        assertTrue(GuardianLock.verify("egal", stored))
    }

    // --- Kaputte Daten dürfen nicht aussperren ------------------------------------------

    /**
     * Der Fall, der niemandem passieren darf: Die App stürzt beim Entsperren ab, und damit
     * kommt niemand mehr an die Einstellungen, um die Sperre zu lösen.
     */
    @Test
    fun `a damaged stored value refuses instead of throwing`() {
        val kaputt = listOf(
            "",
            "kein Doppelpunkt",
            "abc:def",
            "nichtnumerisch:AAAA:BBBB",
            "0:AAAA:BBBB",
            "-5:AAAA:BBBB",
            "1000::",
            "1000:!!!keinbase64!!!:BBBB",
            "1000:AAAA:BBBB:zuviel",
        )

        for (wert in kaputt) {
            assertFalse("„$wert“ darf nur false geben, nie werfen", GuardianLock.verify("x", wert))
        }
    }

    // --- Wiederherstellungscode ---------------------------------------------------------

    /**
     * Keine verwechselbaren Zeichen.
     *
     * Der Code wird von Papier abgetippt, oft von jemand anderem. Eine Null, die als O gelesen
     * wird, macht den Notausgang unbenutzbar — und der Notausgang ist das Einzige, was zwischen
     * einem verlorenen Passwort und einem unbrauchbaren Telefon steht.
     */
    @Test
    fun `the recovery code has no confusable characters`() {
        repeat(200) {
            val code = GuardianLock.newRecoveryCode()
            for (verwechselbar in listOf('0', 'O', '1', 'I', 'L')) {
                assertFalse("$code enthält $verwechselbar", code.contains(verwechselbar))
            }
        }
    }

    @Test
    fun `the recovery code is grouped and long enough`() {
        val code = GuardianLock.newRecoveryCode()
        val groups = code.split("-")

        assertEquals(5, groups.size)
        assertTrue(groups.all { it.length == 4 })
        assertEquals(20, GuardianLock.normalizeCode(code).length)
    }

    @Test
    fun `two codes are not the same`() {
        val codes = (1..50).map { GuardianLock.newRecoveryCode() }.toSet()
        assertEquals(50, codes.size)
    }

    /** Wer abtippt, tippt anders: klein, mit Leerzeichen, mit einem zu viel am Ende. */
    @Test
    fun `the code survives being typed by hand`() {
        val code = "ABCD-EFGH-JKMN-PQRS-TUVW"
        val stored = GuardianLock.storeCode(code, fastSalt)

        assertTrue(GuardianLock.verifyCode(code, stored))
        assertTrue(GuardianLock.verifyCode("abcd-efgh-jkmn-pqrs-tuvw", stored))
        assertTrue(GuardianLock.verifyCode("ABCD EFGH JKMN PQRS TUVW", stored))
        assertTrue(GuardianLock.verifyCode("  abcdefghjkmnpqrstuvw  ", stored))
    }

    @Test
    fun `a wrong code stays wrong`() {
        val stored = GuardianLock.storeCode("ABCD-EFGH-JKMN-PQRS-TUVW", fastSalt)

        assertFalse(GuardianLock.verifyCode("ABCD-EFGH-JKMN-PQRS-TUVX", stored))
        assertFalse(GuardianLock.verifyCode("", stored))
    }

    // --- Bremse gegen Raten --------------------------------------------------------------

    @Test
    fun `the first three attempts are free`() {
        assertEquals(0, GuardianLock.lockoutSeconds(0))
        assertEquals(0, GuardianLock.lockoutSeconds(1))
        assertEquals(0, GuardianLock.lockoutSeconds(2))
        assertEquals(0, GuardianLock.lockoutSeconds(3))
    }

    @Test
    fun `after that the wait grows`() {
        assertEquals(30, GuardianLock.lockoutSeconds(4))
        assertEquals(60, GuardianLock.lockoutSeconds(5))
        assertEquals(120, GuardianLock.lockoutSeconds(6))
    }

    /** Gedeckelt — auch nach hundert Versuchen soll niemand tagelang warten. */
    @Test
    fun `the wait is capped`() {
        assertEquals(15 * 60, GuardianLock.lockoutSeconds(20))
        assertEquals(15 * 60, GuardianLock.lockoutSeconds(100))
        assertEquals(15 * 60, GuardianLock.lockoutSeconds(Int.MAX_VALUE))
    }

    @Test
    fun `the remaining wait counts down`() {
        val now = 1_800_000_000_000L
        assertEquals(30, GuardianLock.remainingLockoutSeconds(4, now, now))
        assertEquals(20, GuardianLock.remainingLockoutSeconds(4, now, now + 10_000L))
        assertEquals(0, GuardianLock.remainingLockoutSeconds(4, now, now + 30_000L))
        assertEquals(0, GuardianLock.remainingLockoutSeconds(4, now, now + 99_000L))
    }

    @Test
    fun `without a recorded failure there is no wait`() {
        val now = 1_800_000_000_000L
        assertEquals(0, GuardianLock.remainingLockoutSeconds(9, 0L, now))
        assertEquals(0, GuardianLock.remainingLockoutSeconds(0, now, now))
    }

    /** Uhr zurückgestellt: Die Wartezeit gilt als abgelaufen, nie als endlos. */
    @Test
    fun `a rewound clock does not extend the wait`() {
        val now = 1_800_000_000_000L
        assertEquals(0, GuardianLock.remainingLockoutSeconds(9, now, now - 60_000L))
    }
}
