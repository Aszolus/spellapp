package com.spellapp.core.model

data class CastingTrack(
    val id: Long = 0L,
    val characterId: Long,
    val trackKey: String,
    val sourceType: CastingTrackSourceType,
    val sourceId: String,
    val progressionType: CastingProgressionType,
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
    ARCHETYPE_PREPARED,
}
