package com.spellapp.feature.character

import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CharacterClass

data class CharacterClassDefinition(
    val characterClass: CharacterClass,
    val label: String,
    val defaultKeyAbility: AbilityScore,
    val keyAbilityOptions: List<AbilityScore>,
)

interface CharacterClassDefinitionSource {
    fun allDefinitions(): List<CharacterClassDefinition>
    fun phaseOneDefinitions(): List<CharacterClassDefinition>
    fun definitionFor(characterClass: CharacterClass): CharacterClassDefinition
}

private val phaseOnePreparedClasses = listOf(
    CharacterClass.WIZARD,
    CharacterClass.CLERIC,
    CharacterClass.DRUID,
)

object StaticCharacterClassDefinitionSource : CharacterClassDefinitionSource {
    private val byClass: Map<CharacterClass, CharacterClassDefinition> = listOf(
        CharacterClassDefinition(
            characterClass = CharacterClass.WIZARD,
            label = "Wizard",
            defaultKeyAbility = AbilityScore.INTELLIGENCE,
            keyAbilityOptions = listOf(AbilityScore.INTELLIGENCE),
        ),
        CharacterClassDefinition(
            characterClass = CharacterClass.CLERIC,
            label = "Cleric",
            defaultKeyAbility = AbilityScore.WISDOM,
            keyAbilityOptions = listOf(AbilityScore.WISDOM, AbilityScore.CHARISMA),
        ),
        CharacterClassDefinition(
            characterClass = CharacterClass.DRUID,
            label = "Druid",
            defaultKeyAbility = AbilityScore.WISDOM,
            keyAbilityOptions = listOf(AbilityScore.WISDOM),
        ),
        CharacterClassDefinition(
            characterClass = CharacterClass.OTHER,
            label = "Other",
            defaultKeyAbility = AbilityScore.INTELLIGENCE,
            keyAbilityOptions = listOf(
                AbilityScore.INTELLIGENCE,
                AbilityScore.WISDOM,
                AbilityScore.CHARISMA,
            ),
        ),
    ).associateBy { it.characterClass }

    override fun allDefinitions(): List<CharacterClassDefinition> {
        return byClass.values.toList()
    }

    override fun phaseOneDefinitions(): List<CharacterClassDefinition> =
        phaseOnePreparedClasses.map(::definitionFor)

    override fun definitionFor(characterClass: CharacterClass): CharacterClassDefinition {
        return byClass[characterClass] ?: byClass.getValue(CharacterClass.OTHER)
    }
}
