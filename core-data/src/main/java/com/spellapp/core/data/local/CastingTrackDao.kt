package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CastingTrackDao {
    @Query(
        """
        SELECT * FROM casting_tracks
        WHERE characterId = :characterId
        ORDER BY trackKey ASC
        """,
    )
    fun observeByCharacter(characterId: Long): Flow<List<CastingTrackEntity>>

    @Query(
        """
        SELECT * FROM casting_tracks
        WHERE characterId = :characterId
        ORDER BY trackKey ASC
        """,
    )
    suspend fun getByCharacter(characterId: Long): List<CastingTrackEntity>

    @Query(
        """
        SELECT * FROM casting_tracks
        WHERE characterId = :characterId
          AND trackKey = :trackKey
        LIMIT 1
        """,
    )
    suspend fun getByCharacterAndTrack(
        characterId: Long,
        trackKey: String,
    ): CastingTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: CastingTrackEntity): Long

    @Query(
        """
        DELETE FROM casting_tracks
        WHERE characterId = :characterId
          AND trackKey = :trackKey
        """,
    )
    suspend fun deleteByCharacterAndTrack(
        characterId: Long,
        trackKey: String,
    ): Int
}
