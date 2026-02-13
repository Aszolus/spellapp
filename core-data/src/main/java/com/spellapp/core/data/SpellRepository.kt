package com.spellapp.core.data

import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow

interface SpellRepository {
    fun observeSpells(query: String = ""): Flow<List<SpellListItem>>
    suspend fun getSpellDetail(spellId: String): SpellDetail?
    suspend fun seedFromDatasetIfEmpty(datasetJson: String)
}
