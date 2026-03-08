package com.spellapp.core.data

import kotlinx.coroutines.flow.Flow

interface AcceptedSpellSourceRepository {
    fun observeAcceptedSources(characterId: Long): Flow<Set<String>>

    suspend fun getAcceptedSources(characterId: Long): Set<String>

    suspend fun replaceAcceptedSources(
        characterId: Long,
        sources: Set<String>,
    )
}
