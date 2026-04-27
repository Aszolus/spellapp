package com.spellapp.core.model

data class ClassSpellcastingDefinition(
    val characterClass: CharacterClass,
    val classId: String,
    val label: String,
    val defaultKeyAbility: AbilityScore,
    val keyAbilityOptions: List<AbilityScore>,
    val baseTradition: SpellcastingTradition?,
    val primaryTracks: List<PrimaryTrackDefinition>,
    val choiceGroups: List<ClassChoiceGroup> = emptyList(),
)

data class PrimaryTrackDefinition(
    val trackKey: String,
    val displayName: String,
    val progressionType: CastingProgressionType,
    val castingStyle: CastingStyle,
    val tradition: SpellcastingTradition?,
    val slotProgressionKey: String,
    val slotsByLevel: Map<Int, Map<Int, Int>> = emptyMap(),
    val allowanceRules: List<SpellAllowanceRule> = emptyList(),
)

data class SpellAllowanceRule(
    val trackKey: String,
    val kind: SpellAllowanceKind,
    val label: String,
    val policy: SpellAllowancePolicy,
    val countsByLevel: Map<Int, Map<Int, Int>> = emptyMap(),
    val totalsByLevel: Map<Int, Int> = emptyMap(),
    val source: String? = null,
    val note: String? = null,
)

enum class SpellAllowanceKind {
    PREPARED_SLOTS,
    REPERTOIRE,
    SIGNATURE_SPELLS,
    SPELLBOOK_MINIMUM,
    FAMILIAR_MINIMUM,
}

enum class SpellAllowancePolicy {
    CAP,
    MINIMUM,
    WARNING_ONLY,
    ALL_KNOWN,
}

data class ClassChoiceGroup(
    val id: String,
    val label: String,
    val optionType: CharacterBuildOptionType,
    val required: Boolean,
    val choices: List<ClassChoice>,
)

data class ClassChoice(
    val optionId: String,
    val label: String,
    val tradition: SpellcastingTradition? = null,
    val keyAbility: AbilityScore? = null,
    val grantedSpellNames: List<String> = emptyList(),
    val focusSpellNames: List<String> = emptyList(),
)

interface ClassSpellcastingCatalogSource {
    fun allDefinitions(): List<ClassSpellcastingDefinition>
    fun definitionFor(characterClass: CharacterClass): ClassSpellcastingDefinition?
}

class InMemoryClassSpellcastingCatalogSource(
    definitions: List<ClassSpellcastingDefinition>,
) : ClassSpellcastingCatalogSource {
    private val orderedDefinitions = definitions
    private val definitionsByClass = definitions.associateBy { it.characterClass }

    override fun allDefinitions(): List<ClassSpellcastingDefinition> = orderedDefinitions

    override fun definitionFor(characterClass: CharacterClass): ClassSpellcastingDefinition? =
        definitionsByClass[characterClass]
}

object EmptyClassSpellcastingCatalogSource : ClassSpellcastingCatalogSource {
    override fun allDefinitions(): List<ClassSpellcastingDefinition> = emptyList()

    override fun definitionFor(characterClass: CharacterClass): ClassSpellcastingDefinition? = null
}

object ClassSpellcastingCatalog : ClassSpellcastingCatalogSource {
    @Volatile
    private var source: ClassSpellcastingCatalogSource = EmptyClassSpellcastingCatalogSource

    val supportedSpellcasterClasses: List<CharacterClass>
        get() = allDefinitions().map { it.characterClass }

    fun install(catalogSource: ClassSpellcastingCatalogSource) {
        source = catalogSource
    }

    override fun allDefinitions(): List<ClassSpellcastingDefinition> =
        source.allDefinitions()

    override fun definitionFor(characterClass: CharacterClass): ClassSpellcastingDefinition? =
        source.definitionFor(characterClass)

    fun classFromId(id: String): CharacterClass? {
        val normalized = id.trim().replace('-', '_')
        return CharacterClass.entries.firstOrNull { characterClass ->
            characterClass != CharacterClass.OTHER &&
                characterClass.name.equals(normalized, ignoreCase = true)
        }
    }

    fun selectedChoices(
        characterClass: CharacterClass,
        selectedOptionIds: Set<String>,
    ): List<ClassChoice> {
        return definitionFor(characterClass)
            ?.choiceGroups
            .orEmpty()
            .flatMap { group -> group.choices }
            .filter { choice -> choice.optionId in selectedOptionIds }
    }

    fun traditionFor(
        characterClass: CharacterClass,
        selectedOptionIds: Set<String>,
    ): SpellcastingTradition? {
        return selectedChoices(characterClass, selectedOptionIds)
            .firstNotNullOfOrNull { it.tradition }
            ?: definitionFor(characterClass)?.baseTradition
    }

    fun defaultKeyAbilityFor(
        characterClass: CharacterClass,
        selectedOptionIds: Set<String> = emptySet(),
    ): AbilityScore {
        return selectedChoices(characterClass, selectedOptionIds)
            .firstNotNullOfOrNull { it.keyAbility }
            ?: definitionFor(characterClass)?.defaultKeyAbility
            ?: AbilityScore.INTELLIGENCE
    }

    fun managedOptionIds(): Set<String> {
        return allDefinitions()
            .flatMap { definition -> definition.choiceGroups }
            .flatMap { group -> group.choices }
            .map { choice -> choice.optionId }
            .toSet()
    }

    fun optionTypeForOptionId(optionId: String): CharacterBuildOptionType? {
        return allDefinitions()
            .flatMap { definition -> definition.choiceGroups }
            .firstOrNull { group -> group.choices.any { choice -> choice.optionId == optionId } }
            ?.optionType
    }
}

fun ClassSpellcastingCatalogSource.selectedChoices(
    characterClass: CharacterClass,
    selectedOptionIds: Set<String>,
): List<ClassChoice> {
    return definitionFor(characterClass)
        ?.choiceGroups
        .orEmpty()
        .flatMap { group -> group.choices }
        .filter { choice -> choice.optionId in selectedOptionIds }
}

fun ClassSpellcastingCatalogSource.traditionFor(
    characterClass: CharacterClass,
    selectedOptionIds: Set<String>,
): SpellcastingTradition? {
    return selectedChoices(
        characterClass = characterClass,
        selectedOptionIds = selectedOptionIds,
    ).firstNotNullOfOrNull { it.tradition }
        ?: definitionFor(characterClass)?.baseTradition
}

fun ClassSpellcastingCatalogSource.defaultKeyAbilityFor(
    characterClass: CharacterClass,
    selectedOptionIds: Set<String> = emptySet(),
): AbilityScore {
    return selectedChoices(
        characterClass = characterClass,
        selectedOptionIds = selectedOptionIds,
    ).firstNotNullOfOrNull { it.keyAbility }
        ?: definitionFor(characterClass)?.defaultKeyAbility
        ?: AbilityScore.INTELLIGENCE
}

fun ClassSpellcastingCatalogSource.managedOptionIds(): Set<String> {
    return allDefinitions()
        .flatMap { definition -> definition.choiceGroups }
        .flatMap { group -> group.choices }
        .map { choice -> choice.optionId }
        .toSet()
}

fun ClassSpellcastingCatalogSource.optionTypeForOptionId(optionId: String): CharacterBuildOptionType? {
    return allDefinitions()
        .flatMap { definition -> definition.choiceGroups }
        .firstOrNull { group -> group.choices.any { choice -> choice.optionId == optionId } }
        ?.optionType
}

fun ClassSpellcastingCatalogSource.slotCountsByProgressionKey(
    progressionKey: String,
    level: Int,
): Map<Int, Int>? {
    val normalizedKey = progressionKey.trim()
    return allDefinitions()
        .asSequence()
        .flatMap { definition -> definition.primaryTracks.asSequence() }
        .firstOrNull { track -> track.slotProgressionKey == normalizedKey }
        ?.slotsByLevel
        ?.get(level)
}

fun ClassSpellcastingCatalogSource.slotCountsForTrack(
    trackKey: String,
    sourceId: String,
    level: Int,
): Map<Int, Int>? {
    val definition = ClassSpellcastingCatalog.classFromId(sourceId)
        ?.let(::definitionFor)
        ?: return null
    return definition.primaryTracks
        .firstOrNull { track -> track.trackKey == trackKey }
        ?.slotsByLevel
        ?.get(level)
}

fun ClassSpellcastingCatalogSource.allowanceRulesForTrack(
    trackKey: String,
    sourceId: String,
): List<SpellAllowanceRule> {
    val definition = ClassSpellcastingCatalog.classFromId(sourceId)
        ?.let(::definitionFor)
        ?: return emptyList()
    return definition.primaryTracks
        .firstOrNull { track -> track.trackKey == trackKey }
        ?.allowanceRules
        .orEmpty()
}

fun SpellAllowanceRule.countsAtLevel(level: Int): Map<Int, Int> {
    countsByLevel[level]?.let { return it }
    val fallbackLevel = countsByLevel.keys
        .filter { candidate -> candidate <= level }
        .maxOrNull()
    return fallbackLevel?.let { countsByLevel[it] }.orEmpty()
}

fun SpellAllowanceRule.totalAtLevel(level: Int): Int? {
    totalsByLevel[level]?.let { return it }
    val fallbackLevel = totalsByLevel.keys
        .filter { candidate -> candidate <= level }
        .maxOrNull()
    return fallbackLevel?.let { totalsByLevel[it] }
}
