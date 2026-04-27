package com.spellapp.feature.character

import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.normalizeClassId

internal fun String.classLabel(
    classDefinitions: Map<String, CharacterClassDefinition> = emptyMap(),
): String {
    val classId = normalizeClassId(this)
    return classDefinitions[classId]?.label
        ?: StaticCharacterClassDefinitionSource.definitionFor(classId).label
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
    classId: String,
    classDefinitions: Map<String, CharacterClassDefinition> = emptyMap(),
): AbilityScore {
    val normalized = normalizeClassId(classId)
    return classDefinitions[normalized]?.defaultKeyAbility
        ?: StaticCharacterClassDefinitionSource.definitionFor(normalized).defaultKeyAbility
}

internal fun keyAbilityOptions(
    classId: String,
    classDefinitions: Map<String, CharacterClassDefinition> = emptyMap(),
): List<AbilityScore> {
    val normalized = normalizeClassId(classId)
    return classDefinitions[normalized]?.keyAbilityOptions
        ?: StaticCharacterClassDefinitionSource.definitionFor(normalized).keyAbilityOptions
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
