package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionEventDao {
    @Query(
        """
        SELECT * FROM session_events
        WHERE characterId = :characterId
        ORDER BY createdAtEpochMillis DESC, id DESC
        """,
    )
    fun observeByCharacter(characterId: Long): Flow<List<SessionEventEntity>>

    @Query(
        """
        SELECT * FROM session_events
        WHERE characterId = :characterId
        ORDER BY createdAtEpochMillis DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestByCharacter(characterId: Long): SessionEventEntity?

    @Query(
        """
        SELECT * FROM session_events
        WHERE characterId = :characterId
        ORDER BY createdAtEpochMillis DESC, id DESC
        """,
    )
    suspend fun getByCharacter(characterId: Long): List<SessionEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: SessionEventEntity): Long
}
