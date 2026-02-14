package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterBuildIdentityDao {
    @Query(
        """
        SELECT * FROM character_build_identity
        WHERE characterId = :characterId
        LIMIT 1
        """,
    )
    fun observeByCharacter(characterId: Long): Flow<CharacterBuildIdentityEntity?>

    @Query(
        """
        SELECT * FROM character_build_identity
        WHERE characterId = :characterId
        LIMIT 1
        """,
    )
    suspend fun getByCharacter(characterId: Long): CharacterBuildIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CharacterBuildIdentityEntity)

    @Query(
        """
        DELETE FROM character_build_identity
        WHERE characterId = :characterId
        """,
    )
    suspend fun deleteByCharacter(characterId: Long): Int
}
