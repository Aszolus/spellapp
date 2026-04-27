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
        ORDER BY spellId ASC, knownRank ASC
        """,
    )
    fun observeByCharacterAndTrack(
        characterId: Long,
        trackKey: String,
    ): Flow<List<KnownSpellEntity>>

    @Query(
        """
        SELECT DISTINCT spellId FROM known_spells
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
          AND knownRank = :knownRank
        LIMIT 1
        """,
    )
    suspend fun getByCharacterTrackAndSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int,
    ): KnownSpellEntity?

    @Query(
        """
        SELECT * FROM known_spells
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND spellId = :spellId
        LIMIT 1
        """,
    )
    suspend fun getAnyRankByCharacterTrackAndSpell(
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
          AND isLocked = 0
        """,
    )
    suspend fun deleteByCharacterTrackAndSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
    ): Int

    @Query(
        """
        DELETE FROM known_spells
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND spellId = :spellId
          AND knownRank = :knownRank
          AND isLocked = 0
        """,
    )
    suspend fun deleteByCharacterTrackSpellAndRank(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int,
    ): Int

    @Query(
        """
        UPDATE known_spells
        SET isSignature = :isSignature
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND spellId = :spellId
        """,
    )
    suspend fun updateSignatureByCharacterTrackAndSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        isSignature: Boolean,
    ): Int

    @Query(
        """
        UPDATE known_spells
        SET isSignature = :isSignature
        WHERE characterId = :characterId
          AND trackKey = :trackKey
          AND spellId = :spellId
          AND knownRank = :knownRank
        """,
    )
    suspend fun updateSignatureByCharacterTrackSpellAndRank(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int,
        isSignature: Boolean,
    ): Int
}
