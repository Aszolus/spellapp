package com.spellapp.feature.character

import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.normalizeClassId
import kotlin.math.floor

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

data class BuilderAbilityBoostSlot(
    val slotId: String,
    val groupId: String,
    val label: String,
    val level: Int,
    val choices: List<AbilityScore>,
    val isFlaw: Boolean,
    val required: Boolean = true,
) {
    val fixedChoice: AbilityScore? get() = choices.singleOrNull()
}

data class BuilderSkillChoiceSlot(
    val slotId: String,
    val label: String,
    val level: Int,
    val kind: BuilderSkillChoiceKind,
    val choices: List<String>,
    val allowLore: Boolean,
    val required: Boolean = true,
)

enum class BuilderSkillChoiceKind {
    TRAINED_SKILL,
    SKILL_INCREASE,
}

data class BuilderIssue(
    val slotId: String,
    val message: String,
    val active: Boolean,
)

data class BuildFactSnapshot(
    val level: Int,
    val abilityScores: Map<AbilityScore, Int>,
    val abilityModifiers: Map<AbilityScore, Int>,
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
                label = "${ancestry?.name} flaw",
                level = 1,
                isFlaw = true,
            )
        }
        ancestry?.boosts.orEmpty().forEach { boost ->
            slots += boost.toBoostSlot(
                slotId = "ability/ancestry/${ancestry?.id}/boost/${boost.id}",
                groupId = "ancestry-${ancestry?.id}-boosts",
                label = "${ancestry?.name} boost",
                level = 1,
                isFlaw = false,
            )
        }
        if (voluntaryFlawEnabled) {
            repeat(2) { index ->
                slots += BuilderAbilityBoostSlot(
                    slotId = "ability/voluntary-flaw/flaw/${index + 1}",
                    groupId = "voluntary-flaw-flaws",
                    label = "Voluntary flaw ${index + 1}",
                    level = 1,
                    choices = AbilityScore.entries.toList(),
                    isFlaw = true,
                )
            }
            slots += BuilderAbilityBoostSlot(
                slotId = "ability/voluntary-flaw/boost/1",
                groupId = "voluntary-flaw-boosts",
                label = "Voluntary flaw boost",
                level = 1,
                choices = AbilityScore.entries.toList(),
                isFlaw = false,
            )
        }
        background?.boosts.orEmpty().forEach { boost ->
            slots += boost.toBoostSlot(
                slotId = "ability/background/${background?.id}/boost/${boost.id}",
                groupId = "background-${background?.id}-boosts",
                label = "${background?.name} boost",
                level = 1,
                isFlaw = false,
            )
        }
        slots += BuilderAbilityBoostSlot(
            slotId = "ability/class/${normalizeClassId(classId)}/key",
            groupId = "class-${normalizeClassId(classId)}-key",
            label = "Class key ability",
            level = 1,
            choices = listOf(keyAbility),
            isFlaw = false,
        )
        listOf(1, 5, 10, 15, 20).forEach { level ->
            repeat(4) { index ->
                slots += BuilderAbilityBoostSlot(
                    slotId = "ability/free/$level/${index + 1}",
                    groupId = "free-boosts-$level",
                    label = "Level $level free boost ${index + 1}",
                    level = level,
                    choices = AbilityScore.entries.toList(),
                    isFlaw = false,
                )
            }
        }
        return slots
    }

    fun skillChoiceSlots(
        catalog: CharacterBuilderCatalog?,
        classId: String,
    ): List<BuilderSkillChoiceSlot> {
        val classRecord = catalog?.classesById?.get(normalizeClassId(classId)) ?: return emptyList()
        val slots = mutableListOf<BuilderSkillChoiceSlot>()
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
            )
        }
        return slots
    }

    fun abilityIssues(
        slots: List<BuilderAbilityBoostSlot>,
        selectedAbilityBoosts: Map<String, AbilityScore>,
        activeLevel: Int,
    ): List<BuilderIssue> {
        val issues = mutableListOf<BuilderIssue>()
        slots.forEach { slot ->
            val active = slot.level <= activeLevel
            val selected = slot.fixedChoice ?: selectedAbilityBoosts[slot.slotId]
            if (slot.required && active && selected == null) {
                issues += BuilderIssue(slot.slotId, "${slot.label} is required.", active = true)
            }
            if (selected != null && selected !in slot.choices) {
                issues += BuilderIssue(slot.slotId, "${slot.label} has an invalid ability.", active = active)
            }
        }
        slots
            .groupBy { it.groupId }
            .forEach { (_, groupSlots) ->
                val selectedInGroup = groupSlots.mapNotNull { slot ->
                    slot.fixedChoice ?: selectedAbilityBoosts[slot.slotId]
                }
                selectedInGroup
                    .groupingBy { it }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
                    .forEach { duplicate ->
                        val active = groupSlots.any { it.level <= activeLevel }
                        issues += BuilderIssue(
                            slotId = groupSlots.first().groupId,
                            message = "${duplicate.label()} is selected more than once in the same boost group.",
                            active = active,
                        )
                    }
            }
        return issues
    }

    fun skillIssues(
        slots: List<BuilderSkillChoiceSlot>,
        selectedSkillChoices: Map<String, String>,
        activeLevel: Int,
    ): List<BuilderIssue> {
        return slots.mapNotNull { slot ->
            if (slot.required && slot.level <= activeLevel && selectedSkillChoices[slot.slotId].isNullOrBlank()) {
                BuilderIssue(slot.slotId, "${slot.label} is required.", active = true)
            } else {
                null
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
        val activeAbilitySlots = abilitySlots.filter { it.level <= level }
        val abilityScores = AbilityScore.entries.associateWith { 10 }.toMutableMap()
        activeAbilitySlots.forEach { slot ->
            val ability = slot.fixedChoice ?: selectedAbilityBoosts[slot.slotId] ?: return@forEach
            val current = abilityScores.getValue(ability)
            abilityScores[ability] = if (slot.isFlaw) current - 2 else boostScore(current)
        }
        val abilityModifiers = abilityScores.mapValues { (_, score) -> abilityModifier(score) }
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
                    BuilderSkillChoiceKind.TRAINED_SKILL -> if (current.value >= BuilderSkillRank.TRAINED.value) {
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
            abilityScores = abilityScores.toMap(),
            abilityModifiers = abilityModifiers,
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
                is PrerequisiteCheck.Unparsed -> warnings += "Unparsed prerequisite: ${result.text}"
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

    fun loreKey(loreName: String): String = "lore:${loreName.trim().ifBlank { "Lore" }}"

    private fun BuilderAbilityBoostRecord.toBoostSlot(
        slotId: String,
        groupId: String,
        label: String,
        level: Int,
        isFlaw: Boolean,
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
            val required = match.groupValues[2].toIntOrNull() ?: return PrerequisiteCheck.Unparsed(normalized)
            val actual = facts.abilityScores[ability] ?: 10
            return if (actual >= required) {
                PrerequisiteCheck.Met
            } else {
                PrerequisiteCheck.NotMet("${ability.label()} $required required; current score is $actual.")
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

    private fun boostScore(score: Int): Int = score + if (score >= 18) 1 else 2

    private fun abilityModifier(score: Int): Int = floor((score - 10) / 2.0).toInt()

    private fun proficiencyTotal(rank: Int, level: Int): Int = if (rank <= 0) 0 else level + rank * 2

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
        pattern = "\\b(str(?:ength)?|dex(?:terity)?|con(?:stitution)?|int(?:elligence)?|wis(?:dom)?|cha(?:risma)?)\\s+(\\d{1,2})\\b",
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

private sealed interface PrerequisiteCheck {
    data object Met : PrerequisiteCheck
    data class NotMet(val message: String) : PrerequisiteCheck
    data class Unparsed(val text: String) : PrerequisiteCheck
}
