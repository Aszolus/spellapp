package com.spellapp.feature.character

import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CharacterClass

internal fun CharacterClass.label(
    classDefinitions: Map<CharacterClass, CharacterClassDefinition> = emptyMap(),
): String {
    return classDefinitions[this]?.label
        ?: StaticCharacterClassDefinitionSource.definitionFor(this).label
}

internal fun AbilityScore.label(): String {
    return when (this) {
        AbilityScore.STRENGTH -> "STR"
        AbilityScore.DEXTERITY -> "DEX"
        AbilityScore.CONSTITUTION -> "CON"
        AbilityScore.INTELLIGENCE -> "INT"
        AbilityScore.WISDOM -> "WIS"
        AbilityScore.CHARISMA -> "CHA"
    }
}

internal fun defaultKeyAbility(
    characterClass: CharacterClass,
    classDefinitions: Map<CharacterClass, CharacterClassDefinition> = emptyMap(),
): AbilityScore {
    return classDefinitions[characterClass]?.defaultKeyAbility
        ?: StaticCharacterClassDefinitionSource.definitionFor(characterClass).defaultKeyAbility
}

internal fun keyAbilityOptions(
    characterClass: CharacterClass,
    classDefinitions: Map<CharacterClass, CharacterClassDefinition> = emptyMap(),
): List<AbilityScore> {
    return classDefinitions[characterClass]?.keyAbilityOptions
        ?: StaticCharacterClassDefinitionSource.definitionFor(characterClass).keyAbilityOptions
}

internal fun sanitizeSignedNumber(value: String, maxLength: Int): String {
    if (value.isEmpty()) {
        return value
    }
    val first = value.first()
    val hasSign = first == '-' || first == '+'
    val digits = if (hasSign) {
        value.drop(1).filter(Char::isDigit)
    } else {
        value.filter(Char::isDigit)
    }
    val trimmedDigits = digits.take(maxLength - if (hasSign) 1 else 0)
    return if (hasSign) {
        "$first$trimmedDigits"
    } else {
        trimmedDigits
    }
}

internal fun Int.withSign(): String {
    return if (this >= 0) "+$this" else toString()
}
