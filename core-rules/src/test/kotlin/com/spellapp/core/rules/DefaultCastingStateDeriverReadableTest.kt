package com.spellapp.core.rules

import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.SpellListItem
import com.spellapp.core.model.CharacterBuildOptionType
import com.spellapp.core.model.CharacterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCastingStateDeriverReadableTest {
    private val deriver = DefaultCastingStateDeriver()

    @Test
    fun archetypeSpellcasting_dedicationBasicExpertMaster_matchesExpectedTrackShape() {
        val dedication = source(CharacterBuildOptionType.ARCHETYPE, "archetype/wizard/wizard-dedication", "Wizard Dedication")
        val basic = source(CharacterBuildOptionType.FEAT, "archetype/wizard/basic-wizard-spellcasting", "Basic Wizard Spellcasting")
        val expert = source(CharacterBuildOptionType.FEAT, "archetype/wizard/expert-wizard-spellcasting", "Expert Wizard Spellcasting")
        val master = source(CharacterBuildOptionType.FEAT, "archetype/wizard/master-wizard-spellcasting", "Master Wizard Spellcasting")

        val buildState = CharacterBuildState(
            characterId = 1001L,
            level = 12,
            primaryClass = CharacterClass.CLERIC,
            baseTracks = emptyList(),
            effects = listOf(
                GrantSpellcastingTrackEffect(
                    source = dedication,
                    track = BuildTrackDefinition(
                        trackKey = "archetype-wizard",
                        progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
                        castingStyle = SpellcastingStyle.PREPARED,
                        tradition = SpellTradition.ARCANE,
                        source = dedication,
                    ),
                ),
                GrantCantripsEffect(
                    source = dedication,
                    trackKey = "archetype-wizard",
                    count = 2,
                ),
                GrantPreparedSlotsEffect(
                    source = basic,
                    trackKey = "archetype-wizard",
                    rank = 1,
                    slotCount = 1,
                ),
                GrantPreparedSlotsEffect(
                    source = expert,
                    trackKey = "archetype-wizard",
                    rank = 2,
                    slotCount = 1,
                ),
                GrantPreparedSlotsEffect(
                    source = master,
                    trackKey = "archetype-wizard",
                    rank = 3,
                    slotCount = 1,
                ),
            ),
        )

        val actual = deriver.derive(buildState)

        val expected = ExpectedTrackShape(
            trackKey = "archetype-wizard",
            progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
            castingStyle = SpellcastingStyle.PREPARED,
            tradition = SpellTradition.ARCANE,
            slotCapsByRank = mapOf(
                1 to 1,
                2 to 1,
                3 to 1,
            ),
            grantedCantrips = 2,
            allowedTraditions = setOf(SpellTradition.ARCANE),
            sourceOptionIds = setOf(
                "archetype/wizard/wizard-dedication",
                "archetype/wizard/basic-wizard-spellcasting",
                "archetype/wizard/expert-wizard-spellcasting",
                "archetype/wizard/master-wizard-spellcasting",
            ),
        )

        assertTrue("Expected no warnings, got: ${actual.warnings}", actual.warnings.isEmpty())
        assertEquals("Expected one derived track", 1, actual.tracks.size)
        assertTrackMatchesExpected(actual.tracks.single(), expected)
    }

    @Test
    fun unknownTrackEffect_emitsWarning_insteadOfCrashing() {
        val source = source(CharacterBuildOptionType.FEAT, "archetype/wizard/basic-wizard-spellcasting")
        val result = deriver.derive(
            CharacterBuildState(
                characterId = 1002L,
                level = 8,
                primaryClass = CharacterClass.WIZARD,
                baseTracks = emptyList(),
                effects = listOf(
                    GrantPreparedSlotsEffect(
                        source = source,
                        trackKey = "missing-track",
                        rank = 1,
                        slotCount = 1,
                    ),
                ),
            ),
        )

        assertEquals(0, result.tracks.size)
        assertEquals(1, result.warnings.size)
        assertEquals(CastingWarningCodes.UNKNOWN_TRACK, result.warnings.single().code)
    }

    @Test
    fun derivation_isDeterministic_forSameInput() {
        val source = source(CharacterBuildOptionType.CLASS, "wizard")
        val input = CharacterBuildState(
            characterId = 1003L,
            level = 7,
            primaryClass = CharacterClass.WIZARD,
            baseTracks = listOf(
                BuildTrackDefinition(
                    trackKey = "primary",
                    progressionType = CastingProgressionType.FULL_PREPARED,
                    castingStyle = SpellcastingStyle.PREPARED,
                    tradition = SpellTradition.ARCANE,
                    source = source,
                ),
            ),
            effects = listOf(
                GrantCantripsEffect(
                    source = source,
                    trackKey = "primary",
                    count = 5,
                ),
                GrantPreparedSlotsEffect(
                    source = source,
                    trackKey = "primary",
                    rank = 1,
                    slotCount = 3,
                ),
            ),
        )

        val first = deriver.derive(input)
        val second = deriver.derive(input)
        assertEquals(first, second)
    }

    @Test
    fun filterLegalPreparedSpellTargets_allowsTrackTradition_and_explicitExceptions() {
        val track = DerivedCastingTrackState(
            trackKey = "primary-cleric",
            progressionType = CastingProgressionType.FULL_PREPARED,
            castingStyle = SpellcastingStyle.PREPARED,
            tradition = SpellTradition.DIVINE,
            slotCapsByRank = mapOf(1 to 2),
            grantedCantrips = 5,
            permanentPreparedSpells = emptyList(),
            legalityProfile = TrackSpellLegalityProfile(
                allowedTraditions = setOf(SpellTradition.DIVINE),
                explicitlyAllowedSpellIds = setOf("arcane-exception"),
            ),
            contributingSources = emptySet(),
        )

        val candidates = listOf(
            SpellListItem(id = "divine-ok", name = "Divine Ok", rank = 1, tradition = "divine"),
            SpellListItem(id = "arcane-blocked", name = "Arcane Blocked", rank = 1, tradition = "arcane"),
            SpellListItem(id = "arcane-exception", name = "Arcane Exception", rank = 1, tradition = "arcane"),
        )

        val legal = track.filterLegalPreparedSpellTargets(candidates)

        assertEquals(setOf("divine-ok", "arcane-exception"), legal.map { it.id }.toSet())
        assertFalse(legal.any { it.id == "arcane-blocked" })
    }

    private fun source(
        type: CharacterBuildOptionType,
        optionId: String,
        label: String? = null,
    ): RuleSourceRef {
        return RuleSourceRef(
            optionType = type,
            optionId = optionId,
            label = label,
        )
    }

    private fun assertTrackMatchesExpected(
        actual: DerivedCastingTrackState,
        expected: ExpectedTrackShape,
    ) {
        assertEquals(expected.trackKey, actual.trackKey)
        assertEquals(expected.progressionType, actual.progressionType)
        assertEquals(expected.castingStyle, actual.castingStyle)
        assertEquals(expected.tradition, actual.tradition)
        assertEquals(expected.slotCapsByRank, actual.slotCapsByRank)
        assertEquals(expected.grantedCantrips, actual.grantedCantrips)
        assertEquals(expected.allowedTraditions, actual.legalityProfile.allowedTraditions)
        assertEquals(expected.sourceOptionIds, actual.contributingSources.map { it.optionId }.toSet())
    }

    private data class ExpectedTrackShape(
        val trackKey: String,
        val progressionType: CastingProgressionType,
        val castingStyle: SpellcastingStyle,
        val tradition: SpellTradition,
        val slotCapsByRank: Map<Int, Int>,
        val grantedCantrips: Int,
        val allowedTraditions: Set<SpellTradition>,
        val sourceOptionIds: Set<String>,
    )
}
