package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SpellDao {
    @Query(
        """
        SELECT id, name, rank, traditionSummary AS tradition, (rank = 0) AS isCantrip
        FROM spells
        WHERE (:query = '' OR name LIKE '%' || :query || '%')
          AND (:rank IS NULL OR rank = :rank)
          AND (:tradition = '' OR LOWER(traditionSummary) LIKE '%' || LOWER(:tradition) || '%')
          AND (:rarity = '' OR LOWER(rarity) = LOWER(:rarity))
          AND (:trait = '' OR LOWER(traitsCsv) LIKE '%' || LOWER(:trait) || '%')
        ORDER BY rank ASC, name ASC
        """,
    )
    fun observeSpellList(
        query: String,
        rank: Int?,
        tradition: String,
        rarity: String,
        trait: String,
    ): Flow<List<SpellListItem>>

    @Query("SELECT * FROM spells WHERE id = :spellId LIMIT 1")
    suspend fun getSpellById(spellId: String): SpellEntity?

    @Query("SELECT COUNT(*) FROM spells")
    suspend fun getSpellCount(): Int

    @Query("SELECT COUNT(*) FROM spells WHERE rank = :rank")
    suspend fun getSpellCountByRank(rank: Int): Int

    @Query("DELETE FROM spells")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(spells: List<SpellEntity>)

    @Transaction
    suspend fun replaceAll(spells: List<SpellEntity>) {
        clearAll()
        upsertAll(spells)
    }
}
