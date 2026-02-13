package com.spellapp.core.data

import com.spellapp.core.model.CharacterProfile
import kotlinx.coroutines.flow.Flow

interface CharacterCrudRepository {
    fun observeCharacters(): Flow<List<CharacterProfile>>
    suspend fun getCharacter(characterId: Long): CharacterProfile?
    suspend fun upsertCharacter(character: CharacterProfile): Long
    suspend fun deleteCharacter(characterId: Long)
}
