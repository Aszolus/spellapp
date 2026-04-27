package com.spellapp.core.data.local

import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.ClassSpellcastingCatalog
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DefaultSlotProgressionEngineTest {
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        ClassSpellcastingCatalogJsonParser.parse(readClassSpellcastingAsset()).also { source ->
            ClassSpellcastingCatalog.install(source)
        }
    private val engine = DefaultSlotProgressionEngine(classSpellcastingCatalogSource)

    @Test
    fun slotCastingClassProgressions_matchMilestoneTables() {
        val expectedByClassAndTrack = mapOf(
            "animist:primary" to mapOf(
                1 to slots(0 to 2, 1 to 1),
                5 to slots(0 to 2, 1 to 2, 2 to 2, 3 to 1),
                10 to slots(0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2),
                15 to slots(0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 1),
                19 to slots(0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 2, 9 to 2),
                20 to slots(0 to 2, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 2, 9 to 2),
            ),
            "animist:apparition" to mapOf(
                1 to slots(0 to 2, 1 to 1),
                5 to slots(0 to 2, 1 to 1, 2 to 1, 3 to 1),
                10 to slots(0 to 3, 1 to 2, 2 to 2, 3 to 2, 4 to 1, 5 to 1),
                15 to slots(0 to 4, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 1, 8 to 1),
                19 to slots(0 to 4, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 2, 9 to 1, 10 to 1),
                20 to slots(0 to 4, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 2, 9 to 1, 10 to 1),
            ),
            "bard:primary" to fullThreeSlotCaster(),
            "cleric:primary" to fullThreeSlotCaster(),
            "druid:primary" to fullThreeSlotCaster(),
            "magus:primary" to boundedCaster(),
            "oracle:primary" to fullFourSlotCaster(),
            "psychic:primary" to psychicCaster(),
            "sorcerer:primary" to fullFourSlotCaster(),
            "summoner:primary" to boundedCaster(),
            "witch:primary" to fullThreeSlotCaster(),
            "wizard:primary" to fullThreeSlotCaster(),
        )

        val actualKeys = mutableSetOf<String>()
        ClassSpellcastingCatalog.allDefinitions().forEach { definition ->
            definition.primaryTracks.forEach { trackDefinition ->
                val key = "${definition.classId}:${trackDefinition.trackKey}"
                actualKeys += key
                val track = CastingTrack(
                    characterId = 1L,
                    trackKey = trackDefinition.trackKey,
                    sourceType = CastingTrackSourceType.PRIMARY_CLASS,
                    sourceId = definition.classId,
                    progressionType = trackDefinition.progressionType,
                    displayName = trackDefinition.displayName,
                    castingStyle = trackDefinition.castingStyle,
                    tradition = trackDefinition.tradition,
                    slotProgressionKey = trackDefinition.slotProgressionKey,
                )
                expectedByClassAndTrack.getValue(key).forEach { (level, expected) ->
                    assertEquals(
                        "Unexpected slot table for $key at level $level",
                        expected,
                        engine.slotCountsByRank(
                            level = level,
                            track = track,
                            selectedBuildOptionIds = emptySet(),
                        ),
                    )
                }
            }
        }

        assertEquals(expectedByClassAndTrack.keys, actualKeys)
    }

    @Test
    fun classCatalog_usesGenericSlotProgressionKeys() {
        val classIds = ClassSpellcastingCatalog.allDefinitions()
            .map { definition -> definition.classId }
            .toSet()
        val progressionKeys = ClassSpellcastingCatalog.allDefinitions()
            .flatMap { definition -> definition.primaryTracks }
            .map { track -> track.slotProgressionKey }

        assertTrue(progressionKeys.none { key -> key in classIds })
    }

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

    private fun slots(vararg entries: Pair<Int, Int>): Map<Int, Int> = mapOf(*entries)

    private fun fullThreeSlotCaster(): Map<Int, Map<Int, Int>> = mapOf(
        1 to slots(0 to 5, 1 to 2),
        5 to slots(0 to 5, 1 to 3, 2 to 3, 3 to 2),
        10 to slots(0 to 5, 1 to 3, 2 to 3, 3 to 3, 4 to 3, 5 to 3),
        15 to slots(0 to 5, 1 to 3, 2 to 3, 3 to 3, 4 to 3, 5 to 3, 6 to 3, 7 to 3, 8 to 2),
        19 to slots(0 to 5, 1 to 3, 2 to 3, 3 to 3, 4 to 3, 5 to 3, 6 to 3, 7 to 3, 8 to 3, 9 to 3, 10 to 1),
        20 to slots(0 to 5, 1 to 3, 2 to 3, 3 to 3, 4 to 3, 5 to 3, 6 to 3, 7 to 3, 8 to 3, 9 to 3, 10 to 1),
    )

    private fun fullFourSlotCaster(): Map<Int, Map<Int, Int>> = mapOf(
        1 to slots(0 to 5, 1 to 3),
        5 to slots(0 to 5, 1 to 4, 2 to 4, 3 to 3),
        10 to slots(0 to 5, 1 to 4, 2 to 4, 3 to 4, 4 to 4, 5 to 4),
        15 to slots(0 to 5, 1 to 4, 2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 4, 7 to 4, 8 to 3),
        19 to slots(0 to 5, 1 to 4, 2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 4, 7 to 4, 8 to 4, 9 to 4, 10 to 1),
        20 to slots(0 to 5, 1 to 4, 2 to 4, 3 to 4, 4 to 4, 5 to 4, 6 to 4, 7 to 4, 8 to 4, 9 to 4, 10 to 1),
    )

    private fun psychicCaster(): Map<Int, Map<Int, Int>> = mapOf(
        1 to slots(0 to 3, 1 to 1),
        5 to slots(0 to 3, 1 to 2, 2 to 2, 3 to 1),
        10 to slots(0 to 3, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2),
        15 to slots(0 to 3, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 1),
        19 to slots(0 to 3, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 2, 9 to 2, 10 to 1),
        20 to slots(0 to 3, 1 to 2, 2 to 2, 3 to 2, 4 to 2, 5 to 2, 6 to 2, 7 to 2, 8 to 2, 9 to 2, 10 to 2),
    )

    private fun boundedCaster(): Map<Int, Map<Int, Int>> = mapOf(
        1 to slots(0 to 5, 1 to 1),
        5 to slots(0 to 5, 2 to 2, 3 to 2),
        10 to slots(0 to 5, 4 to 2, 5 to 2),
        15 to slots(0 to 5, 7 to 2, 8 to 2),
        19 to slots(0 to 5, 8 to 2, 9 to 2),
        20 to slots(0 to 5, 8 to 2, 9 to 2),
    )

    private fun readClassSpellcastingAsset(): String {
        val candidates = listOf(
            File("app/src/main/assets/class-spellcasting.normalized.json"),
            File("../app/src/main/assets/class-spellcasting.normalized.json"),
            File("../../app/src/main/assets/class-spellcasting.normalized.json"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("class-spellcasting.normalized.json not found from ${File(".").absolutePath}")
    }
}
