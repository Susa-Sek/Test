package de.shortblock.app.data

import de.shortblock.app.service.Feature
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Das Zusammenspiel der beiden TikTok-Zeilen.
 *
 * Ohne diese Unterscheidung stand über der „Für dich“-Zeile „ohne Wirkung“, obwohl sie während
 * eines laufenden TikTok-Kontingents sehr wohl greift — ein Hinweis, der lügt, ist schlimmer
 * als keiner.
 */
class TikTokRowStateTest {

    private fun settings(tiktokAll: Boolean, allowanceMinutes: Int) = BlockSettings(
        enabled = if (tiktokAll) {
            setOf(Feature.TIKTOK_FYP, Feature.TIKTOK_ALL)
        } else {
            setOf(Feature.TIKTOK_FYP)
        },
        diagnostics = false,
        budgets = mapOf(Feature.TIKTOK_ALL to allowanceMinutes),
    )

    @Test
    fun `without the total block the row stands on its own`() {
        assertEquals(
            FypRelation.INDEPENDENT,
            settings(tiktokAll = false, allowanceMinutes = 0).tiktokFypRelation(),
        )
    }

    @Test
    fun `an allowance on the total block makes the row effective again`() {
        assertEquals(
            FypRelation.DURING_BUDGET,
            settings(tiktokAll = true, allowanceMinutes = 10).tiktokFypRelation(),
        )
    }

    @Test
    fun `a total block without allowance overrides the row`() {
        assertEquals(
            FypRelation.OVERRIDDEN,
            settings(tiktokAll = true, allowanceMinutes = 0).tiktokFypRelation(),
        )
    }
}
