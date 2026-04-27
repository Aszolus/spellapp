package com.spellapp.feature.character

import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.ClassSpellcastingCatalog
import com.spellapp.core.model.normalizeClassId

data class CharacterClassDefinition(
    val classId: String,
    val label: String,
    val defaultKeyAbility: AbilityScore,
    val keyAbilityOptions: List<AbilityScore>,
)

interface CharacterClassDefinitionSource {
    fun allDefinitions(): List<CharacterClassDefinition>
    fun phaseOneDefinitions(): List<CharacterClassDefinition>
    fun definitionFor(classId: String): CharacterClassDefinition
}

object StaticCharacterClassDefinitionSource : CharacterClassDefinitionSource {
    private val byClassId: Map<String, CharacterClassDefinition> =
        ClassSpellcastingCatalog.allDefinitions()
            .map { definition ->
                CharacterClassDefinition(
                    classId = definition.classId,
                    label = definition.label,
                    defaultKeyAbility = definition.defaultKeyAbility,
                    keyAbilityOptions = definition.keyAbilityOptions,
                )
            }
            .plus(
                CharacterClassDefinition(
                    classId = "other",
                    label = "Other",
                    defaultKeyAbility = AbilityScore.INTELLIGENCE,
                    keyAbilityOptions = listOf(
                        AbilityScore.INTELLIGENCE,
                        AbilityScore.WISDOM,
                        AbilityScore.CHARISMA,
                    ),
                ),
            )
            .associateBy { normalizeClassId(it.classId) }

    override fun allDefinitions(): List<CharacterClassDefinition> {
        return byClassId.values.toList()
    }

    override fun phaseOneDefinitions(): List<CharacterClassDefinition> =
        ClassSpellcastingCatalog.allDefinitions()
            .map { definition -> definition.classId }
            .map(::definitionFor)

    override fun definitionFor(classId: String): CharacterClassDefinition {
        return byClassId[normalizeClassId(classId)] ?: byClassId.getValue("other")
    }
}
