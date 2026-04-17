package com.spellapp.core.data.local

import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSpellRepository(
    private val spellDao: SpellDao,
) : SpellRepository {
    override fun observeAvailableSources(): Flow<List<String>> {
        return spellDao.observeAvailableSourceBooks()
    }

    override fun observeAvailableTraits(): Flow<List<String>> {
        return spellDao.observeTraitCatalogRows().map(::normalizeTraitCatalog)
    }

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
            area = entity.areaText.orEmpty(),
            target = entity.targetText,
            defense = entity.defenseText.orEmpty(),
            duration = entity.durationText,
            description = entity.description,
            license = entity.license,
            sourceBook = entity.sourceBook,
            sourcePage = entity.sourcePage,
            heightenedEntries = HeightenedEntryCodec.decode(entity.heightenedEntriesJson),
        )
    }

    override suspend fun seedFromDatasetIfEmpty(datasetJson: String) {
        val entities = SpellDatasetParser.parseEntities(datasetJson)
        if (entities.isEmpty()) {
            error("Spell dataset did not contain any valid spell entities.")
        }

        val existingCount = spellDao.getSpellCount()
        if (existingCount == 0) {
            spellDao.replaceAll(entities)
            return
        }

        val importedCantripCount = entities.count { entity -> entity.rank == 0 }
        val existingCantripCount = spellDao.getSpellCountByRank(0)

        // One-time repair path for datasets imported before cantrips were normalized to rank 0.
        if (importedCantripCount > 0 && existingCantripCount == 0) {
            spellDao.replaceAll(entities)
            return
        }

        // One-time repair path for datasets imported before area/defense were extracted.
        val populatedAreaCount = spellDao.getCountWithArea()
        val importedAreaCount = entities.count { it.areaText != null }
        if (populatedAreaCount == 0 && importedAreaCount > 0) {
            spellDao.replaceAll(entities)
            return
        }

        // One-time repair path for datasets imported before structured heighten entries existed.
        val populatedHeightenCount = spellDao.getCountWithHeightenedEntries()
        val importedHeightenCount = entities.count { entity ->
            entity.heightenedEntriesJson.isNotBlank() && entity.heightenedEntriesJson != "[]"
        }
        if (populatedHeightenCount == 0 && importedHeightenCount > 0) {
            spellDao.replaceAll(entities)
        }
    }
}

internal fun normalizeTraitCatalog(rows: List<String>): List<String> {
    return rows.asSequence()
        .flatMap { row ->
            row.splitToSequence(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
        .toList()
}
