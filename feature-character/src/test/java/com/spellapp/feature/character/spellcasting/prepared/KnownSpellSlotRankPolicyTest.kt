package com.spellapp.feature.character.spellcasting.prepared

import com.spellapp.core.model.HeightenTrigger
import com.spellapp.core.model.HeightenedEntry
import com.spellapp.core.model.PreparedSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownSpellSlotRankPolicyTest {
    @Test
    fun nonSignatureSpell_usesOnlyKnownRankSlot() {
        assertTrue(
            knownSpellCanUseSlotRank(
                baseRank = 1,
                knownRank = 1,
                isSignature = false,
                slotRank = 1,
            ),
        )
        assertFalse(
            knownSpellCanUseSlotRank(
                baseRank = 1,
                knownRank = 1,
                isSignature = false,
                slotRank = 2,
            ),
        )
    }

    @Test
    fun signatureSpell_usesKnownRankAndHigherSlots() {
        assertTrue(
            knownSpellCanUseSlotRank(
                baseRank = 1,
                knownRank = 1,
                isSignature = true,
                slotRank = 1,
            ),
        )
        assertTrue(
            knownSpellCanUseSlotRank(
                baseRank = 1,
                knownRank = 1,
                isSignature = true,
                slotRank = 4,
            ),
        )
    }

    @Test
    fun cantrip_usesOnlyCantripSection() {
        assertTrue(
            knownSpellCanUseSlotRank(
                baseRank = 0,
                knownRank = 0,
                isSignature = false,
                slotRank = 0,
            ),
        )
        assertFalse(
            knownSpellCanUseSlotRank(
                baseRank = 0,
                knownRank = 0,
                isSignature = false,
                slotRank = 1,
            ),
        )
    }

    @Test
    fun repertoireRows_includeAllKnownSpellsOnlyAtKnownRank() {
        val signatureSpell = knownSpell(
            id = 1L,
            spellId = "heal",
            knownRank = 1,
            isSignature = true,
        )
        val signatureCantrip = knownSpell(
            id = 2L,
            spellId = "shield",
            baseRank = 0,
            knownRank = 0,
            isSignature = true,
        )
        val ordinarySpell = knownSpell(
            id = 3L,
            spellId = "bless",
            knownRank = 1,
        )

        assertEquals(
            listOf(ordinarySpell, signatureSpell),
            repertoireSpellsForKnownRank(
                spells = listOf(signatureSpell, signatureCantrip, ordinarySpell),
                rank = 1,
            ),
        )
        assertEquals(
            listOf(signatureCantrip),
            repertoireSpellsForKnownRank(
                spells = listOf(signatureSpell, signatureCantrip, ordinarySpell),
                rank = 0,
            ),
        )
        assertEquals(
            emptyList<KnownSpellCastingSummary>(),
            repertoireSpellsForKnownRank(
                spells = listOf(signatureSpell, signatureCantrip, ordinarySpell),
                rank = 2,
            ),
        )
    }

    @Test
    fun allKnownSignatureSpells_stillAppearOnlyAtKnownRank() {
        val rankOneSpell = knownSpell(
            id = 1L,
            spellId = "heal",
            knownRank = 1,
            isSignature = true,
        )
        val rankTwoSpell = knownSpell(
            id = 2L,
            spellId = "dispel-magic",
            knownRank = 2,
            isSignature = true,
        )

        assertEquals(
            listOf(rankOneSpell),
            repertoireSpellsForKnownRank(
                spells = listOf(rankOneSpell, rankTwoSpell),
                rank = 1,
            ),
        )
        assertEquals(
            listOf(rankTwoSpell),
            repertoireSpellsForKnownRank(
                spells = listOf(rankOneSpell, rankTwoSpell),
                rank = 2,
            ),
        )
        assertEquals(
            emptyList<KnownSpellCastingSummary>(),
            repertoireSpellsForKnownRank(
                spells = listOf(rankOneSpell, rankTwoSpell),
                rank = 3,
            ),
        )
    }

    @Test
    fun signatureSlotRankOptions_includeEligibleRanks_andAvailability() {
        val signatureSpell = knownSpell(
            id = 1L,
            spellId = "heal",
            knownRank = 2,
            isSignature = true,
        )
        val slotsByRank = mapOf(
            1 to listOf(slot(rank = 1)),
            2 to listOf(slot(rank = 2, isExpended = true)),
            3 to listOf(slot(rank = 3, isExpended = true), slot(rank = 3)),
        )

        assertEquals(
            listOf(
                SignatureSlotRankOption(rank = 2, availableSlots = 0, totalSlots = 1),
                SignatureSlotRankOption(rank = 3, availableSlots = 1, totalSlots = 2),
            ),
            signatureSlotRankOptions(
                spell = signatureSpell,
                slotsByRank = slotsByRank,
            ),
        )
    }

    @Test
    fun signatureSlotRankOptions_excludeCantrips() {
        assertFalse(
            signatureSlotRankOptions(
                spell = knownSpell(
                    id = 1L,
                    spellId = "shield",
                    baseRank = 0,
                    knownRank = 0,
                    isSignature = true,
                ),
                slotsByRank = mapOf(
                    0 to listOf(slot(rank = 0)),
                    1 to listOf(slot(rank = 1)),
                ),
            ).isNotEmpty(),
        )
    }

    @Test
    fun signatureCastRankPresentations_includeSlotCountsAndAvailability() {
        val signatureSpell = knownSpell(
            id = 1L,
            spellId = "heal",
            knownRank = 1,
            isSignature = true,
        )
        val slotsByRank = mapOf(
            1 to listOf(slot(rank = 1)),
            2 to listOf(slot(rank = 2, isExpended = true), slot(rank = 2, isExpended = true)),
        )

        assertEquals(
            listOf(
                SignatureCastRankPresentation(
                    rank = 1,
                    availableSlots = 1,
                    totalSlots = 1,
                    isAvailable = true,
                    heighteningLines = listOf("Base casting"),
                ),
                SignatureCastRankPresentation(
                    rank = 2,
                    availableSlots = 0,
                    totalSlots = 2,
                    isAvailable = false,
                    heighteningLines = listOf("No listed heightened entry"),
                ),
            ),
            signatureCastRankPresentations(
                spell = signatureSpell,
                slotsByRank = slotsByRank,
            ),
        )
    }

    @Test
    fun signatureCastRankPresentations_includeStepHeightenedEntriesAndBonusDice() {
        val signatureSpell = knownSpell(
            id = 1L,
            spellId = "force-barrage",
            knownRank = 1,
            isSignature = true,
            heightenedEntries = listOf(
                HeightenedEntry(
                    trigger = HeightenTrigger.Step(1),
                    text = "The damage increases by 1d4.",
                ),
            ),
        )
        val slotsByRank = mapOf(
            1 to listOf(slot(rank = 1)),
            3 to listOf(slot(rank = 3)),
        )

        assertEquals(
            listOf(
                "At 3rd: +2d4",
                "Heightened (+1): The damage increases by 1d4.",
            ),
            signatureCastRankPresentations(
                spell = signatureSpell,
                slotsByRank = slotsByRank,
            ).single { it.rank == 3 }.heighteningLines,
        )
    }

    @Test
    fun signatureCastRankPresentations_includeAbsoluteHeightenedEntries() {
        val signatureSpell = knownSpell(
            id = 1L,
            spellId = "invisibility",
            knownRank = 2,
            isSignature = true,
            heightenedEntries = listOf(
                HeightenedEntry(
                    trigger = HeightenTrigger.Absolute(4),
                    text = "The spell lasts 1 minute but no longer ends after a hostile action.",
                ),
            ),
        )
        val slotsByRank = mapOf(
            2 to listOf(slot(rank = 2)),
            4 to listOf(slot(rank = 4)),
        )

        assertEquals(
            listOf("Heightened (4th): The spell lasts 1 minute but no longer ends after a hostile action."),
            signatureCastRankPresentations(
                spell = signatureSpell,
                slotsByRank = slotsByRank,
            ).single { it.rank == 4 }.heighteningLines,
        )
    }

    @Test
    fun signatureCastRankPresentations_excludeCantrips() {
        assertEquals(
            emptyList<SignatureCastRankPresentation>(),
            signatureCastRankPresentations(
                spell = knownSpell(
                    id = 1L,
                    spellId = "shield",
                    baseRank = 0,
                    knownRank = 0,
                    isSignature = true,
                ),
                slotsByRank = mapOf(
                    0 to listOf(slot(rank = 0)),
                    1 to listOf(slot(rank = 1)),
                ),
            ),
        )
    }

    @Test
    fun rankedEmptyRows_areSuppressedForSlotOnlyRanks() {
        assertFalse(
            shouldShowSpontaneousEmptyRankRow(
                rank = 1,
                slots = listOf(slot(rank = 1)),
                knownSpellsForRank = emptyList(),
            ),
        )
        assertTrue(
            shouldShowSpontaneousEmptyRankRow(
                rank = 1,
                slots = emptyList(),
                knownSpellsForRank = emptyList(),
            ),
        )
        assertTrue(
            shouldShowSpontaneousEmptyRankRow(
                rank = 0,
                slots = listOf(slot(rank = 0)),
                knownSpellsForRank = emptyList(),
            ),
        )
    }

    private fun knownSpell(
        id: Long,
        spellId: String,
        baseRank: Int = 1,
        knownRank: Int = baseRank,
        isSignature: Boolean = false,
        heightenedEntries: List<HeightenedEntry> = emptyList(),
    ): KnownSpellCastingSummary {
        return KnownSpellCastingSummary(
            knownSpellId = id,
            spellId = spellId,
            name = spellId,
            baseRank = baseRank,
            knownRank = knownRank,
            isSignature = isSignature,
            castTime = "2 actions",
            range = "",
            traits = emptyList(),
            heightenedEntries = heightenedEntries,
        )
    }

    private fun slot(
        rank: Int,
        isExpended: Boolean = false,
    ): PreparedSlot {
        return PreparedSlot(
            characterId = 1L,
            rank = rank,
            slotIndex = 0,
            isExpended = isExpended,
        )
    }
}
