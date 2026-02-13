package com.spellapp.core.data.local

import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow

class RoomSpellRepository(
    private val spellDao: SpellDao,
) : SpellRepository {
    override fun observeSpells(
        query: String,
        rank: Int?,
        tradition: String?,
        rarity: String?,
        trait: String?,
    ): Flow<List<SpellListItem>> {
        return spellDao.observeSpellList(
            query = query.trim(),
            rank = rank,
            tradition = tradition.orEmpty().trim(),
            rarity = rarity.orEmpty().trim(),
            trait = trait.orEmpty().trim(),
        )
    }

    override suspend fun getSpellDetail(spellId: String): SpellDetail? {
        val entity = spellDao.getSpellById(spellId) ?: return null
        return SpellDetail(
            id = entity.id,
            name = entity.name,
            rank = entity.rank,
            tradition = entity.traditionSummary,
            rarity = entity.rarity,
            traits = entity.traitsCsv.split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() },
            castTime = entity.castTime,
            range = entity.rangeText,
            target = entity.targetText,
            duration = entity.durationText,
            description = entity.description,
            license = entity.license,
            sourceBook = entity.sourceBook,
            sourcePage = entity.sourcePage,
        )
    }

    override suspend fun seedFromDatasetIfEmpty(datasetJson: String) {
        val entities = SpellDatasetParser.parseEntities(datasetJson)
        if (entities.isEmpty()) {
            error("Spell dataset did not contain any valid spell entities.")
        }
        spellDao.replaceAll(entities)
    }
}
