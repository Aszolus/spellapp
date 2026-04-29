package com.spellapp.core.data.local

import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll

class CatalogFirstSpellRepository(
    private val catalogRepository: CatalogSpellRepository,
    private val fallbackRepository: SpellRepository,
) : SpellRepository {
    suspend fun isCatalogAvailable(): Boolean = catalogRepository.isAvailable()

    override fun observeAvailableSources(): Flow<List<String>> {
        return catalogRepository.observeAvailableSources()
            .catch { emitAll(fallbackRepository.observeAvailableSources()) }
    }

    override fun observeAvailableTraits(): Flow<List<String>> {
        return catalogRepository.observeAvailableTraits()
            .catch { emitAll(fallbackRepository.observeAvailableTraits()) }
    }

    override fun observeSpells(
        query: String,
        rank: Int?,
        tradition: String?,
        rarity: String?,
        trait: String?,
    ): Flow<List<SpellListItem>> {
        return catalogRepository.observeSpells(query, rank, tradition, rarity, trait)
            .catch { emitAll(fallbackRepository.observeSpells(query, rank, tradition, rarity, trait)) }
    }

    override suspend fun getSpellDetail(spellId: String): SpellDetail? {
        return runCatching { catalogRepository.getSpellDetail(spellId) }.getOrNull()
            ?: fallbackRepository.getSpellDetail(spellId)
    }

    override suspend fun getSpellDetails(spellIds: Collection<String>): Map<String, SpellDetail> {
        val distinctIds = spellIds.map(String::trim).filter(String::isNotBlank).distinct()
        if (distinctIds.isEmpty()) return emptyMap()
        val catalogDetails = runCatching { catalogRepository.getSpellDetails(distinctIds) }
            .getOrDefault(emptyMap())
        val missingIds = distinctIds.filterNot { id -> id in catalogDetails }
        if (missingIds.isEmpty()) return catalogDetails
        return catalogDetails + fallbackRepository.getSpellDetails(missingIds)
    }

    override suspend fun getSpellRanks(spellIds: Collection<String>): Map<String, Int> {
        val distinctIds = spellIds.map(String::trim).filter(String::isNotBlank).distinct()
        if (distinctIds.isEmpty()) return emptyMap()
        val catalogRanks = runCatching { catalogRepository.getSpellRanks(distinctIds) }
            .getOrDefault(emptyMap())
        val missingIds = distinctIds.filterNot { id -> id in catalogRanks }
        if (missingIds.isEmpty()) return catalogRanks
        return catalogRanks + fallbackRepository.getSpellRanks(missingIds)
    }

    override suspend fun seedFromDatasetIfEmpty(datasetJson: String) {
        if (!isCatalogAvailable()) {
            fallbackRepository.seedFromDatasetIfEmpty(datasetJson)
        }
    }
}
