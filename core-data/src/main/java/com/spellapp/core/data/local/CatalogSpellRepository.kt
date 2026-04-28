package com.spellapp.core.data.local

import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CatalogSpellRepository(
    private val catalogDao: CatalogDao,
) : SpellRepository {
    suspend fun isAvailable(): Boolean {
        return runCatching {
            catalogDao.getMetadataValue(CATALOG_SCHEMA_VERSION_KEY) == SUPPORTED_CATALOG_SCHEMA_VERSION.toString() &&
                catalogDao.getSpellIndexCount() > 0
        }.getOrDefault(false)
    }

    override fun observeAvailableSources(): Flow<List<String>> {
        return catalogDao.observeAvailableSpellSources()
    }

    override fun observeAvailableTraits(): Flow<List<String>> {
        return catalogDao.observeSpellTraitRows().map(::normalizeTraitCatalog)
    }

    override fun observeSpells(
        query: String,
        rank: Int?,
        tradition: String?,
        rarity: String?,
        trait: String?,
    ): Flow<List<SpellListItem>> {
        return catalogDao.observeSpellList(
            query = query.trim(),
            rank = rank,
            tradition = tradition.orEmpty().trim(),
            rarity = rarity.orEmpty().trim(),
            trait = trait.orEmpty().trim(),
        )
    }

    override suspend fun getSpellDetail(spellId: String): SpellDetail? {
        val row = catalogDao.getSpellDetail(spellId) ?: return null
        val heightenedEntries = HeightenedDescriptionParser.parse(
            descriptionRaw = null,
            description = row.description,
        )
        return SpellDetail(
            id = row.id,
            name = row.name,
            rank = row.rank,
            tradition = row.traditionSummary,
            rarity = row.rarity,
            traits = row.traitsCsv.split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() },
            castTime = row.castTime,
            range = row.rangeText,
            area = row.areaText.orEmpty(),
            target = row.targetText,
            defense = row.defenseText.orEmpty(),
            duration = row.durationText,
            description = row.description,
            license = row.license,
            sourceBook = row.sourceBook,
            sourcePage = row.sourcePageText?.toIntOrNull(),
            heightenedEntries = heightenedEntries,
        )
    }

    override suspend fun seedFromDatasetIfEmpty(datasetJson: String) {
        // The catalog DB is prebuilt. Seeding remains only for the legacy fallback repository.
    }

    private companion object {
        private const val CATALOG_SCHEMA_VERSION_KEY = "catalog_schema_version"
        private const val SUPPORTED_CATALOG_SCHEMA_VERSION = 1
    }
}
