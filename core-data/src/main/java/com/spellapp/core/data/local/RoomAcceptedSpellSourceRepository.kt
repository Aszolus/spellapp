package com.spellapp.core.data.local

import com.spellapp.core.data.AcceptedSpellSourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAcceptedSpellSourceRepository(
    private val dao: AcceptedSpellSourceDao,
) : AcceptedSpellSourceRepository {

    override fun observeAcceptedSources(characterId: Long): Flow<Set<String>> {
        return dao.observeSourceBooksByCharacter(characterId).map { it.toSet() }
    }

    override suspend fun getAcceptedSources(characterId: Long): Set<String> {
        return dao.getSourceBooksByCharacter(characterId).toSet()
    }

    override suspend fun replaceAcceptedSources(
        characterId: Long,
        sources: Set<String>,
    ) {
        dao.replaceForCharacter(
            characterId = characterId,
            sourceBooks = sources,
        )
    }
}
