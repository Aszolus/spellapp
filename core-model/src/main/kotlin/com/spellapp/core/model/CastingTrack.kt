package com.spellapp.core.model

data class CastingTrack(
    val id: Long = 0L,
    val characterId: Long,
    val trackKey: String,
    val sourceType: CastingTrackSourceType,
    val sourceId: String,
    val progressionType: CastingProgressionType,
    val displayName: String = "",
    val castingStyle: CastingStyle = CastingStyle.PREPARED,
    val tradition: SpellcastingTradition? = null,
    val slotProgressionKey: String = "",
) {
    companion object {
        const val PRIMARY_TRACK_KEY = PreparedSlot.PRIMARY_TRACK_KEY
    }
}

enum class CastingTrackSourceType {
    PRIMARY_CLASS,
    ARCHETYPE,
}

enum class CastingProgressionType {
    FULL_PREPARED,
    FULL_SPONTANEOUS,
    BOUNDED_PREPARED,
    BOUNDED_SPONTANEOUS,
    ANIMIST_PREPARED,
    ANIMIST_APPARITION_SPONTANEOUS,
    ARCHETYPE_PREPARED,
}

enum class CastingStyle {
    PREPARED,
    SPONTANEOUS,
}

enum class SpellcastingTradition {
    ARCANE,
    DIVINE,
    OCCULT,
    PRIMAL,
    VARIABLE,
    OTHER,
}
