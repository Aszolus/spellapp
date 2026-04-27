package com.spellapp.core.data.local

import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.ClassSpellcastingCatalog
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.EmptyClassSpellcastingCatalogSource
import com.spellapp.core.model.SlotProgressionKeys
import com.spellapp.core.model.slotCountsByProgressionKey
import com.spellapp.core.model.slotCountsForTrack

internal interface SlotProgressionEngine {
    fun slotCountsByRank(
        level: Int,
        track: CastingTrack,
        selectedBuildOptionIds: Set<String> = emptySet(),
    ): Map<Int, Int>
}

internal class DefaultSlotProgressionEngine(
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        EmptyClassSpellcastingCatalogSource,
) : SlotProgressionEngine {
    override fun slotCountsByRank(
        level: Int,
        track: CastingTrack,
        selectedBuildOptionIds: Set<String>,
    ): Map<Int, Int> {
        classSpellcastingCatalogSource.slotCountsByProgressionKey(
            progressionKey = track.slotProgressionKey,
            level = level,
        )?.let { return it }
        classSpellcastingCatalogSource.slotCountsForTrack(
            trackKey = track.trackKey,
            sourceId = track.sourceId,
            level = level,
        )?.let { return it }

        val progressionKey = progressionKeyFor(track)
        return when (track.progressionType) {
            CastingProgressionType.FULL_PREPARED -> fullCaster(
                level = level,
                profile = fullCasterProfile(progressionKey),
            )
            CastingProgressionType.FULL_SPONTANEOUS -> fullSpontaneous(
                level = level,
                progressionKey = progressionKey,
            )
            CastingProgressionType.BOUNDED_PREPARED,
            CastingProgressionType.BOUNDED_SPONTANEOUS,
            -> boundedCaster(level)
            CastingProgressionType.ANIMIST_PREPARED -> splitPrepared(level)
            CastingProgressionType.ANIMIST_APPARITION_SPONTANEOUS -> splitSpontaneousScaling(level)
            CastingProgressionType.ARCHETYPE_PREPARED -> archetypePrepared(
                level = level,
                track = track,
                selectedBuildOptionIds = selectedBuildOptionIds,
            )
        }
    }

    private fun progressionKeyFor(track: CastingTrack): String {
        val storedKey = SlotProgressionKeys.normalize(track.slotProgressionKey)
        if (SlotProgressionKeys.isKnown(storedKey)) {
            return storedKey
        }

        if (track.sourceType == CastingTrackSourceType.PRIMARY_CLASS) {
            val catalogKey = ClassSpellcastingCatalog.classFromId(track.sourceId)
                ?.let(classSpellcastingCatalogSource::definitionFor)
                ?.primaryTracks
                ?.firstOrNull { definition -> definition.trackKey == track.trackKey }
                ?.slotProgressionKey
            if (catalogKey != null && SlotProgressionKeys.isKnown(catalogKey)) {
                return SlotProgressionKeys.normalize(catalogKey)
            }
        }

        return SlotProgressionKeys.defaultFor(track.progressionType)
    }

    private fun fullSpontaneous(
        level: Int,
        progressionKey: String,
    ): Map<Int, Int> {
        return fullCaster(
            level = level,
            profile = fullCasterProfile(progressionKey),
        )
    }

    private fun fullCaster(
        level: Int,
        profile: FullCasterSlotProfile,
    ): Map<Int, Int> {
        val slots = mutableMapOf<Int, Int>()
        // Slot storage models cantrips as rank-0 entries.
        // Runtime cast logic treats rank 0 as non-expending.
        slots[0] = profile.cantrips
        for (rank in 1..9) {
            val unlockLevel = (rank * 2) - 1
            if (level >= unlockLevel) {
                slots[rank] = if (level == unlockLevel) {
                    profile.slotsAtUnlock
                } else {
                    profile.slotsAfterUnlock
                }
            }
        }
        if (level >= 19) {
            slots[10] = if (level >= 20) {
                profile.rankTenSlotsAtLevel20
            } else {
                profile.rankTenSlotsAtLevel19
            }
        }
        return slots
    }

    private fun boundedCaster(level: Int): Map<Int, Int> {
        val highestRank = ((level + 1) / 2).coerceIn(1, 9)
        val slots = mutableMapOf(0 to 5)
        if (highestRank == 1) {
            slots[1] = if (level == 1) 1 else 2
            return slots
        }
        val highestSlots = if (level == 3) 1 else 2
        slots[highestRank - 1] = 2
        slots[highestRank] = highestSlots
        return slots
    }

    private fun splitPrepared(level: Int): Map<Int, Int> {
        val slots = mutableMapOf(0 to 2)
        for (rank in 1..9) {
            val unlockLevel = (rank * 2) - 1
            if (level >= unlockLevel) {
                slots[rank] = if (level == unlockLevel) 1 else 2
            }
        }
        return slots
    }

    private fun splitSpontaneousScaling(level: Int): Map<Int, Int> {
        val cantrips = when {
            level >= 15 -> 4
            level >= 7 -> 3
            else -> 2
        }
        val slots = mutableMapOf(0 to cantrips)
        for (rank in 1..9) {
            val unlockLevel = (rank * 2) - 1
            if (level >= unlockLevel) {
                slots[rank] = splitSpontaneousScalingSlotsForRank(level, rank)
            }
        }
        if (level >= 19) {
            slots[10] = 1
        }
        return slots
    }

    private fun splitSpontaneousScalingSlotsForRank(
        level: Int,
        rank: Int,
    ): Int {
        val secondSlotLevel = when (rank) {
            1, 2, 3 -> 10
            4 -> 11
            5 -> 13
            6 -> 15
            7 -> 17
            8 -> 19
            else -> Int.MAX_VALUE
        }
        return if (level >= secondSlotLevel) 2 else 1
    }

    private fun fullCasterProfile(progressionKey: String): FullCasterSlotProfile {
        return when (SlotProgressionKeys.normalize(progressionKey)) {
            SlotProgressionKeys.FULL_SPONTANEOUS_EXPANDED -> FullCasterSlotProfile(
                cantrips = 5,
                slotsAtUnlock = 3,
                slotsAfterUnlock = 4,
            )
            SlotProgressionKeys.FULL_SPONTANEOUS_REDUCED -> FullCasterSlotProfile(
                cantrips = 3,
                slotsAtUnlock = 1,
                slotsAfterUnlock = 2,
                rankTenSlotsAtLevel20 = 2,
            )
            else -> FullCasterSlotProfile(
                cantrips = 5,
                slotsAtUnlock = 2,
                slotsAfterUnlock = 3,
            )
        }
    }

    private fun archetypePrepared(
        level: Int,
        track: CastingTrack,
        selectedBuildOptionIds: Set<String>,
    ): Map<Int, Int> {
        val archetypeId = archetypeIdFromTrack(track.trackKey)
            ?: return archetypePreparedLegacy(level)
        val archetypeOptionPrefix = "archetype/$archetypeId/"
        val archetypeOptions = selectedBuildOptionIds
            .filter { optionId -> optionId.startsWith(archetypeOptionPrefix) }
            .map { optionId -> optionId.lowercase() }
            .toSet()
        if (archetypeOptions.isEmpty()) {
            return if (isLegacyArchetypeTrack(track.trackKey)) {
                archetypePreparedLegacy(level)
            } else {
                emptyMap()
            }
        }
        val optionSlugs = archetypeOptions.map { optionId ->
            optionId.removePrefix(archetypeOptionPrefix)
        }.toSet()

        val hasBasicSpellcasting = optionSlugs.any { slug ->
            BASIC_SPELLCASTING_REGEX.matches(slug)
        }
        val hasExpertSpellcasting = optionSlugs.any { slug ->
            EXPERT_SPELLCASTING_REGEX.matches(slug)
        }
        val hasMasterSpellcasting = optionSlugs.any { slug ->
            MASTER_SPELLCASTING_REGEX.matches(slug)
        }

        return buildMap {
            if (hasBasicSpellcasting) {
                if (level >= 4) put(1, 1)
                if (level >= 6) put(2, 1)
                if (level >= 8) put(3, 1)
            }
            if (hasExpertSpellcasting) {
                if (level >= 12) put(4, 1)
                if (level >= 14) put(5, 1)
                if (level >= 16) put(6, 1)
            }
            if (hasMasterSpellcasting) {
                if (level >= 18) put(7, 1)
                if (level >= 20) put(8, 1)
            }
        }
    }

    private fun archetypePreparedLegacy(level: Int): Map<Int, Int> {
        val unlockByRank = mapOf(
            1 to 4,
            2 to 6,
            3 to 8,
            4 to 10,
            5 to 12,
            6 to 14,
            7 to 16,
            8 to 18,
        )
        return buildMap {
            unlockByRank.forEach { (rank, unlockLevel) ->
                if (level >= unlockLevel) {
                    put(rank, 1)
                }
            }
        }
    }

    private fun archetypeIdFromTrack(trackKey: String): String? {
        val prefix = "archetype-"
        if (!trackKey.startsWith(prefix)) {
            return null
        }
        val archetypeId = trackKey.removePrefix(prefix).trim()
        return archetypeId.takeIf { it.isNotBlank() }
    }

    private fun isLegacyArchetypeTrack(trackKey: String): Boolean {
        return LEGACY_ARCHETYPE_TRACK_REGEX.matches(trackKey)
    }

    private companion object {
        private val LEGACY_ARCHETYPE_TRACK_REGEX = Regex("^archetype-(\\d+|legacy.*)$")
        private val BASIC_SPELLCASTING_REGEX = Regex("^basic-[a-z0-9-]+-spellcasting$")
        private val EXPERT_SPELLCASTING_REGEX = Regex("^expert-[a-z0-9-]+-spellcasting$")
        private val MASTER_SPELLCASTING_REGEX = Regex("^master-[a-z0-9-]+-spellcasting$")
    }
}

private data class FullCasterSlotProfile(
    val cantrips: Int,
    val slotsAtUnlock: Int,
    val slotsAfterUnlock: Int,
    val rankTenSlotsAtLevel19: Int = 1,
    val rankTenSlotsAtLevel20: Int = 1,
)
