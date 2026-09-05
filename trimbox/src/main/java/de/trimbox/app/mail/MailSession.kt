package de.trimbox.app.mail

import de.trimbox.app.data.MailAccount
import java.util.Properties
import javax.mail.Session

/**
 * Baut die JavaMail-Sitzungen. Klein, aber die Stelle, an der die Sicherheit der ganzen
 * App entschieden wird.
 *
 * Der wichtigste Eintrag ist `ssl.checkserveridentity`. JavaMail 1.6 prüft **von sich aus
 * nicht**, ob das Zertifikat überhaupt zu dem Server gehört, mit dem gerade gesprochen
 * wird — ohne diese Zeile nimmt die App jedes gültige Zertifikat der Welt an, und ein
 * Angreifer im selben WLAN liest Passwort und Postfach mit.
 */
internal object MailSession {

    private const val CONNECT_TIMEOUT_MS = "20000"
    private const val READ_TIMEOUT_MS = "60000"

    fun imap(account: MailAccount): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", account.imapHost)
            put("mail.imaps.port", account.imapPort.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.imaps.connectiontimeout", CONNECT_TIMEOUT_MS)
            put("mail.imaps.timeout", READ_TIMEOUT_MS)
            // Ohne das holt JavaMail bei manchen Servern die ganze Nachricht, sobald ein
            // einzelnes Feld fehlt — und aus dem Kopfzeilen-Durchlauf wird ein Download
            // des halben Postfachs.
            put("mail.imaps.partialfetch", "true")
        }
        return Session.getInstance(props)
    }

    fun smtp(account: MailAccount): Session {
        val props = Properties().apply {
            put("mail.transport.protocol", "smtp")
            put("mail.smtp.host", account.smtpHost)
            put("mail.smtp.port", account.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.ssl.checkserveridentity", "true")
            put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.smtp.connectiontimeout", CONNECT_TIMEOUT_MS)
            put("mail.smtp.timeout", READ_TIMEOUT_MS)
            if (account.smtpStartTls) {
                put("mail.smtp.starttls.enable", "true")
                // "required" ist der Unterschied zwischen "verschlüsselt, wenn es klappt"
                // und "sonst gar nicht". Eine Abmeldung im Klartext wäre verzichtbar,
                // das Passwort davor nicht.
                put("mail.smtp.starttls.required", "true")
            } else {
                put("mail.smtp.ssl.enable", "true")
            }
        }
        return Session.getInstance(props)
    }
}
