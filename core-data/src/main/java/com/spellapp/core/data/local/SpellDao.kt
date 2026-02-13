package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SpellDao {
    @Query(
        """
        SELECT id, name, rank, traditionSummary AS tradition
        FROM spells
        WHERE (:query = '' OR name LIKE '%' || :query || '%')
        ORDER BY rank ASC, name ASC
        """,
    )
    fun observeSpellList(query: String): Flow<List<SpellListItem>>

    @Query("SELECT * FROM spells WHERE id = :spellId LIMIT 1")
    suspend fun getSpellById(spellId: String): SpellEntity?

    @Query("SELECT COUNT(*) FROM spells")
    suspend fun getSpellCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(spells: List<SpellEntity>)
}
