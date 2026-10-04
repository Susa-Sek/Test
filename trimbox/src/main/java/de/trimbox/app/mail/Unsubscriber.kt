package de.trimbox.app.mail

import de.trimbox.app.data.MailAccount
import de.trimbox.app.data.UnsubscribeHeader
import java.net.HttpURLConnection
import java.net.URL
import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Führt Abmeldungen aus — die beiden Wege, die ohne Zutun des Nutzers gehen.
 *
 * Was hier bewusst fehlt: ein stilles GET auf eine beliebige Abmelde-Adresse. Ohne die
 * Zusage aus `List-Unsubscribe-Post` weiss niemand, was hinter dem Link liegt; das bleibt
 * ein Fall für den Browser und einen bewussten Klick.
 */
class Unsubscriber(
    private val account: MailAccount,
    private val password: String,
) {

    sealed interface Result {
        data class Sent(val address: String) : Result
        data class NeedsBrowser(val address: String, val url: String) : Result
        data class Impossible(val address: String) : Result
        data class Failed(val address: String, val reason: String) : Result
    }

    suspend fun unsubscribe(
        address: String,
        route: UnsubscribeHeader.Route,
    ): Result = withContext(Dispatchers.IO) {
        when (route) {
            is UnsubscribeHeader.Route.OneClick -> runCatching { oneClick(route.url) }
                .fold(
                    onSuccess = { ok ->
                        if (ok) Result.Sent(address) else Result.Failed(address, "HTTP")
                    },
                    onFailure = { Result.Failed(address, it.message.orEmpty()) },
                )

            is UnsubscribeHeader.Route.SendMail -> runCatching { sendMail(route.target) }
                .fold(
                    onSuccess = { Result.Sent(address) },
                    onFailure = { Result.Failed(address, it.message.orEmpty()) },
                )

            is UnsubscribeHeader.Route.OpenInBrowser -> Result.NeedsBrowser(address, route.url)
            UnsubscribeHeader.Route.None -> Result.Impossible(address)
        }
    }

    /**
     * Ein-Klick-Abmeldung nach RFC 8058: POST mit genau diesem Rumpf, sonst nichts.
     */
    private fun oneClick(url: String): Boolean {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            // Eine Umleitung wuerde HttpURLConnection als GET weiterverfolgen — und ein GET
            // auf ein unbekanntes Ziel ist genau das, was diese App nicht tut.
            instanceFollowRedirects = false
        }
        return try {
            connection.outputStream.use { it.write(BODY.toByteArray(Charsets.US_ASCII)) }
            // 2xx ist die Zusage, 3xx die uebliche Weiterleitung auf eine Danke-Seite.
            connection.responseCode in 200..399
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Abmelde-Mail über den eigenen Postausgang. Betreff und Text kommen aus dem
     * `mailto:` des Absenders — manche Verteiler prüfen genau darauf.
     */
    private fun sendMail(target: UnsubscribeHeader.MailtoTarget) {
        val session = MailSession.smtp(account)
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(account.address))
            setRecipient(Message.RecipientType.TO, InternetAddress(target.address))
            subject = target.subject ?: DEFAULT_SUBJECT
            setText(target.body ?: DEFAULT_BODY, "UTF-8")
        }

        session.getTransport("smtp").apply {
            connect(account.smtpHost, account.smtpPort, account.userName, password)
            try {
                sendMessage(message, message.allRecipients)
            } finally {
                runCatching { close() }
            }
        }
    }

    private companion object {
        const val BODY = "List-Unsubscribe=One-Click"
        const val TIMEOUT_MS = 20_000
        const val DEFAULT_SUBJECT = "unsubscribe"
        const val DEFAULT_BODY = "unsubscribe"
    }
}
