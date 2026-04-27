package com.spellapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellAllowanceSummaryTest {
    @Test
    fun repertoireAllowance_reportsPerRankOverageWarnings() {
        val rule = SpellAllowanceRule(
            trackKey = "primary",
            kind = SpellAllowanceKind.REPERTOIRE,
            label = "Repertoire",
            policy = SpellAllowancePolicy.CAP,
            countsByLevel = mapOf(1 to mapOf(0 to 2, 1 to 1)),
        )
        val summaries = buildSpellAllowanceSummaries(
            rules = listOf(rule),
            characterLevel = 1,
            knownSpells = listOf(
                knownSpell("cantrip-a", rank = 0),
                knownSpell("cantrip-b", rank = 0),
                knownSpell("rank-one-a", rank = 1),
                knownSpell("rank-one-b", rank = 1),
            ),
        )

        assertNull(summaries.first { it.rank == 0 }.warning)
        assertEquals("1 over expected repertoire", summaries.first { it.rank == 1 }.warning)
    }

    @Test
    fun spellbookMinimum_warnsOnlyWhenBelowMinimum() {
        val rule = SpellAllowanceRule(
            trackKey = "primary",
            kind = SpellAllowanceKind.SPELLBOOK_MINIMUM,
            label = "Spellbook minimum",
            policy = SpellAllowancePolicy.MINIMUM,
            totalsByLevel = mapOf(1 to 3),
        )

        val lowSummary = buildSpellAllowanceSummaries(
            rules = listOf(rule),
            characterLevel = 1,
            knownSpells = listOf(knownSpell("one", rank = 1)),
        ).single()
        assertEquals("2 below expected minimum", lowSummary.warning)

        val highSummary = buildSpellAllowanceSummaries(
            rules = listOf(rule),
            characterLevel = 1,
            knownSpells = listOf(
                knownSpell("one", rank = 1),
                knownSpell("two", rank = 1),
                knownSpell("three", rank = 1),
                knownSpell("four", rank = 1),
            ),
        ).single()
        assertNull(highSummary.warning)
    }

    @Test
    fun allKnownSignatureRuleDoesNotRequireManualSignatureFlags() {
        val rule = SpellAllowanceRule(
            trackKey = "primary",
            kind = SpellAllowanceKind.SIGNATURE_SPELLS,
            label = "Signature spells",
            policy = SpellAllowancePolicy.ALL_KNOWN,
        )

        val summary = buildSpellAllowanceSummaries(
            rules = listOf(rule),
            characterLevel = 5,
            knownSpells = listOf(
                knownSpell("one", rank = 1),
                knownSpell("two", rank = 2),
            ),
        ).single()

        assertEquals(2, summary.actual)
        assertTrue(summary.warning == null)
    }

    private fun knownSpell(
        spellId: String,
        rank: Int,
    ): KnownSpell {
        return KnownSpell(
            characterId = 1L,
            trackKey = "primary",
            spellId = spellId,
            knownRank = rank,
        )
    }
}
