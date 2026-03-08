package com.spellapp.core.model

fun CastingTrack.preferredSpellTradition(): String? {
    return preferredSpellTraditionForSource(
        sourceType = sourceType,
        sourceId = sourceId,
    )
}

fun preferredSpellTraditionForSource(
    sourceType: CastingTrackSourceType,
    sourceId: String,
): String? {
    val normalizedSourceId = sourceId.trim().lowercase()
    return when (sourceType) {
        CastingTrackSourceType.PRIMARY_CLASS,
        CastingTrackSourceType.ARCHETYPE,
        -> when (normalizedSourceId) {
            "wizard" -> "arcane"
            "cleric" -> "divine"
            "druid" -> "primal"
            else -> null
        }
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
