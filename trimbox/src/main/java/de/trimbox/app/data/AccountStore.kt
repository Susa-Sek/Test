package de.trimbox.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal val Context.trimboxDataStore: DataStore<Preferences> by preferencesDataStore(name = "trimbox")

/**
 * Bewahrt Konto und Passwort auf dem Gerät auf.
 *
 * Das Passwort wird mit einem Schlüssel aus dem **Android Keystore** verschlüsselt (AES/GCM).
 * Der Schlüssel selbst verlässt die Hardware nie und lässt sich nicht auslesen — wer die
 * DataStore-Datei kopiert, hat damit nichts gewonnen.
 *
 * Bewusst ohne `androidx.security:security-crypto`: Die Bibliothek wird nicht mehr gepflegt,
 * und für einen Schlüssel und zwei Aufrufe lohnt keine Abhängigkeit, die absehbar stirbt.
 */
class AccountStore(context: Context) {

    private val dataStore = context.applicationContext.trimboxDataStore

    val account: Flow<MailAccount?> = dataStore.data.map { prefs ->
        val address = prefs[ADDRESS] ?: return@map null
        MailAccount(
            address = address,
            imapHost = prefs[IMAP_HOST].orEmpty(),
            imapPort = prefs[IMAP_PORT] ?: 993,
            smtpHost = prefs[SMTP_HOST].orEmpty(),
            smtpPort = prefs[SMTP_PORT] ?: 587,
            smtpStartTls = prefs[SMTP_STARTTLS] ?: true,
            days = prefs[DAYS] ?: 90,
        )
    }

    suspend fun save(account: MailAccount, password: String) {
        val sealed = encrypt(password)
        dataStore.edit { prefs ->
            prefs[ADDRESS] = account.address
            prefs[IMAP_HOST] = account.imapHost
            prefs[IMAP_PORT] = account.imapPort
            prefs[SMTP_HOST] = account.smtpHost
            prefs[SMTP_PORT] = account.smtpPort
            prefs[SMTP_STARTTLS] = account.smtpStartTls
            prefs[DAYS] = account.days
            prefs[PASSWORD] = sealed
        }
    }

    /** `null`, wenn nichts gespeichert ist oder der Schlüssel nicht mehr passt. */
    suspend fun password(): String? {
        val sealed = dataStore.data.first()[PASSWORD] ?: return null
        return decrypt(sealed)
    }

    /**
     * Trennen heisst wirklich trennen: Der Keystore-Schlüssel wird mit gelöscht, damit der
     * zurückgebliebene Geheimtext auch theoretisch nicht mehr aufgeht.
     */
    suspend fun clear() {
        dataStore.edit { it.clear() }
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // Der Zufallsvektor gehoert zum Geheimtext und darf offen davorstehen.
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(sealed: String): String? = runCatching {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        if (bytes.size <= IV_LENGTH) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, bytes, 0, IV_LENGTH),
        )
        String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
    }.getOrNull()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        val store = keyStore()
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Kein Geräteschloss verlangt: Sonst stünde der Nutzer ohne Bildschirmsperre
                // vor einer App, die sich nicht mehr anmelden kann.
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "trimbox.mailbox"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128

        val ADDRESS = stringPreferencesKey("address")
        val IMAP_HOST = stringPreferencesKey("imap_host")
        val IMAP_PORT = intPreferencesKey("imap_port")
        val SMTP_HOST = stringPreferencesKey("smtp_host")
        val SMTP_PORT = intPreferencesKey("smtp_port")
        val SMTP_STARTTLS = booleanPreferencesKey("smtp_starttls")
        val DAYS = intPreferencesKey("days")
        val PASSWORD = stringPreferencesKey("password_sealed")
    }
}
