package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterBuildOptionDao {
    @Query(
        """
        SELECT * FROM character_build_options
        WHERE characterId = :characterId
        ORDER BY optionType ASC, optionId ASC
        """,
    )
    fun observeByCharacter(characterId: Long): Flow<List<CharacterBuildOptionEntity>>

    @Query(
        """
        SELECT * FROM character_build_options
        WHERE characterId = :characterId
        ORDER BY optionType ASC, optionId ASC
        """,
    )
    suspend fun getByCharacter(characterId: Long): List<CharacterBuildOptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CharacterBuildOptionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CharacterBuildOptionEntity>)

    @Query(
        """
        DELETE FROM character_build_options
        WHERE characterId = :characterId
          AND optionType = :optionType
          AND optionId = :optionId
        """,
    )
    suspend fun deleteByCharacterAndOption(
        characterId: Long,
        optionType: String,
        optionId: String,
    ): Int

    @Query(
        """
        DELETE FROM character_build_options
        WHERE characterId = :characterId
        """,
    )
    suspend fun deleteByCharacter(characterId: Long): Int
}
