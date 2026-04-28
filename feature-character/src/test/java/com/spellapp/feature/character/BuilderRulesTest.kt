package com.spellapp.feature.character

import com.spellapp.core.model.AbilityScore
import org.junit.Assert.assertEquals
import org.junit.Test

class BuilderRulesTest {
    @Test
    fun buildFacts_ignoresPlannedFutureBoostsUntilLevelIsActive() {
        val slots = listOf(
            BuilderAbilityBoostSlot(
                slotId = "ability/free/1/1",
                groupId = "free-1",
                label = "Level 1",
                level = 1,
                choices = AbilityScore.entries.toList(),
                isFlaw = false,
            ),
            BuilderAbilityBoostSlot(
                slotId = "ability/free/5/1",
                groupId = "free-5",
                label = "Level 5",
                level = 5,
                choices = AbilityScore.entries.toList(),
                isFlaw = false,
            ),
        )
        val selected = mapOf(
            "ability/free/1/1" to AbilityScore.STRENGTH,
            "ability/free/5/1" to AbilityScore.DEXTERITY,
        )

        val levelOneFacts = BuilderRules.buildFacts(
            catalog = null,
            classId = "wizard",
            ancestryId = null,
            backgroundId = null,
            level = 1,
            abilitySlots = slots,
            selectedAbilityBoosts = selected,
            skillSlots = emptyList(),
            selectedSkillChoices = emptyMap(),
            selectedFeatSlotOptions = emptyMap(),
        )
        val levelFiveFacts = BuilderRules.buildFacts(
            catalog = null,
            classId = "wizard",
            ancestryId = null,
            backgroundId = null,
            level = 5,
            abilitySlots = slots,
            selectedAbilityBoosts = selected,
            skillSlots = emptyList(),
            selectedSkillChoices = emptyMap(),
            selectedFeatSlotOptions = emptyMap(),
        )

        assertEquals(12, levelOneFacts.abilityScores[AbilityScore.STRENGTH])
        assertEquals(10, levelOneFacts.abilityScores[AbilityScore.DEXTERITY])
        assertEquals(12, levelFiveFacts.abilityScores[AbilityScore.DEXTERITY])
    }

    @Test
    fun legalityFor_unparsedPrerequisiteIsNeedsReviewNotEligible() {
        val result = BuilderRules.legalityFor(
            feat = feat(prerequisites = listOf("member of a specific secret society")),
            slot = BuilderFeatSlot(slotId = "wizard/class/2", kind = "class", level = 2),
            facts = emptyFacts(),
            selectedClassId = "wizard",
            selectedAncestryId = null,
            selectedHeritageId = null,
            catalog = null,
        )

        assertEquals(BuilderLegalityStatus.NEEDS_REVIEW, result.status)
    }

    @Test
    fun legalityFor_skillPrerequisiteBlocksWhenRankMissing() {
        val result = BuilderRules.legalityFor(
            feat = feat(prerequisites = listOf("trained in Athletics")),
            slot = BuilderFeatSlot(slotId = "wizard/general/3", kind = "general", level = 3),
            facts = emptyFacts(),
            selectedClassId = "wizard",
            selectedAncestryId = null,
            selectedHeritageId = null,
            catalog = null,
        )

        assertEquals(BuilderLegalityStatus.UNAVAILABLE, result.status)
    }

    private fun emptyFacts(): BuildFactSnapshot {
        return BuildFactSnapshot(
            level = 1,
            abilityScores = AbilityScore.entries.associateWith { 10 },
            abilityModifiers = AbilityScore.entries.associateWith { 0 },
            skillRanks = emptyMap(),
            proficiencyRanks = emptyMap(),
            hp = null,
            perceptionTotal = null,
            saveTotals = emptyMap(),
            skillTotals = emptyMap(),
            selectedFeatIds = emptySet(),
        )
    }

    private fun feat(prerequisites: List<String>): BuilderFeatRecord {
        return BuilderFeatRecord(
            id = "sample-feat",
            name = "Sample Feat",
            category = "class",
            level = 1,
            rarity = "common",
            traits = emptyList(),
            source = BuilderSourceRecord(title = "Test", license = "Test", remaster = true),
            description = "",
            prerequisites = prerequisites,
            grants = emptyList(),
            choicePrompts = emptyList(),
            warnings = emptyList(),
            actionType = "passive",
            actions = null,
            shard = "test",
        )
    }
}
