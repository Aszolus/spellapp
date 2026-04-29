package com.spellapp

import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.RulesReferenceRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.KnownSpellOrigin
import com.spellapp.core.model.RulesReferenceEntry
import com.spellapp.core.model.RulesReferenceKey
import com.spellapp.core.model.RulesTextDocument
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import com.spellapp.feature.character.BuilderFeatRecord
import com.spellapp.feature.character.CharacterBuilderCatalogResult
import com.spellapp.feature.character.CharacterBuilderCatalogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

internal class DeferredSpellRepository(
    private val provider: () -> SpellRepository,
) : SpellRepository {
    @Volatile
    private var delegate: SpellRepository? = null

    override fun observeAvailableSources(): Flow<List<String>> = deferredFlow {
        observeAvailableSources()
    }

    override fun observeAvailableTraits(): Flow<List<String>> = deferredFlow {
        observeAvailableTraits()
    }

    override fun observeSpells(
        query: String,
        rank: Int?,
        tradition: String?,
        rarity: String?,
        trait: String?,
    ): Flow<List<SpellListItem>> = deferredFlow {
        observeSpells(
            query = query,
            rank = rank,
            tradition = tradition,
            rarity = rarity,
            trait = trait,
        )
    }

    override suspend fun getSpellDetail(spellId: String): SpellDetail? =
        withDelegateOnIo { getSpellDetail(spellId) }

    override suspend fun getSpellDetails(spellIds: Collection<String>): Map<String, SpellDetail> =
        withDelegateOnIo { getSpellDetails(spellIds) }

    override suspend fun getSpellRanks(spellIds: Collection<String>): Map<String, Int> =
        withDelegateOnIo { getSpellRanks(spellIds) }

    override suspend fun seedFromDatasetIfEmpty(datasetJson: String) {
        withDelegateOnIo { seedFromDatasetIfEmpty(datasetJson) }
    }

    private fun <T> deferredFlow(block: SpellRepository.() -> Flow<T>): Flow<T> = flow {
        val source = withContext(Dispatchers.IO) { delegate().block() }
        emitAll(source)
    }

    private suspend fun <T> withDelegateOnIo(block: suspend SpellRepository.() -> T): T =
        withContext(Dispatchers.IO) { delegate().block() }

    private fun delegate(): SpellRepository =
        delegate ?: synchronized(this) {
            delegate ?: provider().also { delegate = it }
        }
}

internal class DeferredAcceptedSpellSourceRepository(
    private val provider: () -> AcceptedSpellSourceRepository,
) : AcceptedSpellSourceRepository {
    @Volatile
    private var delegate: AcceptedSpellSourceRepository? = null

    override fun observeAcceptedSources(characterId: Long): Flow<Set<String>> = flow {
        val source = withContext(Dispatchers.IO) {
            delegate().observeAcceptedSources(characterId)
        }
        emitAll(source)
    }

    override suspend fun getAcceptedSources(characterId: Long): Set<String> =
        withContext(Dispatchers.IO) { delegate().getAcceptedSources(characterId) }

    override suspend fun replaceAcceptedSources(
        characterId: Long,
        sources: Set<String>,
    ) {
        withContext(Dispatchers.IO) {
            delegate().replaceAcceptedSources(characterId, sources)
        }
    }

    private fun delegate(): AcceptedSpellSourceRepository =
        delegate ?: synchronized(this) {
            delegate ?: provider().also { delegate = it }
        }
}

internal class DeferredKnownSpellRepository(
    private val provider: () -> KnownSpellRepository,
) : KnownSpellRepository {
    @Volatile
    private var delegate: KnownSpellRepository? = null

    override fun observeKnownSpells(
        characterId: Long,
        trackKey: String,
    ): Flow<List<KnownSpell>> = flow {
        val source = withContext(Dispatchers.IO) {
            delegate().observeKnownSpells(characterId, trackKey)
        }
        emitAll(source)
    }

    override fun observeKnownSpellIds(
        characterId: Long,
        trackKey: String,
    ): Flow<Set<String>> = flow {
        val source = withContext(Dispatchers.IO) {
            delegate().observeKnownSpellIds(characterId, trackKey)
        }
        emitAll(source)
    }

    override suspend fun addKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int?,
        origin: KnownSpellOrigin,
        isLocked: Boolean,
        isSignature: Boolean,
    ): Long = withContext(Dispatchers.IO) {
        delegate().addKnownSpell(
            characterId = characterId,
            trackKey = trackKey,
            spellId = spellId,
            knownRank = knownRank,
            origin = origin,
            isLocked = isLocked,
            isSignature = isSignature,
        )
    }

    override suspend fun removeKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int?,
    ): Boolean = withContext(Dispatchers.IO) {
        delegate().removeKnownSpell(characterId, trackKey, spellId, knownRank)
    }

    override suspend fun removeKnownSpellById(knownSpellId: Long): Boolean =
        withContext(Dispatchers.IO) { delegate().removeKnownSpellById(knownSpellId) }

    override suspend fun setSignatureSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        isSignature: Boolean,
        knownRank: Int?,
    ): Boolean = withContext(Dispatchers.IO) {
        delegate().setSignatureSpell(characterId, trackKey, spellId, isSignature, knownRank)
    }

    override suspend fun isKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int?,
    ): Boolean = withContext(Dispatchers.IO) {
        delegate().isKnownSpell(characterId, trackKey, spellId, knownRank)
    }

    private fun delegate(): KnownSpellRepository =
        delegate ?: synchronized(this) {
            delegate ?: provider().also { delegate = it }
        }
}

internal class DeferredRulesReferenceRepository(
    private val provider: () -> RulesReferenceRepository,
) : RulesReferenceRepository {
    @Volatile
    private var delegate: RulesReferenceRepository? = null

    override suspend fun getEntry(key: RulesReferenceKey): RulesReferenceEntry? =
        withContext(Dispatchers.IO) { delegate().getEntry(key) }

    override suspend fun getEntries(keys: Collection<RulesReferenceKey>): Map<RulesReferenceKey, RulesReferenceEntry> =
        withContext(Dispatchers.IO) { delegate().getEntries(keys) }

    private fun delegate(): RulesReferenceRepository =
        delegate ?: synchronized(this) {
            delegate ?: provider().also { delegate = it }
        }
}

internal class DeferredSpellRulesTextRepository(
    private val provider: () -> SpellRulesTextRepository,
) : SpellRulesTextRepository {
    @Volatile
    private var delegate: SpellRulesTextRepository? = null

    override suspend fun getSpellRulesText(
        spellId: String,
        spellRank: Int?,
    ): RulesTextDocument? = withContext(Dispatchers.IO) {
        delegate().getSpellRulesText(spellId, spellRank)
    }

    override suspend fun getSpellHeightenedRulesText(
        spellId: String,
        spellRank: Int?,
    ): List<RulesTextDocument> = withContext(Dispatchers.IO) {
        delegate().getSpellHeightenedRulesText(spellId, spellRank)
    }

    private fun delegate(): SpellRulesTextRepository =
        delegate ?: synchronized(this) {
            delegate ?: provider().also { delegate = it }
        }
}

internal class DeferredCharacterBuilderCatalogSource(
    private val provider: () -> CharacterBuilderCatalogSource,
) : CharacterBuilderCatalogSource {
    @Volatile
    private var delegate: CharacterBuilderCatalogSource? = null

    override suspend fun loadCatalog(): CharacterBuilderCatalogResult =
        withContext(Dispatchers.IO) { delegate().loadCatalog() }

    override suspend fun loadAvailableSourceTitles(): List<String> =
        withContext(Dispatchers.IO) { delegate().loadAvailableSourceTitles() }

    override suspend fun loadFeatRecords(): List<BuilderFeatRecord> =
        withContext(Dispatchers.IO) { delegate().loadFeatRecords() }

    private fun delegate(): CharacterBuilderCatalogSource =
        delegate ?: synchronized(this) {
            delegate ?: provider().also { delegate = it }
        }
}
