package de.klarzeit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayHistoryTest {

    private val today = 20_000L
    private val hour = 3_600_000L

    private fun summary(hours: Long, opens: Int = 0) =
        DayHistory.DaySummary(countedMillis = hours * hour, totalMillis = hours * hour * 2, opens = opens, unlocks = opens)

    @Test
    fun `encoding and decoding loses nothing`() {
        val history = mapOf(today to summary(2, opens = 47), today - 1 to summary(3))

        assertEquals(history, DayHistory.decode(DayHistory.encode(history)))
    }

    @Test
    fun `broken json yields an empty history, not a crash`() {
        assertTrue(DayHistory.decode(null).isEmpty())
        assertTrue(DayHistory.decode("").isEmpty())
        assertTrue(DayHistory.decode("kein json").isEmpty())
        assertTrue(DayHistory.decode("""{"nichtszahl":{"c":1}}""").isEmpty())
    }

    @Test
    fun `the same day is overwritten, never added up`() {
        // Der Wert ist jedes Mal die Tagessumme. Addieren wuerde ihn bei jedem Durchlauf
        // vervielfachen — nach einer Stunde staenden dort zwoelf Stunden.
        var history = DayHistory.put(emptyMap(), today, summary(1))
        history = DayHistory.put(history, today, summary(2))

        assertEquals(1, history.size)
        assertEquals(2 * hour, history[today]?.countedMillis)
    }

    @Test
    fun `pruning keeps exactly the last days`() {
        val history = (0L until 30L).associate { (today - it) to summary(1) }

        val pruned = DayHistory.prune(history, today, keepDays = 14)

        assertEquals(14, pruned.size)
        assertTrue(pruned.containsKey(today))
        assertTrue(pruned.containsKey(today - 13))
        assertTrue(!pruned.containsKey(today - 14))
    }

    @Test
    fun `days in the future are dropped`() {
        // Zurueckgestellte Systemuhr: Ein Tag aus der Zukunft darf die Reihe nicht sprengen.
        val history = mapOf(today to summary(1), today + 3 to summary(9))

        assertEquals(setOf(today), DayHistory.prune(history, today).keys)
    }

    @Test
    fun `the week has seven fixed places, gaps included`() {
        val history = mapOf(today to summary(2), today - 3 to summary(1))

        val week = DayHistory.lastDays(history, today, days = 7)

        assertEquals(7, week.size)
        assertEquals(today - 6, week.first().first)
        assertEquals(today, week.last().first)
        // Ein Tag ohne Eintrag ist ein leerer Balken, kein fehlender.
        assertEquals(0L, week[1].second.countedMillis)
        assertEquals(2 * hour, week.last().second.countedMillis)
    }
}
