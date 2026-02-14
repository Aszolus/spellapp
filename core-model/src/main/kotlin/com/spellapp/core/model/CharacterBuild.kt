package com.spellapp.core.model

data class CharacterBuildIdentity(
    val characterId: Long,
    val ancestryId: String? = null,
    val heritageId: String? = null,
    val backgroundId: String? = null,
)

data class CharacterBuildOption(
    val id: Long = 0L,
    val characterId: Long,
    val optionType: CharacterBuildOptionType,
    val optionId: String,
    val levelAcquired: Int? = null,
    val metadataJson: String = "{}",
)

enum class CharacterBuildOptionType {
    CLASS,
    CLASS_FEATURE,
    FEAT,
    ARCHETYPE,
    ANCESTRY,
    ANCESTRY_FEATURE,
    BACKGROUND,
    BACKGROUND_FEATURE,
    HERITAGE,
    OTHER,
}
