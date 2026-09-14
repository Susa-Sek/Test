package de.klarzeit.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryTest {

    private val minute = 60_000L

    @Test
    fun `excluded apps count in the total but not in the net figure`() {
        val summary = UsageSessions.summarize(
            perPackage = mapOf(
                "insta" to 60 * minute,
                "maps" to 40 * minute,
                "spotify" to 30 * minute,
            ),
            excluded = setOf("maps", "spotify"),
        )

        assertEquals(130 * minute, summary.totalMillis)
        assertEquals(60 * minute, summary.countedMillis)
        assertEquals(70 * minute, summary.excludedMillis)
    }

    @Test
    fun `excluded apps are still shown, just marked`() {
        val summary = UsageSessions.summarize(
            perPackage = mapOf("insta" to 60 * minute, "maps" to 40 * minute),
            excluded = setOf("maps"),
        )

        assertEquals(listOf("insta", "maps"), summary.apps.map { it.packageName })
        assertTrue(summary.apps.first { it.packageName == "insta" }.counted)
        assertFalse(summary.apps.first { it.packageName == "maps" }.counted)
    }

    @Test
    fun `ignored apps disappear entirely`() {
        val summary = UsageSessions.summarize(
            perPackage = mapOf("insta" to 60 * minute, "com.android.systemui" to 90 * minute),
            excluded = emptySet(),
            ignored = setOf("com.android.systemui"),
        )

        assertEquals(listOf("insta"), summary.apps.map { it.packageName })
        assertEquals(60 * minute, summary.totalMillis)
    }

    @Test
    fun `the widget gets the biggest counted apps, not the excluded ones`() {
        val summary = UsageSessions.summarize(
            perPackage = mapOf(
                "maps" to 90 * minute,
                "insta" to 60 * minute,
                "tiktok" to 45 * minute,
                "mail" to 20 * minute,
                "news" to 10 * minute,
            ),
            excluded = setOf("maps"),
        )

        assertEquals(
            listOf("insta", "tiktok", "mail"),
            summary.topCounted(3).map { it.packageName },
        )
    }

    @Test
    fun `apps without time do not appear`() {
        val summary = UsageSessions.summarize(mapOf("insta" to 0L, "maps" to minute), emptySet())

        assertEquals(listOf("maps"), summary.apps.map { it.packageName })
    }

    @Test
    fun `equal times keep a stable order`() {
        val a = UsageSessions.summarize(mapOf("b" to minute, "a" to minute), emptySet())
        val b = UsageSessions.summarize(mapOf("a" to minute, "b" to minute), emptySet())

        assertEquals(a.apps.map { it.packageName }, b.apps.map { it.packageName })
    }

    @Test
    fun `everything excluded means a net figure of zero`() {
        val summary = UsageSessions.summarize(
            mapOf("maps" to 60 * minute),
            excluded = setOf("maps"),
        )

        assertEquals(0L, summary.countedMillis)
        assertEquals(60 * minute, summary.totalMillis)
        assertTrue(summary.topCounted(3).isEmpty())
    }
}
