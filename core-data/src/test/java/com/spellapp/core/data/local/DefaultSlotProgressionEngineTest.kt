package com.spellapp.core.data.local

import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSlotProgressionEngineTest {
    private val engine = DefaultSlotProgressionEngine()

    @Test
    fun archetypeSpellcasting_progression_scalesByLevelAndSelectedTierFeats() {
        val track = archetypeTrack("wizard")
        val selectedOptions = setOf(
            "archetype/wizard/wizard-dedication",
            "archetype/wizard/basic-wizard-spellcasting",
            "archetype/wizard/expert-wizard-spellcasting",
            "archetype/wizard/master-wizard-spellcasting",
        )

        val expectedByLevel = listOf(
            3 to emptyMap(),
            4 to mapOf(1 to 1),
            6 to mapOf(1 to 1, 2 to 1),
            8 to mapOf(1 to 1, 2 to 1, 3 to 1),
            12 to mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1),
            14 to mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1),
            16 to mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1),
            18 to mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1, 7 to 1),
            20 to mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1, 7 to 1, 8 to 1),
        )

        expectedByLevel.forEach { (level, expectedRanks) ->
            val actual = engine.slotCountsByRank(
                level = level,
                track = track,
                selectedBuildOptionIds = selectedOptions,
            )
            assertEquals(
                "Unexpected archetype slot map at level $level",
                expectedRanks,
                actual,
            )
        }
    }

    @Test
    fun archetypeSpellcasting_partialTiers_gateHigherRankSlots() {
        val track = archetypeTrack("wizard")
        val selectedOptions = setOf(
            "archetype/wizard/wizard-dedication",
            "archetype/wizard/basic-wizard-spellcasting",
        )

        val level20 = engine.slotCountsByRank(
            level = 20,
            track = track,
            selectedBuildOptionIds = selectedOptions,
        )
        assertEquals(
            mapOf(1 to 1, 2 to 1, 3 to 1),
            level20,
        )

        val withExpert = engine.slotCountsByRank(
            level = 20,
            track = track,
            selectedBuildOptionIds = selectedOptions + "archetype/wizard/expert-wizard-spellcasting",
        )
        assertEquals(
            mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1),
            withExpert,
        )
    }

    @Test
    fun archetypeSpellcasting_tierDetection_ignoresFalsePositiveOptionIds() {
        val track = archetypeTrack("wizard")
        val selectedOptions = setOf(
            "archetype/wizard/wizard-dedication",
            "archetype/wizard/non-basic-spellcasting",
            "archetype/wizard/basic-spellcasting-focus",
        )

        val actual = engine.slotCountsByRank(
            level = 20,
            track = track,
            selectedBuildOptionIds = selectedOptions,
        )

        assertEquals(
            emptyMap<Int, Int>(),
            actual,
        )
    }

    @Test
    fun archetypeSpellcasting_dedicationOnly_grantsNoSlots() {
        val track = archetypeTrack("wizard")
        val selectedOptions = setOf("archetype/wizard/wizard-dedication")

        val actual = engine.slotCountsByRank(
            level = 20,
            track = track,
            selectedBuildOptionIds = selectedOptions,
        )

        assertEquals(emptyMap<Int, Int>(), actual)
    }

    @Test
    fun archetypeSpellcasting_legacyTrackWithoutBuildOptions_usesLegacyFallback() {
        val legacyTrack = CastingTrack(
            characterId = 1L,
            trackKey = "archetype-legacy",
            sourceType = CastingTrackSourceType.ARCHETYPE,
            sourceId = "Legacy Archetype",
            progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
        )

        val actual = engine.slotCountsByRank(
            level = 10,
            track = legacyTrack,
            selectedBuildOptionIds = emptySet(),
        )

        assertEquals(
            mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1),
            actual,
        )
    }

    @Test
    fun archetypeSpellcasting_modernTrackWithoutBuildOptions_grantsNoSlots() {
        val modernTrack = archetypeTrack("wizard")

        val actual = engine.slotCountsByRank(
            level = 20,
            track = modernTrack,
            selectedBuildOptionIds = emptySet(),
        )

        assertEquals(emptyMap<Int, Int>(), actual)
    }

    @Test
    fun fullPrepared_progression_unchanged() {
        val fullTrack = CastingTrack(
            characterId = 1L,
            trackKey = "primary",
            sourceType = CastingTrackSourceType.PRIMARY_CLASS,
            sourceId = "WIZARD",
            progressionType = CastingProgressionType.FULL_PREPARED,
        )

        val actual = engine.slotCountsByRank(
            level = 5,
            track = fullTrack,
            selectedBuildOptionIds = emptySet(),
        )

        assertEquals(
            mapOf(0 to 5, 1 to 3, 2 to 3, 3 to 2),
            actual,
        )
    }

    @Test
    fun fullPrepared_newRankUnlocksWithTwoSlots_thenThreeNextLevel() {
        val fullTrack = CastingTrack(
            characterId = 1L,
            trackKey = "primary",
            sourceType = CastingTrackSourceType.PRIMARY_CLASS,
            sourceId = "WIZARD",
            progressionType = CastingProgressionType.FULL_PREPARED,
        )

        val level5 = engine.slotCountsByRank(
            level = 5,
            track = fullTrack,
            selectedBuildOptionIds = emptySet(),
        )
        val level6 = engine.slotCountsByRank(
            level = 6,
            track = fullTrack,
            selectedBuildOptionIds = emptySet(),
        )

        assertEquals(2, level5[3])
        assertEquals(3, level6[3])
    }

    private fun archetypeTrack(archetypeId: String): CastingTrack {
        return CastingTrack(
            characterId = 1L,
            trackKey = "archetype-$archetypeId",
            sourceType = CastingTrackSourceType.ARCHETYPE,
            sourceId = archetypeId,
            progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
        )
    }
}
