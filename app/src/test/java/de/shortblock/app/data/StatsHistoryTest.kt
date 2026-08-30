package de.shortblock.app.data

import de.shortblock.app.service.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsHistoryTest {

    private val today = 20_000L

    @Test
    fun `round trip keeps the counts`() {
        val history = mapOf(
            today to mapOf(Feature.INSTAGRAM_REELS to 3, Feature.YOUTUBE_SHORTS to 7),
            today - 1 to mapOf(Feature.TIKTOK_FYP to 2),
        )
        assertEquals(history, StatsHistory.decode(StatsHistory.encode(history)))
    }

    /** Nach einem Absturz kann alles im Speicher stehen — nur abstürzen darf es nicht. */
    @Test
    fun `broken json yields an empty history instead of throwing`() {
        assertEquals(emptyMap<Long, Map<Feature, Int>>(), StatsHistory.decode("{kein json"))
        assertEquals(emptyMap<Long, Map<Feature, Int>>(), StatsHistory.decode(""))
        assertEquals(emptyMap<Long, Map<Feature, Int>>(), StatsHistory.decode(null))
    }

    /** Ein umbenanntes oder entferntes Feature darf die restliche Historie nicht mitreißen. */
    @Test
    fun `unknown feature names are skipped`() {
        val raw = """{"$today":{"INSTAGRAM_REELS":4,"WAS_AUCH_IMMER":9}}"""
        assertEquals(mapOf(Feature.INSTAGRAM_REELS to 4), StatsHistory.decode(raw)[today])
    }

    @Test
    fun `increment counts on the current day only`() {
        val once = StatsHistory.increment(emptyMap(), today, Feature.YOUTUBE_SHORTS)
        val twice = StatsHistory.increment(once, today, Feature.YOUTUBE_SHORTS)

        assertEquals(2, twice[today]?.get(Feature.YOUTUBE_SHORTS))
        assertEquals(setOf(today), twice.keys)
    }

    @Test
    fun `a new day starts at zero without losing yesterday`() {
        val yesterday = StatsHistory.increment(emptyMap(), today - 1, Feature.INSTAGRAM_REELS)
        val nextDay = StatsHistory.increment(yesterday, today, Feature.INSTAGRAM_REELS)

        assertEquals(1, StatsHistory.countsFor(nextDay, today)[Feature.INSTAGRAM_REELS])
        assertEquals(1, StatsHistory.countsFor(nextDay, today - 1)[Feature.INSTAGRAM_REELS])
    }

    @Test
    fun `pruning keeps exactly the retention window`() {
        val history = (0L until 40L).associate { back ->
            (today - back) to mapOf(Feature.INSTAGRAM_REELS to 1)
        }
        val pruned = StatsHistory.prune(history, today)

        assertEquals(StatsHistory.KEEP_DAYS, pruned.size)
        assertTrue(pruned.keys.all { it in (today - StatsHistory.KEEP_DAYS + 1)..today })
    }

    /** Ein falsch gestelltes Gerätedatum darf keine Tage in der Zukunft hinterlassen. */
    @Test
    fun `pruning drops days from the future`() {
        val history = mapOf(today + 5 to mapOf(Feature.INSTAGRAM_REELS to 1))
        assertTrue(StatsHistory.prune(history, today).isEmpty())
    }

    /**
     * Der Wochenbalken braucht immer sieben Balken. Fehlende Tage müssen als Null erscheinen,
     * sonst wandert die Woche je nach Aktivität hin und her.
     */
    @Test
    fun `lastDays fills gaps with zero and ends today`() {
        val history = mapOf(today - 3 to mapOf(Feature.YOUTUBE_SHORTS to 5))
        val week = StatsHistory.lastDays(history, today)

        assertEquals(7, week.size)
        assertEquals(today - 6, week.first().epochDay)
        assertEquals(today, week.last().epochDay)
        assertEquals(5, week.single { it.epochDay == today - 3 }.total)
        assertEquals(0, week.last().total)
    }

    @Test
    fun `saved minutes round down`() {
        assertEquals(0, StatsHistory.savedMinutes(0))
        assertEquals(0, StatsHistory.savedMinutes(2))
        assertEquals(1, StatsHistory.savedMinutes(3))
        assertEquals(25, StatsHistory.savedMinutes(60))
    }
}
