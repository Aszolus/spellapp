package com.spellapp.feature.character

import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.ClassSpellcastingCatalog

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

object StaticCharacterClassDefinitionSource : CharacterClassDefinitionSource {
    private val byClass: Map<CharacterClass, CharacterClassDefinition> =
        ClassSpellcastingCatalog.allDefinitions()
            .map { definition ->
                CharacterClassDefinition(
                    characterClass = definition.characterClass,
                    label = definition.label,
                    defaultKeyAbility = definition.defaultKeyAbility,
                    keyAbilityOptions = definition.keyAbilityOptions,
                )
            }
            .plus(
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
            )
            .associateBy { it.characterClass }

    override fun allDefinitions(): List<CharacterClassDefinition> {
        return byClass.values.toList()
    }

    override fun phaseOneDefinitions(): List<CharacterClassDefinition> =
        ClassSpellcastingCatalog.allDefinitions()
            .map { definition -> definition.characterClass }
            .map(::definitionFor)

    override fun definitionFor(characterClass: CharacterClass): CharacterClassDefinition {
        return byClass[characterClass] ?: byClass.getValue(CharacterClass.OTHER)
    }
}
