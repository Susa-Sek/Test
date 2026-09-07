package de.trimbox.app.mail

/**
 * Warum etwas schiefging — in den drei Stufen, die für den Nutzer einen Unterschied machen,
 * jeweils mit dem Originaltext des Servers.
 *
 * Der [detail] ist kein Schmuck: Bei einer abgelehnten Anmeldung steht dort, woran es lag
 * ("Application-specific password required", "Invalid credentials", "LOGIN failed"). Ohne
 * ihn rät der Nutzer, und die häufigste Ursache — das falsche Passwort für den falschen
 * Zweck — sieht genauso aus wie ein Tippfehler im Servernamen.
 */
sealed interface MailError {

    val detail: String?

    /** Server hat die Anmeldung abgelehnt — fast immer das fehlende App-Passwort. */
    data class Authentication(override val detail: String?) : MailError

    /** Server nicht erreichbar, Zeitüberschreitung, kein Netz. */
    data class Network(override val detail: String?) : MailError

    /** Alles andere. */
    data class Other(override val detail: String?) : MailError
}

internal fun Throwable.toMailError(): MailError {
    val text = describe()
    return when {
        this is javax.mail.AuthenticationFailedException -> MailError.Authentication(text)

        this is java.net.UnknownHostException ||
            this is java.net.SocketTimeoutException ||
            this is java.net.ConnectException -> MailError.Network(text)

        // Eine MessagingException verpackt den echten Grund oft nur; erst die Ursache
        // sagt, ob es an der Anmeldung oder am Netz lag.
        this is javax.mail.MessagingException && cause != null -> when (val inner = cause!!.toMailError()) {
            is MailError.Other -> MailError.Other(text)
            else -> inner
        }

        this is java.io.IOException -> MailError.Network(text)

        else -> MailError.Other(text)
    }
}

/** Die ganze Ursachenkette, weil JavaMail den aussagekräftigen Teil gern zuunterst legt. */
private fun Throwable.describe(): String {
    val parts = mutableListOf<String>()
    var current: Throwable? = this
    var guard = 0
    while (current != null && guard < 5) {
        val message = current.message?.trim()
        val line = if (message.isNullOrEmpty()) {
            current::class.java.simpleName
        } else {
            message.lines().first().take(160)
        }
        if (parts.lastOrNull() != line) parts += line
        current = current.cause
        guard++
    }
    return parts.joinToString(" · ")
}
