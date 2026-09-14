package de.trimbox.app.data

/**
 * Alles, was TrimBox braucht, um ein Postfach zu erreichen. Das Passwort steht bewusst
 * **nicht** hier drin — es liegt verschlüsselt in [AccountStore] und wird erst beim
 * Verbinden dazugeholt, damit es nicht in Protokollen, Zustandsobjekten oder einem
 * beiläufigen `toString()` landet.
 */
data class MailAccount(
    val address: String,
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int = 587,
    val smtpStartTls: Boolean = true,
    /** Wie weit der Durchlauf zurückschaut. */
    val days: Int = 90,
) {
    /** Fast überall ist der Benutzername die Adresse; wo nicht, ist es der Teil davor. */
    val userName: String get() = address

    val isComplete: Boolean
        get() = address.contains('@') &&
            imapHost.isNotBlank() && imapPort in 1..65535 &&
            smtpHost.isNotBlank() && smtpPort in 1..65535

    companion object {
        /** Aus einer Adresse ein möglichst fertiges Konto bauen. */
        fun suggestFor(address: String): MailAccount {
            val preset = ProviderPresets.forAddress(address)
            return MailAccount(
                address = address.trim(),
                imapHost = preset?.imapHost.orEmpty(),
                imapPort = preset?.imapPort ?: 993,
                smtpHost = preset?.smtpHost.orEmpty(),
                smtpPort = preset?.smtpPort ?: 587,
                smtpStartTls = preset?.smtpStartTls ?: true,
            )
        }
    }
}
