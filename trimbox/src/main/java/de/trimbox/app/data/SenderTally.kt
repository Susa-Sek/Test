package de.trimbox.app.data

/**
 * Fasst gelesene Kopfzeilen zu Absendern zusammen — die Liste, die der Nutzer am Ende sieht.
 *
 * Rein und ohne Netz, damit die Sortierung und das Zusammenzählen prüfbar bleiben: Das ist
 * die Zahl, auf deren Grundlage jemand entscheidet, 400 Mails wegzuräumen.
 */
object SenderTally {

    /** Eine gelesene Mail — nur Kopfzeilen, nie der Inhalt. */
    data class MailHeaderRecord(
        val uid: Long,
        val sender: SenderKey.Sender,
        val receivedMillis: Long,
        val subject: String,
        val unsubscribe: UnsubscribeHeader.Options,
    )

    /** Ein Absender mit allem, was für Anzeige und Aufräumen nötig ist. */
    data class SenderSummary(
        val address: String,
        val displayName: String,
        val count: Int,
        val newestMillis: Long,
        val latestSubject: String,
        /** Die Nachrichten dieses Absenders, für das spätere Verschieben. */
        val uids: List<Long>,
        val route: UnsubscribeHeader.Route,
    ) {
        /** Nur diese beiden Wege kann die App allein gehen. */
        val canUnsubscribeAutomatically: Boolean
            get() = route is UnsubscribeHeader.Route.OneClick || route is UnsubscribeHeader.Route.SendMail

        val name: String get() = displayName.ifBlank { address }
    }

    /**
     * @param minCount Absender unterhalb dieser Schwelle fallen raus. Wer zweimal im
     *   Vierteljahr schreibt, ist kein Verteiler, sondern ein Mensch.
     */
    fun summarize(records: List<MailHeaderRecord>, minCount: Int = 2): List<SenderSummary> =
        records.groupBy { it.sender.address }
            .map { (address, group) ->
                val newest = group.maxBy { it.receivedMillis }
                SenderSummary(
                    address = address,
                    // Der zuletzt benutzte Anzeigename gewinnt: Absender benennen sich um.
                    displayName = group.sortedByDescending { it.receivedMillis }
                        .firstOrNull { it.sender.displayName.isNotBlank() }
                        ?.sender?.displayName.orEmpty(),
                    count = group.size,
                    newestMillis = newest.receivedMillis,
                    latestSubject = newest.subject,
                    uids = group.map { it.uid },
                    route = bestRoute(group),
                )
            }
            .filter { it.count >= minCount }
            .sortedWith(compareByDescending<SenderSummary> { it.count }.thenByDescending { it.newestMillis })

    /**
     * Abmelde-Adressen tragen oft eine Kennung, die nur eine Weile gilt. Deshalb der Weg
     * aus der **neuesten** Mail, die überhaupt einen anbietet — nicht der aus der ersten,
     * die zufällig gefunden wurde.
     */
    private fun bestRoute(group: List<MailHeaderRecord>): UnsubscribeHeader.Route =
        group.sortedByDescending { it.receivedMillis }
            .asSequence()
            .map { UnsubscribeHeader.route(it.unsubscribe) }
            .firstOrNull { it !is UnsubscribeHeader.Route.None }
            ?: UnsubscribeHeader.Route.None

    /** Was der Bestätigungsdialog anzeigt, bevor irgendetwas passiert. */
    data class Plan(val unsubscribeCount: Int, val mailCount: Int, val senderCount: Int)

    fun plan(
        summaries: List<SenderSummary>,
        unsubscribeFrom: Set<String>,
        cleanUp: Set<String>,
    ): Plan = Plan(
        unsubscribeCount = summaries.count { it.address in unsubscribeFrom && it.canUnsubscribeAutomatically },
        mailCount = summaries.filter { it.address in cleanUp }.sumOf { it.count },
        senderCount = (unsubscribeFrom + cleanUp).size,
    )
}
