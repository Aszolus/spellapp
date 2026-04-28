package com.spellapp.feature.character

import com.spellapp.core.model.AbilityScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuilderRulesTest {
    @Test
    fun buildFacts_startsAtZeroAndAppliesBoostsAndFlawsAsModifiers() {
        val slots = listOf(
            slot(
                slotId = "ancestry/flaw/cha",
                groupId = "ancestry-flaws",
                groupLabel = "Dwarf ancestry flaws",
                choices = listOf(AbilityScore.CHARISMA),
                isFlaw = true,
                kind = BuilderAbilityBoostKind.ANCESTRY_FLAW,
            ),
            slot(
                slotId = "ancestry/boost/str",
                groupId = "ancestry-boosts",
                groupLabel = "Dwarf ancestry boosts",
                choices = listOf(AbilityScore.STRENGTH),
                kind = BuilderAbilityBoostKind.ANCESTRY_BOOST,
            ),
            slot(
                slotId = "background/boost/str",
                groupId = "background-boosts",
                groupLabel = "Warrior background boosts",
                choices = listOf(AbilityScore.STRENGTH),
                kind = BuilderAbilityBoostKind.BACKGROUND_BOOST,
            ),
        )

        val facts = BuilderRules.buildFacts(
            catalog = null,
            classId = "wizard",
            ancestryId = null,
            backgroundId = null,
            level = 1,
            abilitySlots = slots,
            selectedAbilityBoosts = emptyMap(),
            skillSlots = emptyList(),
            selectedSkillChoices = emptyMap(),
            selectedFeatSlotOptions = emptyMap(),
        )

        assertEquals(2, facts.abilityModifiers[AbilityScore.STRENGTH])
        assertEquals(-1, facts.abilityModifiers[AbilityScore.CHARISMA])
        assertEquals(0, facts.abilityModifiers[AbilityScore.DEXTERITY])
        assertEquals(3, facts.abilityAdjustments.size)
        assertEquals(0, facts.abilityAdjustments.first().beforeModifier)
        assertEquals(-1, facts.abilityAdjustments.first().afterModifier)
    }

    @Test
    fun buildFacts_ignoresPlannedFutureBoostsUntilLevelIsActive() {
        val slots = listOf(
            slot(
                slotId = "ability/free/1/1",
                groupId = "free-boosts-1",
                label = "Free Level 1 boost 1",
                level = 1,
            ),
            slot(
                slotId = "ability/free/5/1",
                groupId = "free-boosts-5",
                groupLabel = "Level 5 free boosts",
                label = "Free Level 5 boost 1",
                level = 5,
            ),
        )
        val selected = mapOf(
            "ability/free/1/1" to AbilityScore.STRENGTH,
            "ability/free/5/1" to AbilityScore.DEXTERITY,
        )

        val levelOneFacts = factsFor(slots, selected, level = 1)
        val levelFiveFacts = factsFor(slots, selected, level = 5)

        assertEquals(1, levelOneFacts.abilityModifiers[AbilityScore.STRENGTH])
        assertEquals(0, levelOneFacts.abilityModifiers[AbilityScore.DEXTERITY])
        assertEquals(1, levelFiveFacts.abilityModifiers[AbilityScore.DEXTERITY])
    }

    @Test
    fun abilityIssues_blocksDuplicateBoostsWithinSameStep() {
        val slots = listOf(
            slot("ability/free/1/1", "free-boosts-1", groupLabel = "Level 1 free boosts"),
            slot("ability/free/1/2", "free-boosts-1", groupLabel = "Level 1 free boosts"),
        )

        val issues = BuilderRules.abilityIssues(
            slots = slots,
            selectedAbilityBoosts = mapOf(
                "ability/free/1/1" to AbilityScore.DEXTERITY,
                "ability/free/1/2" to AbilityScore.DEXTERITY,
            ),
            activeLevel = 1,
        )

        assertTrue(issues.any { issue ->
            issue.active &&
                issue.message == "DEX is selected twice in Level 1 free boosts." &&
                issue.details == "Choose two different attributes for this step."
        })
    }

    @Test
    fun abilityIssues_allowsSameBoostAcrossDifferentSteps() {
        val slots = listOf(
            slot("ability/background/free", "background-boosts", groupLabel = "Acolyte background boosts"),
            slot("ability/free/1/1", "free-boosts-1", groupLabel = "Level 1 free boosts"),
        )

        val issues = BuilderRules.abilityIssues(
            slots = slots,
            selectedAbilityBoosts = mapOf(
                "ability/background/free" to AbilityScore.WISDOM,
                "ability/free/1/1" to AbilityScore.WISDOM,
            ),
            activeLevel = 1,
        )

        assertTrue(issues.isEmpty())
    }

    @Test
    fun abilityIssues_reportsInvalidStaleSelection() {
        val issues = BuilderRules.abilityIssues(
            slots = listOf(
                slot(
                    slotId = "ability/background/acolyte/boost/free",
                    groupId = "background-acolyte-boosts",
                    groupLabel = "Acolyte background boosts",
                    choices = listOf(AbilityScore.WISDOM, AbilityScore.INTELLIGENCE),
                    kind = BuilderAbilityBoostKind.BACKGROUND_BOOST,
                ),
            ),
            selectedAbilityBoosts = mapOf("ability/background/acolyte/boost/free" to AbilityScore.DEXTERITY),
            activeLevel = 1,
        )

        assertEquals("DEX no longer belongs to Acolyte background boosts.", issues.single().message)
        assertEquals("The selected background changed. Choose one of the listed attributes.", issues.single().details)
    }

    @Test
    fun abilityIssues_flagsLevelOneModifierBounds() {
        val highSlots = (1..5).map { index ->
            slot(
                slotId = "boost/$index",
                groupId = "group-$index",
                choices = listOf(AbilityScore.WISDOM),
            )
        }
        val lowSlots = (1..2).map { index ->
            slot(
                slotId = "flaw/$index",
                groupId = "flaw-group-$index",
                choices = listOf(AbilityScore.CHARISMA),
                isFlaw = true,
                kind = BuilderAbilityBoostKind.ANCESTRY_FLAW,
            )
        }

        val highIssues = BuilderRules.abilityIssues(highSlots, emptyMap(), activeLevel = 1)
        val lowIssues = BuilderRules.abilityIssues(lowSlots, emptyMap(), activeLevel = 1)

        assertTrue(highIssues.any { issue ->
            issue.message == "WIS is +5 at level 1." &&
                issue.details == "Level 1 attribute modifiers cannot be higher than +4."
        })
        assertTrue(lowIssues.any { issue ->
            issue.message == "CHA is -2 at level 1." &&
                issue.details == "Level 1 attribute modifiers cannot be lower than -1."
        })
    }

    @Test
    fun abilityIssues_keepsFutureBoostIssuesNonBlockingUntilLevelIsActive() {
        val slots = listOf(
            slot("ability/free/5/1", "free-boosts-5", level = 5, groupLabel = "Level 5 free boosts"),
            slot("ability/free/5/2", "free-boosts-5", level = 5, groupLabel = "Level 5 free boosts"),
        )

        val issues = BuilderRules.abilityIssues(
            slots = slots,
            selectedAbilityBoosts = mapOf(
                "ability/free/5/1" to AbilityScore.STRENGTH,
                "ability/free/5/2" to AbilityScore.STRENGTH,
            ),
            activeLevel = 1,
        )

        assertEquals(1, issues.size)
        assertFalse(issues.single().active)
        assertEquals("STR is selected twice in Level 5 free boosts.", issues.single().message)
    }

    @Test
    fun abilityBoostSlots_ignoresVoluntaryFlawSlots() {
        val slots = BuilderRules.abilityBoostSlots(
            catalog = null,
            ancestryId = null,
            backgroundId = null,
            classId = "wizard",
            keyAbility = AbilityScore.INTELLIGENCE,
            voluntaryFlawEnabled = true,
        )

        assertFalse(slots.any { slot -> slot.slotId.startsWith("ability/voluntary-flaw/") })
    }

    @Test
    fun skillChoiceSlots_convertsStructuredBackgroundSkillPrompt() {
        val catalog = catalog(
            backgrounds = listOf(
                background(
                    id = "able-carter",
                    name = "Able Carter",
                    choicePrompts = listOf(
                        prompt(
                            promptId = "skill",
                            label = "PF2E.SpecificRule.Prompt.Skill",
                            choices = listOf("deception" to "Deception", "diplomacy" to "Diplomacy"),
                        ),
                    ),
                ),
            ),
        )

        val slots = BuilderRules.skillChoiceSlots(
            catalog = catalog,
            classId = "wizard",
            backgroundId = "able-carter",
        )
        val promptSlot = slots.single { it.kind == BuilderSkillChoiceKind.PROMPT_SKILL }
        val facts = BuilderRules.buildFacts(
            catalog = catalog,
            classId = "wizard",
            ancestryId = null,
            backgroundId = "able-carter",
            level = 1,
            abilitySlots = emptyList(),
            selectedAbilityBoosts = emptyMap(),
            skillSlots = slots,
            selectedSkillChoices = mapOf(promptSlot.slotId to "diplomacy"),
            selectedFeatSlotOptions = emptyMap(),
        )

        assertEquals("Able Carter background skill", promptSlot.label)
        assertEquals(listOf("deception", "diplomacy"), promptSlot.choices)
        assertEquals(BuilderSkillRank.TRAINED, facts.skillRanks["diplomacy"])
    }

    @Test
    fun promptIssues_reportsMissingAndStaleStructuredPromptChoices() {
        val catalog = catalog(
            ancestries = listOf(
                ancestry(
                    id = "automaton",
                    name = "Automaton",
                    choicePrompts = listOf(
                        prompt(
                            promptId = "size",
                            label = "PF2E.SpecificRule.Prompt.CreatureSize",
                            choices = listOf("small" to "Small", "medium" to "Medium"),
                        ),
                    ),
                ),
            ),
        )
        val slots = BuilderRules.promptSlots(
            catalog = catalog,
            ancestryId = "automaton",
            heritageId = null,
            backgroundId = null,
            classId = "wizard",
        )

        val missing = BuilderRules.promptIssues(slots, emptyMap(), activeLevel = 1)
        val stale = BuilderRules.promptIssues(slots, mapOf(slots.single().slotId to "large"), activeLevel = 1)

        assertEquals("Automaton ancestry size", slots.single().label)
        assertTrue(missing.any { issue -> issue.active && issue.message == "Choose automaton ancestry size." })
        assertTrue(stale.any { issue ->
            issue.active &&
                issue.message == "Large no longer belongs to Automaton ancestry size." &&
                issue.details == "The selected source changed. Choose one of the listed options."
        })
    }

    @Test
    fun skillIssues_futureChoicesDoNotBlockUntilLevelIsActive() {
        val slots = listOf(
            BuilderSkillChoiceSlot(
                slotId = "skill/increase/wizard/3/1",
                label = "Level 3 skill increase",
                level = 3,
                kind = BuilderSkillChoiceKind.SKILL_INCREASE,
                choices = listOf("arcana"),
                allowLore = true,
            ),
        )

        val levelOneIssues = BuilderRules.skillIssues(slots, emptyMap(), activeLevel = 1)
        val levelThreeIssues = BuilderRules.skillIssues(slots, emptyMap(), activeLevel = 3)

        assertTrue(levelOneIssues.isEmpty())
        assertEquals("Level 3 skill increase is required.", levelThreeIssues.single().message)
        assertTrue(levelThreeIssues.single().active)
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

    private fun factsFor(
        slots: List<BuilderAbilityBoostSlot>,
        selected: Map<String, AbilityScore>,
        level: Int,
    ): BuildFactSnapshot {
        return BuilderRules.buildFacts(
            catalog = null,
            classId = "wizard",
            ancestryId = null,
            backgroundId = null,
            level = level,
            abilitySlots = slots,
            selectedAbilityBoosts = selected,
            skillSlots = emptyList(),
            selectedSkillChoices = emptyMap(),
            selectedFeatSlotOptions = emptyMap(),
        )
    }

    private fun emptyFacts(): BuildFactSnapshot {
        return BuildFactSnapshot(
            level = 1,
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

    private fun slot(
        slotId: String,
        groupId: String,
        label: String = "Free Level 1 boost",
        level: Int = 1,
        choices: List<AbilityScore> = AbilityScore.entries.toList(),
        isFlaw: Boolean = false,
        kind: BuilderAbilityBoostKind = BuilderAbilityBoostKind.FREE_BOOST,
        groupLabel: String = "Level 1 free boosts",
    ): BuilderAbilityBoostSlot {
        return BuilderAbilityBoostSlot(
            slotId = slotId,
            groupId = groupId,
            label = label,
            level = level,
            choices = choices,
            isFlaw = isFlaw,
            kind = kind,
            groupLabel = groupLabel,
            instruction = "Choose different attributes for this step.",
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

    private fun catalog(
        classes: List<BuilderClassRecord> = listOf(classRecord()),
        ancestries: List<BuilderAncestryRecord> = emptyList(),
        backgrounds: List<BuilderBackgroundRecord> = emptyList(),
    ): CharacterBuilderCatalog {
        return CharacterBuilderCatalog(
            classes = classes,
            ancestries = ancestries,
            heritages = emptyList(),
            backgrounds = backgrounds,
            featIndex = emptyList(),
            feats = emptyList(),
            featShards = emptyList(),
            classFeatures = emptyList(),
            ancestryFeatures = emptyList(),
        )
    }

    private fun classRecord(): BuilderClassRecord {
        return BuilderClassRecord(
            id = "wizard",
            name = "Wizard",
            hp = 6,
            keyAbilityOptions = listOf(AbilityScore.INTELLIGENCE),
            featSlots = emptyList(),
            source = source(),
            traits = traits(),
            description = "",
            warnings = emptyList(),
        )
    }

    private fun ancestry(
        id: String,
        name: String,
        choicePrompts: List<BuilderChoicePromptRecord>,
    ): BuilderAncestryRecord {
        return BuilderAncestryRecord(
            id = id,
            name = name,
            hp = 8,
            speed = "25 feet",
            size = "medium",
            source = source(),
            traits = traits(),
            description = "",
            grants = emptyList(),
            choicePrompts = choicePrompts,
            warnings = emptyList(),
        )
    }

    private fun background(
        id: String,
        name: String,
        choicePrompts: List<BuilderChoicePromptRecord>,
    ): BuilderBackgroundRecord {
        return BuilderBackgroundRecord(
            id = id,
            name = name,
            source = source(),
            traits = traits(),
            description = "",
            grants = emptyList(),
            choicePrompts = choicePrompts,
            warnings = emptyList(),
        )
    }

    private fun prompt(
        promptId: String,
        label: String,
        choices: List<Pair<String, String>>,
    ): BuilderChoicePromptRecord {
        return BuilderChoicePromptRecord(
            promptId = promptId,
            label = label,
            sourceRulePath = "system.rules[0]",
            required = true,
            choiceValues = choices.map { (value, choiceLabel) ->
                BuilderChoiceValueRecord(value = value, label = choiceLabel)
            },
        )
    }

    private fun source(): BuilderSourceRecord {
        return BuilderSourceRecord(title = "Test", license = "Test", remaster = true)
    }

    private fun traits(): BuilderTraitsRecord {
        return BuilderTraitsRecord(rarity = "common", values = emptyList())
    }
}
