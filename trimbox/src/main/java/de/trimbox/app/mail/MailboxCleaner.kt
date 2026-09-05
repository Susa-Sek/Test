package de.trimbox.app.mail

import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import de.trimbox.app.data.MailAccount
import de.trimbox.app.data.TrashFolder
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Räumt Mails in den Papierkorb. Nie weiter.
 *
 * Hier steckt die gefährlichste Zeile, die diese App **nicht** enthält: `folder.expunge()`
 * ohne Argument löscht alles, was im Ordner als gelöscht markiert ist — auch das, was der
 * Nutzer vor drei Wochen selbst markiert und nie weggeräumt hat. Deshalb ausschliesslich
 * `MOVE` (RFC 6851) und, wo der Server das nicht kann, `UID EXPUNGE` (RFC 4315), das
 * nachweislich nur die genannten Nachrichten trifft. Kann der Server auch das nicht,
 * bleiben die Mails markiert liegen und die App sagt es, statt zu raten.
 */
class MailboxCleaner(
    private val account: MailAccount,
    private val password: String,
) {

    data class Outcome(
        val moved: Int,
        /** Kein Papierkorb gefunden — es wurde nichts angefasst. */
        val trashMissing: Boolean = false,
        /** Server kann weder MOVE noch UID EXPUNGE: Mails sind nur markiert. */
        val markedOnly: Boolean = false,
    )

    suspend fun moveToTrash(uids: List<Long>): Result<Outcome> = withContext(Dispatchers.IO) {
        runCatching {
            if (uids.isEmpty()) return@runCatching Outcome(moved = 0)

            val store = MailSession.imap(account).getStore("imaps") as IMAPStore
            store.connect(account.imapHost, account.imapPort, account.userName, password)
            try {
                val trashName = findTrash(store) ?: return@runCatching Outcome(0, trashMissing = true)
                val trash = store.getFolder(trashName)

                val inbox = store.getFolder("INBOX") as IMAPFolder
                inbox.open(Folder.READ_WRITE)
                try {
                    val messages = inbox.getMessagesByUID(uids.toLongArray())
                        .filterNotNull()
                        .toTypedArray()
                    if (messages.isEmpty()) return@runCatching Outcome(moved = 0)

                    move(store, inbox, trash, messages)
                } finally {
                    // close(false): close(true) wuerde den ganzen Ordner ausraeumen.
                    runCatching { inbox.close(false) }
                }
            } finally {
                runCatching { store.close() }
            }
        }
    }

    private fun move(
        store: IMAPStore,
        inbox: IMAPFolder,
        trash: Folder,
        messages: Array<Message>,
    ): Outcome {
        if (store.hasCapability("MOVE")) {
            inbox.moveMessages(messages, trash)
            return Outcome(moved = messages.size)
        }

        // Der alte Weg: Kopie in den Papierkorb, Original markieren, gezielt ausraeumen.
        inbox.copyMessages(messages, trash)
        inbox.setFlags(messages, Flags(Flags.Flag.DELETED), true)

        return if (store.hasCapability("UIDPLUS")) {
            inbox.expunge(messages)
            Outcome(moved = messages.size)
        } else {
            Outcome(moved = messages.size, markedOnly = true)
        }
    }

    /** Alle Ordner auflisten und [TrashFolder] entscheiden lassen. */
    private fun findTrash(store: IMAPStore): String? {
        val folders = store.defaultFolder.list("*").map { folder ->
            TrashFolder.Folder(
                fullName = folder.fullName,
                attributes = runCatching { (folder as? IMAPFolder)?.attributes?.toSet() }
                    .getOrNull()
                    .orEmpty(),
            )
        }
        return TrashFolder.choose(folders)
    }
}
