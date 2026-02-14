package com.spellapp.core.data

import com.spellapp.core.model.CastingTrack
import kotlinx.coroutines.flow.Flow

interface CastingTrackRepository {
    fun observeCastingTracks(characterId: Long): Flow<List<CastingTrack>>
    suspend fun getCastingTracks(characterId: Long): List<CastingTrack>
    suspend fun upsertCastingTrack(track: CastingTrack): Long
    suspend fun deleteCastingTrack(
        characterId: Long,
        trackKey: String,
    ): Boolean
}
