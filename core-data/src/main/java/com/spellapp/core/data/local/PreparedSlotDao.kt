package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PreparedSlotDao {
    @Query(
        """
        SELECT * FROM prepared_slots
        WHERE characterId = :characterId
        ORDER BY rank ASC, slotIndex ASC
        """,
    )
    fun observeByCharacter(characterId: Long): Flow<List<PreparedSlotEntity>>

    @Query(
        """
        SELECT * FROM prepared_slots
        WHERE characterId = :characterId
          AND trackKey = :trackKey
        ORDER BY rank ASC, slotIndex ASC
        """,
    )
    fun observeByCharacterAndTrack(
        characterId: Long,
        trackKey: String,
    ): Flow<List<PreparedSlotEntity>>

    @Query(
        """
        SELECT * FROM prepared_slots
        WHERE characterId = :characterId
        ORDER BY rank ASC, slotIndex ASC
        """,
    )
    suspend fun getByCharacter(characterId: Long): List<PreparedSlotEntity>

    @Query(
        """
        SELECT slotIndex FROM prepared_slots
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND rank = :rank
        ORDER BY slotIndex ASC
        """,
    )
    suspend fun getSlotIndexesByCharacterAndRank(
        characterId: Long,
        trackKey: String,
        rank: Int,
    ): List<Int>

    @Query(
        """
        SELECT * FROM prepared_slots
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND rank = :rank
          AND slotIndex = :slotIndex
        LIMIT 1
        """,
    )
    suspend fun getByCharacterRankAndIndex(
        characterId: Long,
        trackKey: String,
        rank: Int,
        slotIndex: Int,
    ): PreparedSlotEntity?

    @Query(
        """
        UPDATE prepared_slots
        SET isExpended = :isExpended
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND rank = :rank
          AND slotIndex = :slotIndex
        """,
    )
    suspend fun setExpendedState(
        characterId: Long,
        trackKey: String,
        rank: Int,
        slotIndex: Int,
        isExpended: Boolean,
    ): Int

    @Query(
        """
        UPDATE prepared_slots
        SET preparedSpellId = NULL,
            isExpended = 0
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND rank = :rank
          AND slotIndex = :slotIndex
        """,
    )
    suspend fun clearPreparedSpell(
        characterId: Long,
        trackKey: String,
        rank: Int,
        slotIndex: Int,
    ): Int

    @Query(
        """
        DELETE FROM prepared_slots
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND rank = :rank
          AND slotIndex = :slotIndex
        """,
    )
    suspend fun deleteByCharacterRankAndIndex(
        characterId: Long,
        trackKey: String,
        rank: Int,
        slotIndex: Int,
    ): Int

    @Query("DELETE FROM prepared_slots WHERE characterId = :characterId")
    suspend fun clearByCharacter(characterId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(slots: List<PreparedSlotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: PreparedSlotEntity): Long

    @Transaction
    suspend fun replaceForCharacter(
        characterId: Long,
        slots: List<PreparedSlotEntity>,
    ) {
        clearByCharacter(characterId)
        if (slots.isNotEmpty()) {
            upsertAll(slots)
        }
    }
}
