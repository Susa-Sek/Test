package de.trimbox.app.mail

/**
 * Warum etwas schiefging — in den drei Stufen, die für den Nutzer einen Unterschied machen.
 * Aus einer rohen `MessagingException` lässt sich keine hilfreiche Meldung bauen.
 */
sealed interface MailError {
    /** Server hat die Anmeldung abgelehnt — fast immer das fehlende App-Passwort. */
    data object Authentication : MailError

    /** Server nicht erreichbar, Zeitüberschreitung, kein Netz. */
    data object Network : MailError

    /** Alles andere, mit dem Originaltext für den seltenen Fall, dass er hilft. */
    data class Other(val message: String) : MailError
}

internal fun Throwable.toMailError(): MailError = when {
    this is javax.mail.AuthenticationFailedException -> MailError.Authentication
    this is java.net.UnknownHostException ||
        this is java.net.SocketTimeoutException ||
        this is java.net.ConnectException ||
        this is java.io.IOException -> MailError.Network
    // Eine MessagingException verpackt den echten Grund oft nur.
    this is javax.mail.MessagingException && cause != null -> cause!!.toMailError()
    else -> MailError.Other(message ?: this::class.java.simpleName)
}
