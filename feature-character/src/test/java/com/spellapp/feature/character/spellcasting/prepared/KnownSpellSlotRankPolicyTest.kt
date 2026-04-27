package com.spellapp.feature.character.spellcasting.prepared

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
}
