package com.spellapp.feature.character

import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.normalizeClassId

enum class BuilderSkillRank(val value: Int, val label: String) {
    UNTRAINED(0, "Untrained"),
    TRAINED(1, "Trained"),
    EXPERT(2, "Expert"),
    MASTER(3, "Master"),
    LEGENDARY(4, "Legendary");

    fun increase(): BuilderSkillRank = entries.firstOrNull { it.value == value + 1 } ?: this
}

data class BuilderSkillDefinition(
    val id: String,
    val label: String,
    val ability: AbilityScore,
)

enum class BuilderAbilityBoostKind {
    ANCESTRY_FLAW,
    ANCESTRY_BOOST,
    BACKGROUND_BOOST,
    CLASS_KEY,
    FREE_BOOST,
}

data class BuilderAbilityBoostSlot(
    val slotId: String,
    val groupId: String,
    val label: String,
    val level: Int,
    val choices: List<AbilityScore>,
    val isFlaw: Boolean,
    val kind: BuilderAbilityBoostKind = if (isFlaw) BuilderAbilityBoostKind.ANCESTRY_FLAW else BuilderAbilityBoostKind.FREE_BOOST,
    val groupLabel: String = label,
    val instruction: String = "",
    val required: Boolean = true,
) {
    val fixedChoice: AbilityScore? get() = choices.singleOrNull()
}

data class BuilderAbilityAdjustment(
    val slotId: String,
    val groupId: String,
    val groupLabel: String,
    val slotLabel: String,
    val level: Int,
    val ability: AbilityScore,
    val beforeModifier: Int,
    val afterModifier: Int,
    val delta: Int,
    val active: Boolean,
    val isFlaw: Boolean,
)

data class BuilderSkillChoiceSlot(
    val slotId: String,
    val label: String,
    val level: Int,
    val kind: BuilderSkillChoiceKind,
    val choices: List<String>,
    val allowLore: Boolean,
    val required: Boolean = true,
    val sourceLabel: String = "",
    val instruction: String = "",
)

enum class BuilderSkillChoiceKind {
    TRAINED_SKILL,
    SKILL_INCREASE,
    PROMPT_SKILL,
}

enum class BuilderPromptSource {
    ANCESTRY,
    HERITAGE,
    BACKGROUND,
    CLASS,
}

data class BuilderPromptSlot(
    val slotId: String,
    val label: String,
    val level: Int,
    val source: BuilderPromptSource,
    val sourceLabel: String,
    val choices: List<BuilderChoiceValueRecord>,
    val required: Boolean = true,
    val instruction: String = "",
)

data class BuilderIssue(
    val slotId: String,
    val message: String,
    val active: Boolean,
    val details: String? = null,
    val relatedSlotIds: Set<String> = emptySet(),
    val level: Int? = null,
)

data class BuildFactSnapshot(
    val level: Int,
    val abilityModifiers: Map<AbilityScore, Int>,
    val abilityAdjustments: List<BuilderAbilityAdjustment> = emptyList(),
    val skillRanks: Map<String, BuilderSkillRank>,
    val proficiencyRanks: Map<String, Int>,
    val hp: Int?,
    val perceptionTotal: Int?,
    val saveTotals: Map<String, Int>,
    val skillTotals: Map<String, Int>,
    val selectedFeatIds: Set<String>,
)

data class BuilderFeatLegality(
    val featId: String,
    val status: BuilderLegalityStatus,
    val reasons: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val requiresOverride: Boolean get() = status != BuilderLegalityStatus.ELIGIBLE
}

enum class BuilderLegalityStatus {
    ELIGIBLE,
    NEEDS_REVIEW,
    UNAVAILABLE,
}

data class BuilderPrerequisiteLookup(
    val classIdsByName: Map<String, String> = emptyMap(),
    val ancestryIdsByName: Map<String, String> = emptyMap(),
    val featIdsByName: Map<String, String> = emptyMap(),
)

object BuilderRules {
    val standardSkills: List<BuilderSkillDefinition> = listOf(
        BuilderSkillDefinition("acrobatics", "Acrobatics", AbilityScore.DEXTERITY),
        BuilderSkillDefinition("arcana", "Arcana", AbilityScore.INTELLIGENCE),
        BuilderSkillDefinition("athletics", "Athletics", AbilityScore.STRENGTH),
        BuilderSkillDefinition("crafting", "Crafting", AbilityScore.INTELLIGENCE),
        BuilderSkillDefinition("deception", "Deception", AbilityScore.CHARISMA),
        BuilderSkillDefinition("diplomacy", "Diplomacy", AbilityScore.CHARISMA),
        BuilderSkillDefinition("intimidation", "Intimidation", AbilityScore.CHARISMA),
        BuilderSkillDefinition("medicine", "Medicine", AbilityScore.WISDOM),
        BuilderSkillDefinition("nature", "Nature", AbilityScore.WISDOM),
        BuilderSkillDefinition("occultism", "Occultism", AbilityScore.INTELLIGENCE),
        BuilderSkillDefinition("performance", "Performance", AbilityScore.CHARISMA),
        BuilderSkillDefinition("religion", "Religion", AbilityScore.WISDOM),
        BuilderSkillDefinition("society", "Society", AbilityScore.INTELLIGENCE),
        BuilderSkillDefinition("stealth", "Stealth", AbilityScore.DEXTERITY),
        BuilderSkillDefinition("survival", "Survival", AbilityScore.WISDOM),
        BuilderSkillDefinition("thievery", "Thievery", AbilityScore.DEXTERITY),
    )
    val standardSkillsById: Map<String, BuilderSkillDefinition> = standardSkills.associateBy { it.id }

    fun abilityBoostSlots(
        catalog: CharacterBuilderCatalog?,
        ancestryId: String?,
        backgroundId: String?,
        classId: String,
        keyAbility: AbilityScore,
        voluntaryFlawEnabled: Boolean,
    ): List<BuilderAbilityBoostSlot> {
        val ancestry = ancestryId?.let { catalog?.ancestriesById?.get(it) }
        val background = backgroundId?.let { catalog?.backgroundsById?.get(it) }
        val slots = mutableListOf<BuilderAbilityBoostSlot>()
        ancestry?.flaws.orEmpty().forEach { flaw ->
            slots += flaw.toBoostSlot(
                slotId = "ability/ancestry/${ancestry?.id}/flaw/${flaw.id}",
                groupId = "ancestry-${ancestry?.id}-flaws",
                label = "${ancestry?.name} ancestry flaw",
                level = 1,
                isFlaw = true,
                kind = BuilderAbilityBoostKind.ANCESTRY_FLAW,
                groupLabel = "${ancestry?.name} ancestry flaws",
                instruction = "Apply each ancestry flaw; a flaw subtracts 1 from that attribute modifier.",
            )
        }
        ancestry?.boosts.orEmpty().forEach { boost ->
            slots += boost.toBoostSlot(
                slotId = "ability/ancestry/${ancestry?.id}/boost/${boost.id}",
                groupId = "ancestry-${ancestry?.id}-boosts",
                label = "${ancestry?.name} ancestry boost",
                level = 1,
                isFlaw = false,
                kind = BuilderAbilityBoostKind.ANCESTRY_BOOST,
                groupLabel = "${ancestry?.name} ancestry boosts",
                instruction = "Choose each ancestry boost; boosts in this step must use different attributes.",
            )
        }
        background?.boosts.orEmpty().forEach { boost ->
            slots += boost.toBoostSlot(
                slotId = "ability/background/${background?.id}/boost/${boost.id}",
                groupId = "background-${background?.id}-boosts",
                label = "${background?.name} background boost",
                level = 1,
                isFlaw = false,
                kind = BuilderAbilityBoostKind.BACKGROUND_BOOST,
                groupLabel = "${background?.name} background boosts",
                instruction = "Choose each background boost; boosts in this step must use different attributes.",
            )
        }
        slots += BuilderAbilityBoostSlot(
            slotId = "ability/class/${normalizeClassId(classId)}/key",
            groupId = "class-${normalizeClassId(classId)}-key",
            label = "Class key ability",
            level = 1,
            choices = listOf(keyAbility),
            isFlaw = false,
            kind = BuilderAbilityBoostKind.CLASS_KEY,
            groupLabel = "Class key attribute",
            instruction = "Your class grants this key attribute boost.",
        )
        listOf(1, 5, 10, 15, 20).forEach { level ->
            repeat(4) { index ->
                slots += BuilderAbilityBoostSlot(
                    slotId = "ability/free/$level/${index + 1}",
                    groupId = "free-boosts-$level",
                    label = "Free Level $level boost ${index + 1}",
                    level = level,
                    choices = AbilityScore.entries.toList(),
                    isFlaw = false,
                    kind = BuilderAbilityBoostKind.FREE_BOOST,
                    groupLabel = "Level $level free boosts",
                    instruction = "Choose four different attributes for Level $level.",
                )
            }
        }
        return slots
    }

    fun skillChoiceSlots(
        catalog: CharacterBuilderCatalog?,
        classId: String,
        ancestryId: String? = null,
        heritageId: String? = null,
        backgroundId: String? = null,
    ): List<BuilderSkillChoiceSlot> {
        val classRecord = catalog?.classesById?.get(normalizeClassId(classId)) ?: return emptyList()
        val ancestry = ancestryId?.let { catalog.ancestriesById[it] }
        val heritage = heritageId?.let { catalog.heritagesById[it] }
        val background = backgroundId?.let { catalog.backgroundsById[it] }
        val slots = mutableListOf<BuilderSkillChoiceSlot>()
        slots += promptSkillChoiceSlots(
            ancestry = ancestry,
            heritage = heritage,
            background = background,
            classRecord = classRecord,
        )
        val fixedSkills = classRecord.trainedSkills.value.toSet()
        val classChoices = standardSkills.map { it.id }.filterNot { it in fixedSkills }
        repeat(classRecord.trainedSkills.additional ?: 0) { index ->
            slots += BuilderSkillChoiceSlot(
                slotId = "skill/class/${classRecord.id}/trained/${index + 1}",
                label = "Class trained skill ${index + 1}",
                level = 1,
                kind = BuilderSkillChoiceKind.TRAINED_SKILL,
                choices = classChoices,
                allowLore = false,
                sourceLabel = "${classRecord.name} class",
                instruction = "Choose a skill your class makes you trained in at 1st level.",
            )
        }
        classRecord.skillIncreaseLevels.forEachIndexed { index, level ->
            slots += BuilderSkillChoiceSlot(
                slotId = "skill/increase/${classRecord.id}/$level/${index + 1}",
                label = "Level $level skill increase",
                level = level,
                kind = BuilderSkillChoiceKind.SKILL_INCREASE,
                choices = standardSkills.map { it.id },
                allowLore = true,
                sourceLabel = "${classRecord.name} class advancement",
                instruction = "Increase one skill proficiency when this level is active.",
            )
        }
        return slots
    }

    fun promptSlots(
        catalog: CharacterBuilderCatalog?,
        ancestryId: String?,
        heritageId: String?,
        backgroundId: String?,
        classId: String,
    ): List<BuilderPromptSlot> {
        if (catalog == null) return emptyList()
        val ancestry = ancestryId?.let(catalog.ancestriesById::get)
        val heritage = heritageId?.let(catalog.heritagesById::get)
        val background = backgroundId?.let(catalog.backgroundsById::get)
        val classRecord = catalog.classesById[normalizeClassId(classId)]
        return buildList {
            ancestry?.choicePrompts.orEmpty()
                .filter { prompt -> prompt.skillChoiceIds() == null }
                .mapPromptSlots(
                    source = BuilderPromptSource.ANCESTRY,
                    sourceId = ancestry?.id.orEmpty(),
                    sourceLabel = "${ancestry?.name.orEmpty()} ancestry",
                    into = this,
                )
            heritage?.choicePrompts.orEmpty()
                .filter { prompt -> prompt.skillChoiceIds() == null }
                .mapPromptSlots(
                    source = BuilderPromptSource.HERITAGE,
                    sourceId = heritage?.id.orEmpty(),
                    sourceLabel = "${heritage?.name.orEmpty()} heritage",
                    into = this,
                )
            background?.choicePrompts.orEmpty()
                .filter { prompt -> prompt.skillChoiceIds() == null }
                .mapPromptSlots(
                    source = BuilderPromptSource.BACKGROUND,
                    sourceId = background?.id.orEmpty(),
                    sourceLabel = "${background?.name.orEmpty()} background",
                    into = this,
                )
            classRecord?.choicePrompts.orEmpty()
                .filter { prompt -> prompt.skillChoiceIds() == null }
                .mapPromptSlots(
                    source = BuilderPromptSource.CLASS,
                    sourceId = classRecord?.id.orEmpty(),
                    sourceLabel = "${classRecord?.name.orEmpty()} class",
                    into = this,
                )
        }
    }

    fun abilityIssues(
        slots: List<BuilderAbilityBoostSlot>,
        selectedAbilityBoosts: Map<String, AbilityScore>,
        activeLevel: Int,
    ): List<BuilderIssue> {
        val issues = mutableListOf<BuilderIssue>()
        slots.forEach { slot ->
            val active = slot.level <= activeLevel
            val savedSelection = selectedAbilityBoosts[slot.slotId]
            val selected = slot.fixedChoice ?: savedSelection
            if (slot.required && active && selected == null) {
                issues += BuilderIssue(
                    slotId = slot.slotId,
                    message = missingAbilityMessage(slot),
                    active = true,
                    details = slot.instruction.ifBlank { null },
                    level = slot.level,
                )
            }
            if (savedSelection != null && savedSelection !in slot.choices) {
                issues += BuilderIssue(
                    slotId = slot.slotId,
                    message = "${savedSelection.label()} no longer belongs to ${slot.groupLabel}.",
                    active = active,
                    details = "The selected ${slot.sourceLabel()} changed. Choose one of the listed attributes.",
                    level = slot.level,
                )
            }
        }
        slots
            .groupBy { it.groupId }
            .forEach { (groupId, groupSlots) ->
                val selectionsInGroup = groupSlots.mapNotNull { slot ->
                    val ability = slot.selectedValidAbility(selectedAbilityBoosts) ?: return@mapNotNull null
                    slot.slotId to ability
                }
                val selectedInGroup = selectionsInGroup.map { it.second }
                selectedInGroup
                    .groupingBy { it }
                    .eachCount()
                    .filterValues { it > 1 }
                    .forEach { (duplicate, count) ->
                        val active = groupSlots.any { it.level <= activeLevel }
                        val relatedSlotIds = selectionsInGroup
                            .filter { (_, ability) -> ability == duplicate }
                            .map { (slotId, _) -> slotId }
                            .toSet()
                        val groupLabel = groupSlots.firstOrNull()?.groupLabel ?: groupId
                        val expectedCount = groupSlots.size
                        issues += BuilderIssue(
                            slotId = groupId,
                            message = "${duplicate.label()} is selected ${duplicateCountText(count)} in $groupLabel.",
                            active = active,
                            details = "Choose ${expectedCount.numberWord()} different attributes for this step.",
                            relatedSlotIds = relatedSlotIds,
                            level = groupSlots.minOfOrNull { it.level },
                        )
                    }
            }
        val activeModifiers = appliedAbilityModifiers(
            slots = slots,
            selectedAbilityBoosts = selectedAbilityBoosts,
            level = activeLevel,
        ).modifiers
        if (activeLevel < 5) {
            activeModifiers.forEach { (ability, modifier) ->
                when {
                    modifier < -1 -> issues += BuilderIssue(
                        slotId = "ability/modifier/${ability.name.lowercase()}",
                        message = "${ability.label()} is ${modifier.withSign()} at level 1.",
                        active = true,
                        details = "Level 1 attribute modifiers cannot be lower than -1.",
                        level = 1,
                    )
                    modifier > 4 -> issues += BuilderIssue(
                        slotId = "ability/modifier/${ability.name.lowercase()}",
                        message = "${ability.label()} is ${modifier.withSign()} at level 1.",
                        active = true,
                        details = "Level 1 attribute modifiers cannot be higher than +4.",
                        level = 1,
                    )
                }
            }
        }
        return issues
    }

    fun skillIssues(
        slots: List<BuilderSkillChoiceSlot>,
        selectedSkillChoices: Map<String, String>,
        activeLevel: Int,
        initialTrainedSkills: Set<String> = emptySet(),
    ): List<BuilderIssue> {
        val issues = mutableListOf<BuilderIssue>()
        slots.forEach { slot ->
            val active = slot.level <= activeLevel
            val selected = selectedSkillChoices[slot.slotId]?.takeIf { it.isNotBlank() }
            if (slot.required && slot.level <= activeLevel && selectedSkillChoices[slot.slotId].isNullOrBlank()) {
                issues += BuilderIssue(
                    slotId = slot.slotId,
                    message = "${slot.label} is required.",
                    active = true,
                    details = slot.instruction.ifBlank { null },
                    level = slot.level,
                )
            }
            if (selected != null && !slot.selectionIsAllowed(selected)) {
                issues += BuilderIssue(
                    slotId = slot.slotId,
                    message = "${displaySkillName(selected)} no longer belongs to ${slot.label}.",
                    active = active,
                    details = "The selected skill source changed. Choose one of the listed skills.",
                    level = slot.level,
                )
            }
        }
        slots
            .filter { slot -> slot.kind.trainsNewSkill() }
            .groupBy { slot -> slot.level }
            .forEach { (level, levelSlots) ->
                val selections = levelSlots.mapNotNull { slot ->
                    val selected = selectedSkillChoices[slot.slotId]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    if (selected.startsWith("lore:", ignoreCase = true)) return@mapNotNull null
                    slot to normalizeSkillId(selected)
                }
                selections
                    .filter { (_, skillId) -> skillId in initialTrainedSkills }
                    .forEach { (slot, skillId) ->
                        issues += BuilderIssue(
                            slotId = slot.slotId,
                            message = "${displaySkillName(skillId)} is already trained.",
                            active = slot.level <= activeLevel,
                            details = "Choose a different skill for ${slot.label}.",
                            level = slot.level,
                        )
                    }
                selections
                    .groupBy { (_, skillId) -> skillId }
                    .filterValues { matches -> matches.size > 1 }
                    .forEach { (skillId, matches) ->
                        val relatedSlotIds = matches.map { (slot, _) -> slot.slotId }.toSet()
                        issues += BuilderIssue(
                            slotId = "skill/duplicate/$level/$skillId",
                            message = "${displaySkillName(skillId)} is selected more than once at level $level.",
                            active = level <= activeLevel,
                            details = "Choose different skills for these training choices.",
                            relatedSlotIds = relatedSlotIds,
                            level = level,
                        )
                    }
            }
        return issues
    }

    fun promptIssues(
        slots: List<BuilderPromptSlot>,
        selectedPromptChoices: Map<String, String>,
        activeLevel: Int,
    ): List<BuilderIssue> {
        return slots.flatMap { slot ->
            val active = slot.level <= activeLevel
            val selected = selectedPromptChoices[slot.slotId]
            buildList {
                if (slot.required && active && slot.choices.isEmpty()) {
                    add(
                        BuilderIssue(
                            slotId = slot.slotId,
                            message = "${slot.label} is required but is not structured yet.",
                            active = true,
                            details = "The selected ${slot.sourceLabel} has a required prompt that cannot be rendered from the local data.",
                            level = slot.level,
                        ),
                    )
                } else if (slot.required && active && selected.isNullOrBlank()) {
                    add(
                        BuilderIssue(
                            slotId = slot.slotId,
                            message = "Choose ${slot.label.lowercase()}.",
                            active = true,
                            details = slot.instruction.ifBlank { "This required choice comes from ${slot.sourceLabel}." },
                            level = slot.level,
                        ),
                    )
                }
                if (!selected.isNullOrBlank() && slot.choices.isNotEmpty() && slot.choices.none { choice -> choice.value == selected }) {
                    add(
                        BuilderIssue(
                            slotId = slot.slotId,
                            message = "${selected.promptChoiceLabel()} no longer belongs to ${slot.label}.",
                            active = active,
                            details = "The selected source changed. Choose one of the listed options.",
                            level = slot.level,
                        ),
                    )
                }
            }
        }
    }

    fun buildFacts(
        catalog: CharacterBuilderCatalog?,
        classId: String,
        ancestryId: String?,
        backgroundId: String?,
        level: Int,
        abilitySlots: List<BuilderAbilityBoostSlot>,
        selectedAbilityBoosts: Map<String, AbilityScore>,
        skillSlots: List<BuilderSkillChoiceSlot>,
        selectedSkillChoices: Map<String, String>,
        selectedFeatSlotOptions: Map<String, String>,
    ): BuildFactSnapshot {
        val abilityFacts = appliedAbilityModifiers(
            slots = abilitySlots,
            selectedAbilityBoosts = selectedAbilityBoosts,
            level = level,
        )
        val abilityModifiers = abilityFacts.modifiers
        val skillRanks = mutableMapOf<String, BuilderSkillRank>()
        val classRecord = catalog?.classesById?.get(normalizeClassId(classId))
        val background = backgroundId?.let { catalog?.backgroundsById?.get(it) }
        classRecord?.trainedSkills?.value.orEmpty().forEach { skillRanks[normalizeSkillId(it)] = BuilderSkillRank.TRAINED }
        classRecord?.trainedSkills?.lore.orEmpty().forEach { skillRanks[loreKey(it)] = BuilderSkillRank.TRAINED }
        background?.trainedSkills?.value.orEmpty().forEach { skillRanks[normalizeSkillId(it)] = BuilderSkillRank.TRAINED }
        background?.trainedSkills?.lore.orEmpty().forEach { skillRanks[loreKey(it)] = BuilderSkillRank.TRAINED }
        skillSlots
            .filter { it.level <= level }
            .sortedBy { it.level }
            .forEach { slot ->
                val selected = selectedSkillChoices[slot.slotId]?.takeIf { it.isNotBlank() } ?: return@forEach
                val key = selected.normalizeSelectedSkill()
                val current = skillRanks[key] ?: BuilderSkillRank.UNTRAINED
                skillRanks[key] = when (slot.kind) {
                    BuilderSkillChoiceKind.TRAINED_SKILL,
                    BuilderSkillChoiceKind.PROMPT_SKILL -> if (current.value >= BuilderSkillRank.TRAINED.value) {
                        current
                    } else {
                        BuilderSkillRank.TRAINED
                    }
                    BuilderSkillChoiceKind.SKILL_INCREASE -> current.increase()
                }
            }
        val proficiencyRanks = buildProficiencyRanks(
            catalog = catalog,
            classRecord = classRecord,
            level = level,
        )
        val hp = classRecord?.hp?.let { classHp ->
            ancestryId?.let { catalog?.ancestriesById?.get(it)?.hp }?.let { ancestryHp ->
                ancestryHp + level * (classHp + (abilityModifiers[AbilityScore.CONSTITUTION] ?: 0))
            }
        }
        val perceptionTotal = proficiencyRanks["perception:perception"]?.let { rank ->
            proficiencyTotal(rank, level) + (abilityModifiers[AbilityScore.WISDOM] ?: 0)
        }
        val saveTotals = mapOf(
            "fortitude" to AbilityScore.CONSTITUTION,
            "reflex" to AbilityScore.DEXTERITY,
            "will" to AbilityScore.WISDOM,
        ).mapNotNull { (save, ability) ->
            proficiencyRanks["save:$save"]?.let { rank ->
                save to proficiencyTotal(rank, level) + (abilityModifiers[ability] ?: 0)
            }
        }.toMap()
        val skillTotals = skillRanks.mapValues { (skillId, rank) ->
            val ability = if (skillId.startsWith("lore:")) {
                AbilityScore.INTELLIGENCE
            } else {
                standardSkillsById[skillId]?.ability ?: AbilityScore.INTELLIGENCE
            }
            proficiencyTotal(rank.value, level) + (abilityModifiers[ability] ?: 0)
        }
        return BuildFactSnapshot(
            level = level,
            abilityModifiers = abilityModifiers,
            abilityAdjustments = abilityFacts.adjustments,
            skillRanks = skillRanks.toMap(),
            proficiencyRanks = proficiencyRanks,
            hp = hp,
            perceptionTotal = perceptionTotal,
            saveTotals = saveTotals,
            skillTotals = skillTotals,
            selectedFeatIds = selectedFeatSlotOptions
                .filterKeys { slotId ->
                    val slotLevel = slotId.split('/').lastOrNull()?.toIntOrNull()
                    slotLevel == null || slotLevel <= level
                }
                .values
                .toSet(),
        )
    }

    fun legalityFor(
        feat: BuilderFeatRecord,
        slot: BuilderFeatSlot,
        facts: BuildFactSnapshot,
        selectedClassId: String,
        selectedAncestryId: String?,
        selectedHeritageId: String?,
        catalog: CharacterBuilderCatalog?,
        prerequisiteLookup: BuilderPrerequisiteLookup = buildPrerequisiteLookup(catalog),
    ): BuilderFeatLegality {
        val reasons = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (feat.category != slot.kind) {
            reasons += "This is a ${feat.category} feat, not a ${slot.kind} feat."
        }
        if (feat.level > slot.level) {
            reasons += "Requires feat level ${feat.level}; this slot is level ${slot.level}."
        }
        if (feat.rarity != "common") {
            warnings += "${feat.rarity.replaceFirstChar { it.uppercase() }} options need table approval."
        }
        feat.prerequisites.forEach { prerequisite ->
            val result = checkPrerequisite(
                text = prerequisite,
                facts = facts,
                selectedClassId = selectedClassId,
                selectedAncestryId = selectedAncestryId,
                selectedHeritageId = selectedHeritageId,
                prerequisiteLookup = prerequisiteLookup,
            )
            when (result) {
                is PrerequisiteCheck.Met -> Unit
                is PrerequisiteCheck.NotMet -> reasons += result.message
                is PrerequisiteCheck.Unparsed -> warnings += "Prerequisite Not Found: ${result.text}"
            }
        }
        feat.choicePrompts.filter { it.required && it.choiceValues.isEmpty() }.forEach { prompt ->
            warnings += "Requires a choice that is not structured yet: ${prompt.label}"
        }
        feat.warnings.take(2).forEach { warning -> warnings += warning.message }
        val status = when {
            reasons.isNotEmpty() -> BuilderLegalityStatus.UNAVAILABLE
            warnings.isNotEmpty() -> BuilderLegalityStatus.NEEDS_REVIEW
            else -> BuilderLegalityStatus.ELIGIBLE
        }
        return BuilderFeatLegality(feat.id, status, reasons.distinct(), warnings.distinct())
    }

    fun buildPrerequisiteLookup(catalog: CharacterBuilderCatalog?): BuilderPrerequisiteLookup {
        if (catalog == null) return BuilderPrerequisiteLookup()
        return BuilderPrerequisiteLookup(
            classIdsByName = catalog.classes.associate { it.name.lowercase() to it.id },
            ancestryIdsByName = catalog.ancestries.associate { it.name.lowercase() to it.id },
            featIdsByName = catalog.feats.associate { it.name.lowercase() to it.id },
        )
    }

    fun displaySkillName(skillId: String): String {
        return when {
            skillId.startsWith("lore:") -> skillId.removePrefix("lore:").ifBlank { "Lore" }
            else -> standardSkillsById[normalizeSkillId(skillId)]?.label ?: skillId.replace('-', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    fun initialTrainedSkillIds(
        catalog: CharacterBuilderCatalog?,
        classId: String,
        backgroundId: String?,
    ): Set<String> {
        val classRecord = catalog?.classesById?.get(normalizeClassId(classId))
        val background = backgroundId?.let { catalog?.backgroundsById?.get(it) }
        return buildSet {
            classRecord?.trainedSkills?.value.orEmpty().mapTo(this, ::normalizeSkillId)
            background?.trainedSkills?.value.orEmpty().mapTo(this, ::normalizeSkillId)
        }
    }

    fun loreKey(loreName: String): String = "lore:${loreName.trim().ifBlank { "Lore" }}"

    private fun promptSkillChoiceSlots(
        ancestry: BuilderAncestryRecord?,
        heritage: BuilderHeritageRecord?,
        background: BuilderBackgroundRecord?,
        classRecord: BuilderClassRecord?,
    ): List<BuilderSkillChoiceSlot> {
        return buildList {
            ancestry?.choicePrompts.orEmpty()
                .mapSkillPromptSlots(
                    sourceId = ancestry?.id.orEmpty(),
                    sourceLabel = "${ancestry?.name.orEmpty()} ancestry",
                    into = this,
                )
            heritage?.choicePrompts.orEmpty()
                .mapSkillPromptSlots(
                    sourceId = heritage?.id.orEmpty(),
                    sourceLabel = "${heritage?.name.orEmpty()} heritage",
                    into = this,
                )
            background?.choicePrompts.orEmpty()
                .mapSkillPromptSlots(
                    sourceId = background?.id.orEmpty(),
                    sourceLabel = "${background?.name.orEmpty()} background",
                    into = this,
                )
            classRecord?.choicePrompts.orEmpty()
                .mapSkillPromptSlots(
                    sourceId = classRecord?.id.orEmpty(),
                    sourceLabel = "${classRecord?.name.orEmpty()} class",
                    into = this,
                )
        }
    }

    private fun List<BuilderChoicePromptRecord>.mapSkillPromptSlots(
        sourceId: String,
        sourceLabel: String,
        into: MutableList<BuilderSkillChoiceSlot>,
    ) {
        forEach { prompt ->
            val choices = prompt.skillChoiceIds() ?: return@forEach
            into += BuilderSkillChoiceSlot(
                slotId = "skill/prompt/${sourceLabel.slotSourceKey()}/$sourceId/${prompt.promptId}",
                label = prompt.promptLabel(sourceLabel),
                level = 1,
                kind = BuilderSkillChoiceKind.PROMPT_SKILL,
                choices = choices,
                allowLore = false,
                required = prompt.required,
                sourceLabel = sourceLabel,
                instruction = "Choose the skill granted by $sourceLabel.",
            )
        }
    }

    private fun List<BuilderChoicePromptRecord>.mapPromptSlots(
        source: BuilderPromptSource,
        sourceId: String,
        sourceLabel: String,
        into: MutableList<BuilderPromptSlot>,
    ) {
        forEach { prompt ->
            into += BuilderPromptSlot(
                slotId = "prompt/${source.name.lowercase()}/$sourceId/${prompt.promptId}",
                label = prompt.promptLabel(sourceLabel),
                level = 1,
                source = source,
                sourceLabel = sourceLabel,
                choices = prompt.choiceValues.map { choice ->
                    choice.copy(label = choice.label.cleanPromptLabel(choice.value))
                },
                required = prompt.required,
                instruction = "Resolve this required choice from $sourceLabel.",
            )
        }
    }

    private fun BuilderChoicePromptRecord.skillChoiceIds(): List<String>? {
        if (choiceDomain.equals("skill", ignoreCase = true) || choiceDomain.equals("skills", ignoreCase = true)) {
            return standardSkills.map { it.id }
        }
        if (choiceValues.isEmpty()) return null
        val normalized = choiceValues.map { choice -> normalizeSkillId(choice.value) }
        return normalized.takeIf { values -> values.all { it in standardSkillsById } }
    }

    private fun BuilderChoicePromptRecord.promptLabel(sourceLabel: String): String {
        val sourcePrefix = sourceLabel.replaceFirstChar { it.uppercase() }
        return when {
            label.contains("Skill", ignoreCase = true) || promptId.contains("skill", ignoreCase = true) -> {
                "$sourcePrefix skill"
            }
            label.contains("CreatureSize", ignoreCase = true) || promptId.contains("size", ignoreCase = true) -> {
                "$sourcePrefix size"
            }
            else -> {
                val cleaned = label.cleanPromptLabel(promptId)
                if (cleaned.equals("Prompt", ignoreCase = true) || cleaned.equals("Choice", ignoreCase = true)) {
                    "$sourcePrefix ${promptId.promptChoiceLabel()}"
                } else {
                    cleaned
                }
            }
        }
    }

    private fun String.slotSourceKey(): String {
        return substringAfterLast(' ')
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "source" }
    }

    private fun String.cleanPromptLabel(fallback: String): String {
        val cleaned = substringAfterLast('.')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
        return cleaned.ifBlank { fallback.promptChoiceLabel() }
    }

    private fun String.promptChoiceLabel(): String {
        return replace(Regex("[^A-Za-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.lowercase().replaceFirstChar { it.uppercase() } }
            .ifBlank { "Choice" }
    }

    private fun BuilderAbilityBoostRecord.toBoostSlot(
        slotId: String,
        groupId: String,
        label: String,
        level: Int,
        isFlaw: Boolean,
        kind: BuilderAbilityBoostKind,
        groupLabel: String,
        instruction: String,
    ): BuilderAbilityBoostSlot {
        val resolvedChoices = when {
            abilities.isNotEmpty() -> abilities
            selected != null -> listOf(selected)
            else -> AbilityScore.entries.toList()
        }
        return BuilderAbilityBoostSlot(
            slotId = slotId,
            groupId = groupId,
            label = label,
            level = level,
            choices = resolvedChoices,
            isFlaw = isFlaw,
            kind = kind,
            groupLabel = groupLabel,
            instruction = instruction,
        )
    }

    private fun buildProficiencyRanks(
        catalog: CharacterBuilderCatalog?,
        classRecord: BuilderClassRecord?,
        level: Int,
    ): Map<String, Int> {
        val ranks = mutableMapOf<String, Int>()
        classRecord?.baseProficiencies.orEmpty().forEach { grant ->
            ranks.upgrade(grant)
        }
        val activeFeatureRefs = classRecord?.featureRefs.orEmpty().toSet()
        catalog?.classFeatures.orEmpty()
            .filter { feature ->
                feature.level <= level &&
                    (feature.uuid in activeFeatureRefs || normalizeClassId(classRecord?.id.orEmpty()) in feature.traits.values)
            }
            .forEach { feature ->
                feature.proficiencyGrants.forEach { ranks.upgrade(it) }
            }
        return ranks
    }

    private fun MutableMap<String, Int>.upgrade(grant: BuilderProficiencyGrant) {
        val key = "${grant.category}:${grant.target}"
        this[key] = maxOf(this[key] ?: 0, grant.rank)
    }

    private fun checkPrerequisite(
        text: String,
        facts: BuildFactSnapshot,
        selectedClassId: String,
        selectedAncestryId: String?,
        selectedHeritageId: String?,
        prerequisiteLookup: BuilderPrerequisiteLookup,
    ): PrerequisiteCheck {
        val normalized = text.trim()
        if (normalized.isBlank()) return PrerequisiteCheck.Met
        abilityPattern.find(normalized)?.let { match ->
            val ability = parseAbilityName(match.groupValues[1]) ?: return PrerequisiteCheck.Unparsed(normalized)
            val required = parseRequiredModifier(match.groupValues[2]) ?: return PrerequisiteCheck.Unparsed(normalized)
            val actual = facts.abilityModifiers[ability] ?: 0
            return if (actual >= required) {
                PrerequisiteCheck.Met
            } else {
                PrerequisiteCheck.NotMet("${ability.label()} ${required.withSign()} required; current modifier is ${actual.withSign()}.")
            }
        }
        skillRankPattern.find(normalized)?.let { match ->
            val requiredRank = parseRank(match.groupValues[1]) ?: return PrerequisiteCheck.Unparsed(normalized)
            val skill = normalizeSkillId(match.groupValues[2])
            val actual = facts.skillRanks[skill] ?: BuilderSkillRank.UNTRAINED
            return if (actual.value >= requiredRank.value) {
                PrerequisiteCheck.Met
            } else {
                PrerequisiteCheck.NotMet("${requiredRank.label} in ${displaySkillName(skill)} required.")
            }
        }
        levelPattern.find(normalized)?.let { match ->
            val requiredLevel = match.groupValues[1].toIntOrNull() ?: return PrerequisiteCheck.Unparsed(normalized)
            return if (facts.level >= requiredLevel) {
                PrerequisiteCheck.Met
            } else {
                PrerequisiteCheck.NotMet("Level $requiredLevel required.")
            }
        }
        if (normalized.equals("trained in at least one skill", ignoreCase = true)) {
            return if (facts.skillRanks.values.any { it.value >= BuilderSkillRank.TRAINED.value }) {
                PrerequisiteCheck.Met
            } else {
                PrerequisiteCheck.NotMet("Trained in at least one skill required.")
            }
        }
        prerequisiteLookup.classIdsByName[normalized.lowercase()]?.let { requiredClassId ->
            return if (normalizeClassId(selectedClassId) == normalizeClassId(requiredClassId)) {
                PrerequisiteCheck.Met
            } else {
                PrerequisiteCheck.NotMet("${normalized} class required.")
            }
        }
        prerequisiteLookup.ancestryIdsByName[normalized.lowercase()]?.let { ancestryId ->
            return if (selectedAncestryId == ancestryId || selectedHeritageId == ancestryId) {
                PrerequisiteCheck.Met
            } else {
                PrerequisiteCheck.NotMet("${normalized} ancestry required.")
            }
        }
        prerequisiteLookup.featIdsByName[normalized.lowercase()]?.let { requiredFeatId ->
            return if (requiredFeatId in facts.selectedFeatIds) {
                PrerequisiteCheck.Met
            } else {
                PrerequisiteCheck.NotMet("${normalized} feat required.")
            }
        }
        return PrerequisiteCheck.Unparsed(normalized)
    }

    private fun String.normalizeSelectedSkill(): String {
        return if (startsWith("lore:", ignoreCase = true)) {
            loreKey(removePrefix("lore:"))
        } else {
            normalizeSkillId(this)
        }
    }

    private fun BuilderSkillChoiceSlot.selectionIsAllowed(selected: String): Boolean {
        if (allowLore && selected.startsWith("lore:", ignoreCase = true)) return true
        return normalizeSkillId(selected) in choices.map(::normalizeSkillId).toSet()
    }

    private fun BuilderSkillChoiceKind.trainsNewSkill(): Boolean {
        return this == BuilderSkillChoiceKind.TRAINED_SKILL || this == BuilderSkillChoiceKind.PROMPT_SKILL
    }

    private fun proficiencyTotal(rank: Int, level: Int): Int = if (rank <= 0) 0 else level + rank * 2

    private fun appliedAbilityModifiers(
        slots: List<BuilderAbilityBoostSlot>,
        selectedAbilityBoosts: Map<String, AbilityScore>,
        level: Int,
    ): AppliedAbilityFacts {
        val modifiers = AbilityScore.entries.associateWith { 0 }.toMutableMap()
        val adjustments = mutableListOf<BuilderAbilityAdjustment>()
        slots
            .filter { slot -> slot.level <= level }
            .forEach { slot ->
                val ability = slot.selectedValidAbility(selectedAbilityBoosts) ?: return@forEach
                val before = modifiers.getValue(ability)
                val delta = if (slot.isFlaw) -1 else 1
                val after = before + delta
                modifiers[ability] = after
                adjustments += BuilderAbilityAdjustment(
                    slotId = slot.slotId,
                    groupId = slot.groupId,
                    groupLabel = slot.groupLabel,
                    slotLabel = slot.label,
                    level = slot.level,
                    ability = ability,
                    beforeModifier = before,
                    afterModifier = after,
                    delta = delta,
                    active = true,
                    isFlaw = slot.isFlaw,
                )
            }
        return AppliedAbilityFacts(
            modifiers = modifiers.toMap(),
            adjustments = adjustments,
        )
    }

    private fun BuilderAbilityBoostSlot.selectedValidAbility(
        selectedAbilityBoosts: Map<String, AbilityScore>,
    ): AbilityScore? {
        val ability = fixedChoice ?: selectedAbilityBoosts[slotId] ?: return null
        return ability.takeIf { it in choices }
    }

    private fun missingAbilityMessage(slot: BuilderAbilityBoostSlot): String {
        return when (slot.kind) {
            BuilderAbilityBoostKind.FREE_BOOST -> "Choose one free Level ${slot.level} boost."
            BuilderAbilityBoostKind.ANCESTRY_FLAW -> "Choose one ancestry flaw."
            BuilderAbilityBoostKind.ANCESTRY_BOOST -> "Choose one ancestry boost."
            BuilderAbilityBoostKind.BACKGROUND_BOOST -> "Choose one background boost."
            BuilderAbilityBoostKind.CLASS_KEY -> "Choose a class key attribute."
        }
    }

    private fun BuilderAbilityBoostSlot.sourceLabel(): String {
        return when (kind) {
            BuilderAbilityBoostKind.ANCESTRY_FLAW,
            BuilderAbilityBoostKind.ANCESTRY_BOOST -> "ancestry"
            BuilderAbilityBoostKind.BACKGROUND_BOOST -> "background"
            BuilderAbilityBoostKind.CLASS_KEY -> "class"
            BuilderAbilityBoostKind.FREE_BOOST -> "boost step"
        }
    }

    private fun duplicateCountText(count: Int): String {
        return when (count) {
            2 -> "twice"
            else -> "$count times"
        }
    }

    private fun Int.numberWord(): String {
        return when (this) {
            1 -> "one"
            2 -> "two"
            3 -> "three"
            4 -> "four"
            5 -> "five"
            6 -> "six"
            else -> toString()
        }
    }

    private fun parseRequiredModifier(raw: String): Int? {
        val value = raw.toIntOrNull() ?: return null
        val explicitModifier = raw.startsWith("+") || raw.startsWith("-") || value in -5..5
        return if (explicitModifier) value else Math.floorDiv(value - 10, 2)
    }

    private fun parseRank(raw: String): BuilderSkillRank? {
        return when (raw.trim().lowercase()) {
            "trained" -> BuilderSkillRank.TRAINED
            "expert" -> BuilderSkillRank.EXPERT
            "master" -> BuilderSkillRank.MASTER
            "legendary" -> BuilderSkillRank.LEGENDARY
            else -> null
        }
    }

    private fun parseAbilityName(raw: String): AbilityScore? {
        return when (raw.trim().lowercase()) {
            "str", "strength" -> AbilityScore.STRENGTH
            "dex", "dexterity" -> AbilityScore.DEXTERITY
            "con", "constitution" -> AbilityScore.CONSTITUTION
            "int", "intelligence" -> AbilityScore.INTELLIGENCE
            "wis", "wisdom" -> AbilityScore.WISDOM
            "cha", "charisma" -> AbilityScore.CHARISMA
            else -> null
        }
    }

    private val abilityPattern = Regex(
        pattern = "\\b(str(?:ength)?|dex(?:terity)?|con(?:stitution)?|int(?:elligence)?|wis(?:dom)?|cha(?:risma)?)\\s+([+-]?\\d{1,2})\\b",
        option = RegexOption.IGNORE_CASE,
    )
    private val skillRankPattern = Regex(
        pattern = "\\b(trained|expert|master|legendary)\\s+in\\s+([A-Za-z][A-Za-z ]+?)(?:\\s+skill)?$",
        option = RegexOption.IGNORE_CASE,
    )
    private val levelPattern = Regex(
        pattern = "\\blevel\\s+(\\d{1,2})\\b",
        option = RegexOption.IGNORE_CASE,
    )
}

private data class AppliedAbilityFacts(
    val modifiers: Map<AbilityScore, Int>,
    val adjustments: List<BuilderAbilityAdjustment>,
)

private sealed interface PrerequisiteCheck {
    data object Met : PrerequisiteCheck
    data class NotMet(val message: String) : PrerequisiteCheck
    data class Unparsed(val text: String) : PrerequisiteCheck
}
