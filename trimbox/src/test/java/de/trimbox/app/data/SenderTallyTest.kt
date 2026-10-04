package de.trimbox.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderTallyTest {

    private val day = 86_400_000L

    private fun record(
        uid: Long,
        from: String,
        daysAgo: Long,
        subject: String = "Betreff",
        unsubscribe: String? = null,
        post: String? = null,
    ) = SenderTally.MailHeaderRecord(
        uid = uid,
        sender = requireNotNull(SenderKey.fromHeader(from)),
        receivedMillis = 100 * day - daysAgo * day,
        subject = subject,
        unsubscribe = UnsubscribeHeader.parse(
            listOfNotNull(unsubscribe),
            listOfNotNull(post),
        ),
    )

    @Test
    fun `senders are counted and sorted by volume`() {
        val summaries = SenderTally.summarize(
            listOf(
                record(1, "A <a@example.org>", 1),
                record(2, "A <a@example.org>", 2),
                record(3, "A <a@example.org>", 3),
                record(4, "B <b@example.org>", 1),
                record(5, "B <b@example.org>", 2),
            ),
        )

        assertEquals(listOf("a@example.org", "b@example.org"), summaries.map { it.address })
        assertEquals(3, summaries[0].count)
        assertEquals(listOf(1L, 2L, 3L), summaries[0].uids)
    }

    @Test
    fun `single mails are not a newsletter`() {
        val summaries = SenderTally.summarize(
            listOf(
                record(1, "Ein Mensch <mensch@example.org>", 1),
                record(2, "Verteiler <liste@example.org>", 1),
                record(3, "Verteiler <liste@example.org>", 2),
            ),
        )

        assertEquals(listOf("liste@example.org"), summaries.map { it.address })
    }

    @Test
    fun `the newest display name and subject win`() {
        val summaries = SenderTally.summarize(
            listOf(
                record(1, "Alter Name <post@example.org>", daysAgo = 30, subject = "Alt"),
                record(2, "Neuer Name <post@example.org>", daysAgo = 1, subject = "Neu"),
            ),
        )

        assertEquals("Neuer Name", summaries[0].displayName)
        assertEquals("Neu", summaries[0].latestSubject)
    }

    @Test
    fun `the unsubscribe link comes from the newest mail that offers one`() {
        // Abmelde-Kennungen laufen ab; die aelteste zuerst zu nehmen waere der Fehler.
        val summaries = SenderTally.summarize(
            listOf(
                record(1, "N <n@example.org>", daysAgo = 30, unsubscribe = "<https://example.org/alt>"),
                record(2, "N <n@example.org>", daysAgo = 1, unsubscribe = "<https://example.org/neu>"),
            ),
        )

        assertEquals(
            UnsubscribeHeader.Route.OpenInBrowser("https://example.org/neu"),
            summaries[0].route,
        )
    }

    @Test
    fun `a mail without a header does not erase the route of the others`() {
        val summaries = SenderTally.summarize(
            listOf(
                record(1, "N <n@example.org>", daysAgo = 5, unsubscribe = "<mailto:weg@example.org>"),
                record(2, "N <n@example.org>", daysAgo = 1),
            ),
        )

        assertTrue(summaries[0].route is UnsubscribeHeader.Route.SendMail)
        assertTrue(summaries[0].canUnsubscribeAutomatically)
    }

    @Test
    fun `a sender without any header cannot be unsubscribed automatically`() {
        val summaries = SenderTally.summarize(
            listOf(
                record(1, "N <n@example.org>", 1),
                record(2, "N <n@example.org>", 2),
            ),
        )

        assertEquals(UnsubscribeHeader.Route.None, summaries[0].route)
        assertFalse(summaries[0].canUnsubscribeAutomatically)
    }

    @Test
    fun `a browser-only sender is shown but not counted as automatic`() {
        val summaries = SenderTally.summarize(
            listOf(
                record(1, "N <n@example.org>", 1, unsubscribe = "<https://example.org/u>"),
                record(2, "N <n@example.org>", 2, unsubscribe = "<https://example.org/u>"),
            ),
        )

        assertFalse(summaries[0].canUnsubscribeAutomatically)
    }

    @Test
    fun `the plan counts what the confirmation dialog promises`() {
        val summaries = SenderTally.summarize(
            listOf(
                record(1, "A <a@example.org>", 1, unsubscribe = "<https://a.example.org/u>", post = "List-Unsubscribe=One-Click"),
                record(2, "A <a@example.org>", 2, unsubscribe = "<https://a.example.org/u>", post = "List-Unsubscribe=One-Click"),
                record(3, "B <b@example.org>", 1),
                record(4, "B <b@example.org>", 2),
                record(5, "B <b@example.org>", 3),
            ),
        )

        val plan = SenderTally.plan(
            summaries,
            unsubscribeFrom = setOf("a@example.org", "b@example.org"),
            cleanUp = setOf("a@example.org", "b@example.org"),
        )

        // B bietet keinen Weg an und zaehlt deshalb nicht als Abmeldung mit.
        assertEquals(1, plan.unsubscribeCount)
        assertEquals(5, plan.mailCount)
        assertEquals(2, plan.senderCount)
    }

    @Test
    fun `an empty mailbox yields an empty list, not a crash`() {
        assertTrue(SenderTally.summarize(emptyList()).isEmpty())
    }
}
