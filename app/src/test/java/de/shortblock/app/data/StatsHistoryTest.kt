package de.shortblock.app.data

import de.shortblock.app.service.Feature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsHistoryTest {

    private val today = 20_000L

    @Test
    fun `round trip keeps counts and seconds`() {
        val history = mapOf(
            today to mapOf(
                Feature.INSTAGRAM_REELS to FeatureStat(count = 3, seconds = 240),
                Feature.YOUTUBE_SHORTS to FeatureStat(count = 7),
            ),
            today - 1 to mapOf(Feature.TIKTOK_FYP to FeatureStat(seconds = 95)),
        )
        assertEquals(history, StatsHistory.decode(StatsHistory.encode(history)))
    }

    /**
     * Die Migration von v0.3 auf v0.4.
     *
     * Bis v0.3 stand je Feature nur eine Zahl im Speicher. Wer aktualisiert, soll seine Woche
     * behalten — eine blanke Zahl wird deshalb als Zähler ohne Sehdauer gelesen.
     */
    @Test
    fun `the old plain-number format is still readable`() {
        val legacy = """{"$today":{"INSTAGRAM_REELS":4,"YOUTUBE_SHORTS":2}}"""
        val decoded = StatsHistory.decode(legacy)

        assertEquals(FeatureStat(count = 4, seconds = 0), decoded[today]?.get(Feature.INSTAGRAM_REELS))
        assertEquals(FeatureStat(count = 2, seconds = 0), decoded[today]?.get(Feature.YOUTUBE_SHORTS))
    }

    @Test
    fun `both formats can appear side by side during a migration`() {
        val mixed = """{"$today":{"INSTAGRAM_REELS":4,"YOUTUBE_SHORTS":{"n":1,"s":30}}}"""
        val decoded = StatsHistory.decode(mixed)

        assertEquals(4, decoded[today]?.get(Feature.INSTAGRAM_REELS)?.count)
        assertEquals(30, decoded[today]?.get(Feature.YOUTUBE_SHORTS)?.seconds)
    }

    @Test
    fun `broken json yields an empty history instead of throwing`() {
        assertTrue(StatsHistory.decode("{kein json").isEmpty())
        assertTrue(StatsHistory.decode("").isEmpty())
        assertTrue(StatsHistory.decode(null).isEmpty())
    }

    /** Ein umbenanntes oder entferntes Feature darf die restliche Historie nicht mitreißen. */
    @Test
    fun `unknown feature names are skipped`() {
        val raw = """{"$today":{"INSTAGRAM_REELS":{"n":4,"s":0},"WAS_AUCH_IMMER":{"n":9,"s":9}}}"""
        assertEquals(setOf(Feature.INSTAGRAM_REELS), StatsHistory.decode(raw)[today]?.keys)
    }

    @Test
    fun `counting and watch time are tracked separately`() {
        var history = StatsHistory.incrementCount(emptyMap(), today, Feature.YOUTUBE_SHORTS)
        history = StatsHistory.addSeconds(history, today, Feature.YOUTUBE_SHORTS, 45)
        history = StatsHistory.addSeconds(history, today, Feature.YOUTUBE_SHORTS, 15)

        assertEquals(1, StatsHistory.countsFor(history, today)[Feature.YOUTUBE_SHORTS])
        assertEquals(60, StatsHistory.secondsFor(history, today)[Feature.YOUTUBE_SHORTS])
    }

    @Test
    fun `adding zero or negative seconds changes nothing`() {
        val history = StatsHistory.addSeconds(emptyMap(), today, Feature.TIKTOK_FYP, 0)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `a new day starts at zero without losing yesterday`() {
        var history = StatsHistory.incrementCount(emptyMap(), today - 1, Feature.INSTAGRAM_REELS)
        history = StatsHistory.addSeconds(history, today - 1, Feature.INSTAGRAM_REELS, 120)
        history = StatsHistory.incrementCount(history, today, Feature.INSTAGRAM_REELS)

        assertEquals(0, StatsHistory.secondsFor(history, today)[Feature.INSTAGRAM_REELS])
        assertEquals(120, StatsHistory.secondsFor(history, today - 1)[Feature.INSTAGRAM_REELS])
        assertEquals(1, StatsHistory.countsFor(history, today)[Feature.INSTAGRAM_REELS])
    }

    @Test
    fun `pruning keeps exactly the retention window`() {
        val history = (0L until 40L).associate { back ->
            (today - back) to mapOf(Feature.INSTAGRAM_REELS to FeatureStat(count = 1))
        }
        val pruned = StatsHistory.prune(history, today)

        assertEquals(StatsHistory.KEEP_DAYS, pruned.size)
        assertTrue(pruned.keys.all { it in (today - StatsHistory.KEEP_DAYS + 1)..today })
    }

    /** Ein falsch gestelltes Gerätedatum darf keine Tage in der Zukunft hinterlassen. */
    @Test
    fun `pruning drops days from the future`() {
        val history = mapOf(today + 5 to mapOf(Feature.INSTAGRAM_REELS to FeatureStat(count = 1)))
        assertTrue(StatsHistory.prune(history, today).isEmpty())
    }

    /**
     * Der Wochenbalken braucht immer sieben Balken. Fehlende Tage müssen als Null erscheinen,
     * sonst wandert die Woche je nach Aktivität hin und her.
     */
    @Test
    fun `lastDays fills gaps with zero and ends today`() {
        val history = mapOf(today - 3 to mapOf(Feature.YOUTUBE_SHORTS to FeatureStat(count = 5)))
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
