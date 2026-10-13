package de.shortblock.app.data

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Die Partnersperre: Einstellungen ändert, wer das Passwort hat — nicht, wer das Handy hält.
 *
 * Gedacht für Selbstbindung: Eine Vertrauensperson setzt das Passwort, und die Hürde ist danach
 * nicht die Technik, sondern jemanden fragen zu müssen. Die App sagt in der Einrichtung
 * ausdrücklich, dass sie kein Tresor ist — im abgesicherten Modus, per `adb` und beim
 * Zurücksetzen geht sie immer weg. Wer mehr verspricht, lügt.
 *
 * **Reine Rechenregel, kein Android.** Deshalb `javax.crypto` und `java.util.Base64` statt
 * `android.util.Base64`: Beides gibt es ab API 26 — genau unser `minSdk` —, und nur so lässt
 * sich das hier als gewöhnlicher Unit-Test prüfen. Bei einer Sperre, die einen aussperren kann,
 * ist das keine Stilfrage.
 */
object GuardianLock {

    /**
     * PBKDF2-Runden.
     *
     * Hoch genug, dass Raten weh tut; niedrig genug, dass ein älteres Telefon nicht sekundenlang
     * hängt. Die Zahl steht im gespeicherten Wert mit drin, damit sich später erhöhen lässt,
     * ohne bestehende Sperren zu entwerten.
     */
    const val ITERATIONS = 120_000

    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    /**
     * Alphabet des Wiederherstellungscodes — **ohne** `0`, `O`, `1`, `I` und `L`.
     *
     * Der Code wird abgeschrieben und später abgetippt, oft von jemand anderem und oft von
     * Papier. Eine Null, die als O gelesen wird, macht den Notausgang unbenutzbar.
     */
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    private const val CODE_GROUPS = 5
    private const val CODE_GROUP_LENGTH = 4

    /** Ab wann gebremst wird, und wie weit das höchstens geht. */
    private const val FREE_ATTEMPTS = 3
    private const val FIRST_LOCKOUT_SECONDS = 30
    private const val MAX_LOCKOUT_SECONDS = 15 * 60

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    /**
     * Was gespeichert wird: `runden:salz:hash`, beides Base64.
     *
     * Das Salz steht bewusst mit drin und ist kein Geheimnis. Seine Aufgabe ist, dass zwei
     * gleiche Passwörter verschiedene Hashes ergeben — sonst verrät schon der Vergleich zweier
     * Geräte, dass dasselbe Passwort gesetzt ist.
     */
    fun store(password: String, salt: ByteArray = newSalt(), iterations: Int = ITERATIONS): String {
        val hash = derive(password, salt, iterations)
        val encoder = Base64.getEncoder()
        return "$iterations:${encoder.encodeToString(salt)}:${encoder.encodeToString(hash)}"
    }

    /**
     * Passt das Passwort zum gespeicherten Wert?
     *
     * Gibt `false` zurück, wenn der gespeicherte Wert leer oder beschädigt ist — **nie** eine
     * Ausnahme. Eine Sperre, die bei kaputten Daten abstürzt, sperrt das Gerät aus, und genau
     * dann kommt niemand mehr an die Einstellungen, um sie zu lösen.
     */
    fun verify(password: String, stored: String): Boolean {
        if (stored.isEmpty()) return false
        return runCatching {
            val parts = stored.split(':')
            if (parts.size != 3) return false
            val iterations = parts[0].toIntOrNull() ?: return false
            if (iterations <= 0) return false
            val decoder = Base64.getDecoder()
            val salt = decoder.decode(parts[1])
            val expected = decoder.decode(parts[2])
            if (salt.isEmpty() || expected.isEmpty()) return false
            constantTimeEquals(derive(password, salt, iterations), expected)
        }.getOrDefault(false)
    }

    /** Fünf Gruppen à vier Zeichen, durch Bindestriche getrennt. */
    fun newRecoveryCode(): String = (1..CODE_GROUPS).joinToString("-") {
        (1..CODE_GROUP_LENGTH)
            .map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }
            .joinToString("")
    }

    /**
     * Abgetippte Form auf die gespeicherte bringen.
     *
     * Wer einen Code von Papier abtippt, tippt ihn anders, als er dasteht: klein geschrieben,
     * mit Leerzeichen statt Bindestrichen, mit einem Leerzeichen zu viel am Ende. Dasselbe
     * Problem löst `AppPassword.normalize` in TrimBox für App-Passwörter.
     */
    fun normalizeCode(typed: String): String =
        typed.uppercase().filter { it in CODE_ALPHABET }

    /** Vor dem Speichern und vor dem Prüfen dieselbe Form — sonst passt der Code nie. */
    fun storeCode(code: String, salt: ByteArray = newSalt()): String =
        store(normalizeCode(code), salt)

    fun verifyCode(typed: String, stored: String): Boolean =
        verify(normalizeCode(typed), stored)

    /**
     * Wie lange nach [failedAttempts] Fehlversuchen gewartet werden muss.
     *
     * Kein Zierat: Der Ratende ist hier der Gerätebesitzer selbst, der sein eigenes Passwort
     * sucht und beliebig viel Zeit hat. Ohne Bremse ist eine Sperre, die jemand anders gesetzt
     * hat, an einem Abend geknackt.
     *
     * Die ersten drei Versuche sind frei — Vertippen soll nicht bestraft werden.
     */
    fun lockoutSeconds(failedAttempts: Int): Int {
        if (failedAttempts <= FREE_ATTEMPTS) return 0
        val steps = failedAttempts - FREE_ATTEMPTS - 1
        if (steps >= 31) return MAX_LOCKOUT_SECONDS
        val grown = FIRST_LOCKOUT_SECONDS.toLong() shl steps
        return grown.coerceAtMost(MAX_LOCKOUT_SECONDS.toLong()).toInt()
    }

    /**
     * Sekunden, die von der Sperrzeit noch übrig sind.
     *
     * Ein Zeitpunkt in der Zukunft heisst zurückgestellte Systemuhr — dann gilt die Wartezeit
     * als abgelaufen, nie als endlos. Dieselbe Vorsicht wie in [CheatPass]: Im Zweifel darf die
     * App niemanden aussperren.
     */
    fun remainingLockoutSeconds(failedAttempts: Int, lastFailureMs: Long, nowMs: Long): Int {
        val total = lockoutSeconds(failedAttempts)
        if (total <= 0 || lastFailureMs <= 0L) return 0
        val elapsed = nowMs - lastFailureMs
        if (elapsed < 0L) return 0
        val remaining = total - elapsed / 1000L
        return remaining.coerceAtLeast(0L).toInt()
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Vergleicht über die volle Länge, damit die Dauer nichts über den Hash verrät. */
    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (index in a.indices) diff = diff or (a[index].toInt() xor b[index].toInt())
        return diff == 0
    }
}
