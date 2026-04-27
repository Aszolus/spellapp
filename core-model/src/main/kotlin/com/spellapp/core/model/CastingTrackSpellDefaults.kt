package com.spellapp.core.model

fun CastingTrack.preferredSpellTradition(): String? {
    tradition.preferredTraditionString()?.let { return it }
    return preferredSpellTraditionForSource(
        sourceType = sourceType,
        sourceId = sourceId,
    )
}

fun preferredSpellTraditionForSource(
    sourceType: CastingTrackSourceType,
    sourceId: String,
): String? {
    return when (sourceType) {
        CastingTrackSourceType.PRIMARY_CLASS,
        CastingTrackSourceType.ARCHETYPE,
        -> ClassSpellcastingCatalog.classFromId(sourceId)
            ?.let { characterClass ->
                ClassSpellcastingCatalog.traditionFor(
                    characterClass = characterClass,
                    selectedOptionIds = emptySet(),
                )
            }
            .preferredTraditionString()
    }
}

fun SpellcastingTradition?.preferredTraditionString(): String? {
    return when (this) {
        SpellcastingTradition.ARCANE -> "arcane"
        SpellcastingTradition.DIVINE -> "divine"
        SpellcastingTradition.OCCULT -> "occult"
        SpellcastingTradition.PRIMAL -> "primal"
        SpellcastingTradition.VARIABLE,
        SpellcastingTradition.OTHER,
        null,
        -> null
    }
}

fun spellSupportsTradition(
    traditions: String,
    preferredTradition: String,
): Boolean {
    val normalizedTradition = preferredTradition.trim().lowercase()
    if (normalizedTradition.isBlank()) {
        return false
    }
    return traditions.split(',')
        .map { tradition -> tradition.trim().lowercase() }
        .any { tradition -> tradition == normalizedTradition }
}
