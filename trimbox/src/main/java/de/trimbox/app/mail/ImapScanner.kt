package de.trimbox.app.mail

import com.sun.mail.imap.IMAPFolder
import de.trimbox.app.data.MailAccount
import de.trimbox.app.data.SenderKey
import de.trimbox.app.data.SenderTally
import de.trimbox.app.data.UnsubscribeHeader
import java.util.Date
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Liest den Posteingang und gibt zurück, wer wie oft geschrieben hat.
 *
 * Der ganze Entwurf hängt an einem Satz: **es werden nur Kopfzeilen geladen.** Der
 * [FetchProfile] weiter unten sorgt dafür, dass der Server pro Mail eine Handvoll Bytes
 * schickt statt der kompletten Nachricht mit Bildern. Das ist der Unterschied zwischen
 * zwanzig Sekunden und zehn Minuten — und der Grund, warum die App ehrlich behaupten
 * kann, deine Mails nicht zu lesen.
 */
class ImapScanner(
    private val account: MailAccount,
    private val password: String,
) {

    /** Nur anmelden und wieder auflegen — für den Knopf „Verbinden". */
    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            connect().use { it.getFolder("INBOX").exists() }
            Unit
        }
    }

    /**
     * @param onProgress wird mit der Zahl bereits gelesener Kopfzeilen gerufen, damit die
     *   Oberfläche etwas zu zeigen hat.
     */
    suspend fun scan(onProgress: (Int) -> Unit = {}): Result<List<SenderTally.MailHeaderRecord>> =
        withContext(Dispatchers.IO) {
            runCatching {
                connect().use { store ->
                    val inbox = store.getFolder("INBOX") as IMAPFolder
                    // Nur lesend: Ein Durchlauf darf am Postfach nichts verändern, nicht
                    // einmal das Gelesen-Merkmal.
                    inbox.open(Folder.READ_ONLY)
                    try {
                        readHeaders(inbox, onProgress)
                    } finally {
                        runCatching { inbox.close(false) }
                    }
                }
            }
        }

    private suspend fun readHeaders(
        inbox: IMAPFolder,
        onProgress: (Int) -> Unit,
    ): List<SenderTally.MailHeaderRecord> {
        val since = Date(System.currentTimeMillis() - account.days * MILLIS_PER_DAY)
        val found = inbox.search(ReceivedDateTerm(ComparisonTerm.GE, since))
        if (found.isEmpty()) return emptyList()

        val profile = FetchProfile().apply {
            // ENVELOPE bringt Absender, Betreff und Datum in einem Rutsch.
            add(FetchProfile.Item.ENVELOPE)
            // Die UID brauchen wir später zum Verschieben; ohne sie müsste die App die
            // Nachrichten ein zweites Mal suchen.
            add(UIDFolder.FetchProfileItem.UID)
            add(HEADER_UNSUBSCRIBE)
            add(HEADER_UNSUBSCRIBE_POST)
        }

        val records = ArrayList<SenderTally.MailHeaderRecord>(found.size)
        for (batch in found.toList().chunked(BATCH_SIZE)) {
            coroutineContext.ensureActive()
            val array = batch.toTypedArray()
            inbox.fetch(array, profile)
            for (message in array) {
                // Eine einzelne kaputte Mail darf den Durchlauf nicht beenden.
                runCatching { toRecord(inbox, message) }.getOrNull()?.let { records += it }
            }
            onProgress(records.size)
        }
        return records
    }

    private fun toRecord(inbox: IMAPFolder, message: Message): SenderTally.MailHeaderRecord? {
        val sender = senderOf(message) ?: return null
        val received = message.receivedDate ?: message.sentDate ?: return null

        return SenderTally.MailHeaderRecord(
            uid = inbox.getUID(message),
            sender = sender,
            receivedMillis = received.time,
            subject = message.subject.orEmpty(),
            unsubscribe = UnsubscribeHeader.parse(
                listUnsubscribe = message.getHeader(HEADER_UNSUBSCRIBE)?.toList().orEmpty(),
                listUnsubscribePost = message.getHeader(HEADER_UNSUBSCRIBE_POST)?.toList().orEmpty(),
            ),
        )
    }

    /**
     * JavaMail hat den Anzeigenamen schon entschlüsselt (`=?UTF-8?B?…?=`), deshalb zuerst
     * dieser Weg; die rohe Kopfzeile ist nur der Rückfallpfad für Mails, an denen sich
     * JavaMail verschluckt.
     */
    private fun senderOf(message: Message): SenderKey.Sender? {
        val parsed = runCatching { message.from?.firstOrNull() as? InternetAddress }.getOrNull()
        if (parsed != null) {
            val address = SenderKey.normalize(parsed.address)
            if (address != null) {
                return SenderKey.Sender(address, parsed.personal.orEmpty().trim())
            }
        }
        return SenderKey.fromHeader(
            runCatching { message.getHeader("From")?.firstOrNull() }.getOrNull(),
        )
    }

    private fun connect(): Store =
        MailSession.imap(account).getStore("imaps").apply {
            connect(account.imapHost, account.imapPort, account.userName, password)
        }

    private inline fun <T> Store.use(block: (Store) -> T): T =
        try {
            block(this)
        } finally {
            runCatching { close() }
        }

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000L

        /**
         * Kleine Häppchen, damit der Fortschritt sichtbar wandert und ein Abbruch
         * schnell greift. Grössere Blöcke bringen kaum noch Tempo.
         */
        const val BATCH_SIZE = 250

        const val HEADER_UNSUBSCRIBE = "List-Unsubscribe"
        const val HEADER_UNSUBSCRIBE_POST = "List-Unsubscribe-Post"
    }
}
