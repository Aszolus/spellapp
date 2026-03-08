package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownSpellDao {
    @Query(
        """
        SELECT * FROM known_spells
        WHERE characterId = :characterId
          AND trackKey = :trackKey
        ORDER BY spellId ASC
        """,
    )
    fun observeByCharacterAndTrack(
        characterId: Long,
        trackKey: String,
    ): Flow<List<KnownSpellEntity>>

    @Query(
        """
        SELECT spellId FROM known_spells
        WHERE characterId = :characterId
          AND trackKey = :trackKey
        ORDER BY spellId ASC
        """,
    )
    fun observeSpellIdsByCharacterAndTrack(
        characterId: Long,
        trackKey: String,
    ): Flow<List<String>>

    @Query(
        """
        SELECT * FROM known_spells
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND spellId = :spellId
        LIMIT 1
        """,
    )
    suspend fun getByCharacterTrackAndSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
    ): KnownSpellEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(knownSpell: KnownSpellEntity): Long

    @Query(
        """
        DELETE FROM known_spells
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND spellId = :spellId
        """,
    )
    suspend fun deleteByCharacterTrackAndSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
    ): Int
}
