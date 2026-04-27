package com.spellapp.core.model

object SlotProgressionKeys {
    const val FULL_PREPARED_STANDARD = "full-prepared-standard"
    const val FULL_SPONTANEOUS_STANDARD = "full-spontaneous-standard"
    const val FULL_SPONTANEOUS_EXPANDED = "full-spontaneous-expanded"
    const val FULL_SPONTANEOUS_REDUCED = "full-spontaneous-reduced"
    const val BOUNDED_STANDARD = "bounded-standard"
    const val SPLIT_PREPARED_STANDARD = "split-prepared-standard"
    const val SPLIT_SPONTANEOUS_SCALING = "split-spontaneous-scaling"
    const val ARCHETYPE_PREPARED = "archetype-prepared"

    private val knownKeys = setOf(
        FULL_PREPARED_STANDARD,
        FULL_SPONTANEOUS_STANDARD,
        FULL_SPONTANEOUS_EXPANDED,
        FULL_SPONTANEOUS_REDUCED,
        BOUNDED_STANDARD,
        SPLIT_PREPARED_STANDARD,
        SPLIT_SPONTANEOUS_SCALING,
        ARCHETYPE_PREPARED,
    )

    fun normalize(key: String): String = key.trim().lowercase()

    fun isKnown(key: String): Boolean = normalize(key) in knownKeys

    fun defaultFor(progressionType: CastingProgressionType): String {
        return when (progressionType) {
            CastingProgressionType.FULL_PREPARED -> FULL_PREPARED_STANDARD
            CastingProgressionType.FULL_SPONTANEOUS -> FULL_SPONTANEOUS_STANDARD
            CastingProgressionType.BOUNDED_PREPARED,
            CastingProgressionType.BOUNDED_SPONTANEOUS,
            -> BOUNDED_STANDARD
            CastingProgressionType.ANIMIST_PREPARED -> SPLIT_PREPARED_STANDARD
            CastingProgressionType.ANIMIST_APPARITION_SPONTANEOUS -> SPLIT_SPONTANEOUS_SCALING
            CastingProgressionType.ARCHETYPE_PREPARED -> ARCHETYPE_PREPARED
        }
    }
}
