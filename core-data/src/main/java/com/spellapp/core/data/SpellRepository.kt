package com.spellapp.core.data

import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow

interface SpellRepository {
    fun observeAvailableSources(): Flow<List<String>>
    fun observeSpells(
        query: String = "",
        rank: Int? = null,
        tradition: String? = null,
        rarity: String? = null,
        trait: String? = null,
    ): Flow<List<SpellListItem>>
    suspend fun getSpellDetail(spellId: String): SpellDetail?
    suspend fun seedFromDatasetIfEmpty(datasetJson: String)
}
