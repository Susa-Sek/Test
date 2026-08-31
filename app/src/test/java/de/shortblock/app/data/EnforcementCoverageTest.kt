package de.shortblock.app.data

import de.shortblock.app.service.Feature
import de.shortblock.app.service.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Test, der v0.4.1 nötig gemacht hat.
 *
 * In v0.4.0 war `TIKTOK_FYP` in [BlockSettings.BUDGETABLE] gelistet, aber von keiner
 * Block-Regel abgedeckt — und das Budget-Gatter saß nur im Regel-Pfad. Die Kontingent-Chips in
 * der Oberfläche waren damit reine Attrappe: einstellbar, ohne jede Wirkung, ohne Fehlermeldung.
 *
 * Diese Invariante hätte das beim ersten Lauf gemeldet.
 */
class EnforcementCoverageTest {

    private val ruleFeatures = Rules.BLOCK_RULES.map { it.feature }.toSet()

    @Test
    fun `every feature is enforced by exactly one mechanism`() {
        Feature.entries.forEach { feature ->
            val byRule = feature in ruleFeatures
            val byPolicy = feature in BlockSettings.POLICY_ENFORCED
            assertTrue(
                "$feature wird von keinem Mechanismus durchgesetzt",
                byRule || byPolicy,
            )
            assertTrue(
                "$feature wird doppelt durchgesetzt (Regel und Policy)",
                !(byRule && byPolicy),
            )
        }
    }

    /** Genau die Prüfung, die in v0.4.0 gefehlt hat. */
    @Test
    fun `every budgetable feature is actually enforced somewhere`() {
        BlockSettings.BUDGETABLE.forEach { feature ->
            assertTrue(
                "$feature ist budgetierbar, wird aber nirgends durchgesetzt — das Kontingent " +
                    "wäre eine Attrappe",
                feature in ruleFeatures || feature in BlockSettings.POLICY_ENFORCED,
            )
        }
    }

    /**
     * Seit v0.4.2 ist auch der Ganz-Block budgetierbar — „TikTok ist zu, außer X Minuten“.
     *
     * Der Preis dieser Umkehr ist, dass ein Kontingent den Ganz-Block aufweichen kann. Deshalb
     * hier festgenagelt: Die Voreinstellung ist und bleibt 0 = „Immer“. Wer den Schalter
     * umlegt, bekommt einen Ganz-Block, bis er selbst Minuten wählt.
     */
    @Test
    fun `blocking an app entirely is budgetable but defaults to no allowance`() {
        assertTrue(Feature.TIKTOK_ALL in BlockSettings.BUDGETABLE)
        assertEquals(0, BlockSettings.DEFAULT.budgetMinutes(Feature.TIKTOK_ALL))
        assertEquals(0, BlockSettings.BUDGET_CHOICES.first())
    }

    @Test
    fun `blocking everything is off by default`() {
        assertTrue(Feature.TIKTOK_ALL !in BlockSettings.DEFAULT_ENABLED)
        assertEquals(Feature.entries.size - 1, BlockSettings.DEFAULT_ENABLED.size)
    }

    /**
     * Alle TikTok-Varianten müssen sowohl in der Paketliste stehen als auch eine Sperr-Regel
     * haben — sonst tut die App auf Geräten mit TikTok Lite still gar nichts.
     */
    @Test
    fun `every tiktok package has a total-block rule`() {
        val covered = Rules.BLOCK_RULES
            .filter { it.feature == Feature.TIKTOK_ALL && it.matchAnyWindow }
            .map { it.packageName }
            .toSet()
        assertEquals(de.shortblock.app.service.Packages.TIKTOK, covered)
    }
}
