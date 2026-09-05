package de.trimbox.app.data

import de.trimbox.app.mail.ImapScanner
import de.trimbox.app.mail.MailError
import de.trimbox.app.mail.MailboxCleaner
import de.trimbox.app.mail.Unsubscriber
import de.trimbox.app.mail.toMailError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Führt den einen Durchlauf: lesen, auswerten, auf Zuruf abmelden und aufräumen.
 *
 * Die Reihenfolge in [execute] ist Absicht: **erst abmelden, dann wegräumen.** Andersherum
 * wären die Mails mit den Abmelde-Kopfzeilen schon im Papierkorb, wenn die Abmeldung
 * fehlschlägt und jemand es von Hand versuchen will.
 */
class TrimRepository {

    sealed interface Phase {
        /** Noch kein Postfach verbunden. */
        data object Idle : Phase

        data class Scanning(val headersRead: Int) : Phase

        data class Ready(
            val senders: List<SenderTally.SenderSummary>,
            val mailsSeen: Int,
        ) : Phase

        data class Working(val done: Int, val total: Int) : Phase

        data class Done(val report: Report) : Phase
    }

    data class Report(
        val unsubscribed: Int = 0,
        val failed: Int = 0,
        /** Absender, die einen Klick im Browser brauchen: Anzeigename zu Adresse. */
        val browser: List<BrowserTask> = emptyList(),
        val moved: Int = 0,
        val trashMissing: Boolean = false,
        val markedOnly: Boolean = false,
    )

    data class BrowserTask(val sender: String, val url: String)

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _error = MutableStateFlow<MailError?>(null)
    val error: StateFlow<MailError?> = _error.asStateFlow()

    fun reset() {
        _phase.value = Phase.Idle
        _error.value = null
    }

    suspend fun scan(account: MailAccount, password: String) {
        _error.value = null
        _phase.value = Phase.Scanning(0)

        ImapScanner(account, password)
            .scan { read -> _phase.value = Phase.Scanning(read) }
            .fold(
                onSuccess = { records ->
                    _phase.value = Phase.Ready(
                        senders = SenderTally.summarize(records),
                        mailsSeen = records.size,
                    )
                },
                onFailure = { failure ->
                    _error.value = failure.toMailError()
                    _phase.value = Phase.Idle
                },
            )
    }

    /**
     * @param unsubscribeFrom Adressen, bei denen abgemeldet werden soll.
     * @param cleanUp Adressen, deren Mails in den Papierkorb sollen.
     */
    suspend fun execute(
        account: MailAccount,
        password: String,
        senders: List<SenderTally.SenderSummary>,
        unsubscribeFrom: Set<String>,
        cleanUp: Set<String>,
    ) {
        _error.value = null
        val toUnsubscribe = senders.filter { it.address in unsubscribeFrom }
        val toClean = senders.filter { it.address in cleanUp }
        val total = toUnsubscribe.size + if (toClean.isEmpty()) 0 else 1

        var report = Report()
        var done = 0
        _phase.value = Phase.Working(done, total)

        val unsubscriber = Unsubscriber(account, password)
        for (sender in toUnsubscribe) {
            report = when (val result = unsubscriber.unsubscribe(sender.address, sender.route)) {
                is Unsubscriber.Result.Sent ->
                    report.copy(unsubscribed = report.unsubscribed + 1)

                is Unsubscriber.Result.NeedsBrowser ->
                    report.copy(browser = report.browser + BrowserTask(sender.name, result.url))

                is Unsubscriber.Result.Failed ->
                    report.copy(failed = report.failed + 1)

                is Unsubscriber.Result.Impossible -> report
            }
            done++
            _phase.value = Phase.Working(done, total)
        }

        if (toClean.isNotEmpty()) {
            val uids = toClean.flatMap { it.uids }
            MailboxCleaner(account, password).moveToTrash(uids).fold(
                onSuccess = { outcome ->
                    report = report.copy(
                        moved = outcome.moved,
                        trashMissing = outcome.trashMissing,
                        markedOnly = outcome.markedOnly,
                    )
                },
                onFailure = { failure -> _error.value = failure.toMailError() },
            )
            done++
        }

        _phase.value = Phase.Done(report)
    }
}
