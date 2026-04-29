package com.spellapp.core.data

import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow

interface SpellRepository {
    fun observeAvailableSources(): Flow<List<String>>
    fun observeAvailableTraits(): Flow<List<String>>
    fun observeSpells(
        query: String = "",
        rank: Int? = null,
        tradition: String? = null,
        rarity: String? = null,
        trait: String? = null,
    ): Flow<List<SpellListItem>>
    suspend fun getSpellDetail(spellId: String): SpellDetail?
    suspend fun getSpellDetails(spellIds: Collection<String>): Map<String, SpellDetail> {
        return spellIds.distinct()
            .mapNotNull { spellId -> getSpellDetail(spellId)?.let { detail -> spellId to detail } }
            .toMap()
    }
    suspend fun getSpellRanks(spellIds: Collection<String>): Map<String, Int> {
        return getSpellDetails(spellIds).mapValues { (_, detail) -> detail.rank }
    }
    suspend fun seedFromDatasetIfEmpty(datasetJson: String)
}
