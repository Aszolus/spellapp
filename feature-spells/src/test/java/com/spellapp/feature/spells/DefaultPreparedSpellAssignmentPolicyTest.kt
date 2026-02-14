package com.spellapp.feature.spells

import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SpellListItem
import com.spellapp.core.rules.SpellTradition
import com.spellapp.core.rules.TrackSpellExceptionPolicy
import com.spellapp.core.rules.TrackSpellLegalityProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPreparedSpellAssignmentPolicyTest {
    private val policy = DefaultPreparedSpellAssignmentPolicy()

    @Test
    fun clericPrimaryTrack_allowsDivineSpell_and_rejectsArcaneSpell() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.CLERIC,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            slotRank = 3,
        )
        val divineSpell = spell(
            id = "heal",
            rank = 1,
            tradition = "divine",
        )
        val arcaneSpell = spell(
            id = "magic-missile",
            rank = 1,
            tradition = "arcane",
        )

        assertTrue(policy.isSpellLegalTarget(divineSpell, context))
        assertFalse(policy.isSpellLegalTarget(arcaneSpell, context))
    }

    @Test
    fun multiTraditionSpell_is_legal_when_any_tradition_matches_track() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.CLERIC,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            slotRank = 2,
        )
        val heal = spell(
            id = "heal",
            rank = 1,
            tradition = "divine, primal, occult",
        )

        assertTrue(policy.isSpellLegalTarget(heal, context))
    }

    @Test
    fun archetypeTrack_uses_archetype_tradition_not_primary_class_tradition() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.CLERIC,
            trackKey = "archetype-wizard",
            slotRank = 1,
        )
        val arcaneSpell = spell(
            id = "magic-missile",
            rank = 1,
            tradition = "arcane",
        )
        val divineSpell = spell(
            id = "heal",
            rank = 1,
            tradition = "divine",
        )

        assertTrue(policy.isSpellLegalTarget(arcaneSpell, context))
        assertFalse(policy.isSpellLegalTarget(divineSpell, context))
    }

    @Test
    fun unknownArchetypeTrack_is_restrictive_by_default() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.CLERIC,
            trackKey = "archetype-bard",
            slotRank = 1,
        )
        val occultSpell = spell(
            id = "phantom-pain",
            rank = 1,
            tradition = "occult",
        )

        assertFalse(policy.isSpellLegalTarget(occultSpell, context))
    }

    @Test
    fun injectedLegalityProfile_can_allow_explicit_spell_exception() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.WIZARD,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            slotRank = 1,
        )
        val profileSource = PreparedTrackLegalityProfileSource {
            TrackSpellLegalityProfile(
                allowedTraditions = setOf(SpellTradition.ARCANE),
                explicitlyAllowedSpellIds = setOf("heal"),
            )
        }
        val policyWithOverrides = DefaultPreparedSpellAssignmentPolicy(profileSource)
        val divineSpellWithException = spell(
            id = "heal",
            rank = 1,
            tradition = "divine",
        )

        assertTrue(policyWithOverrides.isSpellLegalTarget(divineSpellWithException, context))
    }

    @Test
    fun injectedLegalityProfile_allowAny_policy_allows_any_tradition() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.WIZARD,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            slotRank = 1,
        )
        val profileSource = PreparedTrackLegalityProfileSource {
            TrackSpellLegalityProfile(
                allowedTraditions = emptySet(),
                exceptionPolicy = TrackSpellExceptionPolicy.ALLOW_ANY,
            )
        }
        val policyWithAllowAny = DefaultPreparedSpellAssignmentPolicy(profileSource)
        val divineSpell = spell(
            id = "heal",
            rank = 1,
            tradition = "divine",
        )

        assertTrue(policyWithAllowAny.isSpellLegalTarget(divineSpell, context))
    }

    @Test
    fun preparedHeightening_allows_lower_rank_spell_in_higher_rank_slot() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.WIZARD,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            slotRank = 4,
        )
        val rankThreeSpell = spell(
            id = "fireball",
            rank = 3,
            tradition = "arcane",
        )

        assertTrue(policy.isSpellLegalTarget(rankThreeSpell, context))
    }

    @Test
    fun preparedHeightening_rejects_spell_rank_higher_than_slot_rank() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.WIZARD,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            slotRank = 3,
        )
        val rankFourSpell = spell(
            id = "resilient-sphere",
            rank = 4,
            tradition = "arcane",
        )

        assertFalse(policy.isSpellLegalTarget(rankFourSpell, context))
    }

    @Test
    fun cantrip_slot_accepts_only_cantrips() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.WIZARD,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            slotRank = 0,
        )
        val cantrip = spell(
            id = "shield",
            rank = 0,
            tradition = "arcane",
        )
        val rankOneSpell = spell(
            id = "mage-armor",
            rank = 1,
            tradition = "arcane",
        )

        assertTrue(policy.isSpellLegalTarget(cantrip, context))
        assertFalse(policy.isSpellLegalTarget(rankOneSpell, context))
    }

    @Test
    fun nonCantrip_slot_rejects_cantrips() {
        val context = PreparedSlotAssignmentContext(
            characterClass = CharacterClass.WIZARD,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            slotRank = 1,
        )
        val cantrip = spell(
            id = "shield",
            rank = 0,
            tradition = "arcane",
        )

        assertFalse(policy.isSpellLegalTarget(cantrip, context))
    }

    private fun spell(
        id: String,
        rank: Int,
        tradition: String,
    ): SpellListItem {
        return SpellListItem(
            id = id,
            name = id,
            rank = rank,
            tradition = tradition,
        )
    }
}
