package com.spellapp.core.data.local

import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.KnownSpellOrigin
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
        knownRank: Int?,
        origin: KnownSpellOrigin,
        isLocked: Boolean,
        isSignature: Boolean,
    ): Long {
        return dao.insert(
            KnownSpellEntity(
                characterId = characterId,
                trackKey = trackKey,
                spellId = spellId,
                knownRank = knownRank.toEntityRank(),
                origin = origin.name,
                isLocked = isLocked,
                isSignature = isSignature,
            ),
        )
    }

    override suspend fun removeKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int?,
    ): Boolean {
        if (knownRank != null) {
            return dao.deleteByCharacterTrackSpellAndRank(
                characterId = characterId,
                trackKey = trackKey,
                spellId = spellId,
                knownRank = knownRank.toEntityRank(),
            ) > 0
        }
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
        knownRank: Int?,
    ): Boolean {
        if (knownRank == null) {
            return dao.getAnyRankByCharacterTrackAndSpell(
                characterId = characterId,
                trackKey = trackKey,
                spellId = spellId,
            ) != null
        }
        return dao.getByCharacterTrackAndSpell(
            characterId = characterId,
            trackKey = trackKey,
            spellId = spellId,
            knownRank = knownRank.toEntityRank(),
        ) != null
    }

    private fun KnownSpellEntity.toDomain(): KnownSpell {
        return KnownSpell(
            id = id,
            characterId = characterId,
            trackKey = trackKey,
            spellId = spellId,
            knownRank = knownRank.toDomainRank(),
            origin = enumValueOrDefault(origin, KnownSpellOrigin.MANUAL),
            isLocked = isLocked,
            isSignature = isSignature,
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        rawValue: String,
        defaultValue: T,
    ): T {
        return runCatching { enumValueOf<T>(rawValue) }.getOrDefault(defaultValue)
    }

    private fun Int?.toEntityRank(): Int = this ?: UNSPECIFIED_RANK

    private fun Int.toDomainRank(): Int? = takeUnless { it == UNSPECIFIED_RANK }

    private companion object {
        private const val UNSPECIFIED_RANK = -1
    }
}
