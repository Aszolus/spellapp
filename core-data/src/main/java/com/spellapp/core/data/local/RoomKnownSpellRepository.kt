package com.spellapp.core.data.local

import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.model.KnownSpell
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomKnownSpellRepository(
    private val dao: KnownSpellDao,
) : KnownSpellRepository {

    override fun observeKnownSpells(
        characterId: Long,
        trackKey: String,
    ): Flow<List<KnownSpell>> {
        return dao.observeByCharacterAndTrack(
            characterId = characterId,
            trackKey = trackKey,
        ).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeKnownSpellIds(
        characterId: Long,
        trackKey: String,
    ): Flow<Set<String>> {
        return dao.observeSpellIdsByCharacterAndTrack(
            characterId = characterId,
            trackKey = trackKey,
        ).map { ids ->
            ids.toSet()
        }
    }

    override suspend fun addKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
    ): Long {
        return dao.insert(
            KnownSpellEntity(
                characterId = characterId,
                trackKey = trackKey,
                spellId = spellId,
            ),
        )
    }

    override suspend fun removeKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
    ): Boolean {
        return dao.deleteByCharacterTrackAndSpell(
            characterId = characterId,
            trackKey = trackKey,
            spellId = spellId,
        ) > 0
    }

    override suspend fun isKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
    ): Boolean {
        return dao.getByCharacterTrackAndSpell(
            characterId = characterId,
            trackKey = trackKey,
            spellId = spellId,
        ) != null
    }

    private fun KnownSpellEntity.toDomain(): KnownSpell {
        return KnownSpell(
            id = id,
            characterId = characterId,
            trackKey = trackKey,
            spellId = spellId,
        )
    }
}
