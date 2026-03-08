package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AcceptedSpellSourceDao {
    @Query(
        """
        SELECT sourceBook FROM accepted_spell_sources
        WHERE characterId = :characterId
        ORDER BY sourceBook ASC
        """,
    )
    fun observeSourceBooksByCharacter(characterId: Long): Flow<List<String>>

    @Query(
        """
        SELECT sourceBook FROM accepted_spell_sources
        WHERE characterId = :characterId
        ORDER BY sourceBook ASC
        """,
    )
    suspend fun getSourceBooksByCharacter(characterId: Long): List<String>

    @Query("DELETE FROM accepted_spell_sources WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<AcceptedSpellSourceEntity>)

    @Transaction
    suspend fun replaceForCharacter(
        characterId: Long,
        sourceBooks: Set<String>,
    ) {
        deleteByCharacter(characterId)
        if (sourceBooks.isNotEmpty()) {
            insertAll(
                sourceBooks.sorted().map { sourceBook ->
                    AcceptedSpellSourceEntity(
                        characterId = characterId,
                        sourceBook = sourceBook,
                    )
                },
            )
        }
    }
}
