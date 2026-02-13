package com.spellapp.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusStateDao {
    @Query("SELECT * FROM focus_states WHERE characterId = :characterId LIMIT 1")
    fun observeByCharacter(characterId: Long): Flow<FocusStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: FocusStateEntity)
}
