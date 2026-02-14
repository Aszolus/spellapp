package com.spellapp.core.data.local

import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack

internal interface SlotProgressionEngine {
    fun slotCountsByRank(
        level: Int,
        track: CastingTrack,
        selectedBuildOptionIds: Set<String> = emptySet(),
    ): Map<Int, Int>
}

internal class DefaultSlotProgressionEngine : SlotProgressionEngine {
    override fun slotCountsByRank(
        level: Int,
        track: CastingTrack,
        selectedBuildOptionIds: Set<String>,
    ): Map<Int, Int> {
        return when (track.progressionType) {
            CastingProgressionType.FULL_PREPARED -> fullPrepared(level)
            CastingProgressionType.ARCHETYPE_PREPARED -> archetypePrepared(
                level = level,
                track = track,
                selectedBuildOptionIds = selectedBuildOptionIds,
            )
        }
    }

    private fun fullPrepared(level: Int): Map<Int, Int> {
        val slots = mutableMapOf<Int, Int>()
        // Current prepared-slot UI models prepared cantrips as rank-0 entries.
        // Runtime cast logic treats rank 0 as non-expending.
        slots[0] = 5
        for (rank in 1..9) {
            val unlockLevel = (rank * 2) - 1
            if (level >= unlockLevel) {
                slots[rank] = if (level == unlockLevel) 2 else 3
            }
        }
        if (level >= 19) {
            slots[10] = 1
        }
        return slots
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
