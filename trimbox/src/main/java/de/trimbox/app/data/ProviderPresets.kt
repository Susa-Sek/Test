package de.trimbox.app.data

/**
 * Server und Ports zu einer Mailadresse raten, damit niemand vier Felder aus einer
 * Hilfeseite abtippen muss. Alles bleibt überschreibbar — geraten wird nur der Vorschlag.
 */
object ProviderPresets {

    data class Preset(
        val imapHost: String,
        val imapPort: Int = 993,
        val smtpHost: String,
        val smtpPort: Int = 587,
        /** 587 spricht Klartext und schaltet per STARTTLS um, 465 ist von Anfang an TLS. */
        val smtpStartTls: Boolean = true,
    )

    private val PRESETS: Map<String, Preset> = buildMap {
        fun add(preset: Preset, vararg domains: String) = domains.forEach { put(it, preset) }

        add(Preset("imap.gmail.com", smtpHost = "smtp.gmail.com"), "gmail.com", "googlemail.com")
        add(Preset("imap.gmx.net", smtpHost = "mail.gmx.net"), "gmx.de", "gmx.net", "gmx.at", "gmx.ch")
        add(Preset("imap.web.de", smtpHost = "smtp.web.de"), "web.de")
        add(Preset("posteo.de", smtpHost = "posteo.de"), "posteo.de", "posteo.net")
        add(Preset("imap.mailbox.org", smtpHost = "smtp.mailbox.org"), "mailbox.org")
        add(Preset("imap.mail.me.com", smtpHost = "smtp.mail.me.com"), "icloud.com", "me.com", "mac.com")
        add(Preset("imap.mail.yahoo.com", smtpHost = "smtp.mail.yahoo.com"), "yahoo.com", "yahoo.de")
        add(
            Preset("secureimap.t-online.de", smtpHost = "securesmtp.t-online.de", smtpPort = 465, smtpStartTls = false),
            "t-online.de", "magenta.de",
        )
        add(Preset("imap.ionos.de", smtpHost = "smtp.ionos.de"), "ionos.de", "1und1.de")
    }

    /**
     * Postfächer, die IMAP mit Passwort nicht mehr anbieten. Microsoft hat die einfache
     * Anmeldung für private Konten abgeschaltet; dort hilft nur OAuth, und das kommt erst
     * in einer späteren Fassung. Besser, die App sagt das vorher, als dass der Nutzer
     * dreimal sein Passwort für falsch hält.
     */
    private val NEEDS_OAUTH = setOf(
        "outlook.com", "outlook.de", "hotmail.com", "hotmail.de", "live.com", "live.de", "msn.com",
    )

    fun forAddress(address: String): Preset? = PRESETS[domainOf(address)]

    fun needsOAuth(address: String): Boolean = domainOf(address) in NEEDS_OAUTH

    private fun domainOf(address: String): String =
        address.trim().lowercase().substringAfterLast('@', "")
}
