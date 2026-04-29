package com.spellapp.core.data.local

import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.PerfTrace
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CatalogSpellRepository(
    private val catalogDao: CatalogDao,
) : SpellRepository {
    suspend fun isAvailable(): Boolean {
        return PerfTrace.suspendSection("CatalogSpellRepository.isAvailable") {
            runCatching {
                catalogDao.getMetadataValue(CATALOG_SCHEMA_VERSION_KEY) == SUPPORTED_CATALOG_SCHEMA_VERSION.toString() &&
                    catalogDao.getSpellIndexCount() > 0
            }.getOrDefault(false)
        }
    }

    override fun observeAvailableSources(): Flow<List<String>> {
        return PerfTrace.firstEmission(
            name = "CatalogSpellRepository.sources",
            source = catalogDao.observeAvailableSpellSources(),
            sizeOf = List<String>::size,
        )
    }

    override fun observeAvailableTraits(): Flow<List<String>> {
        return PerfTrace.firstEmission(
            name = "CatalogSpellRepository.traitRows",
            source = catalogDao.observeSpellTraitRows(),
            sizeOf = List<String>::size,
        ).map { rows ->
            PerfTrace.section("CatalogSpellRepository.normalizeTraits") {
                normalizeTraitCatalog(rows)
            }
        }
    }

    override fun observeSpells(
        query: String,
        rank: Int?,
        tradition: String?,
        rarity: String?,
        trait: String?,
    ): Flow<List<SpellListItem>> {
        return PerfTrace.firstEmission(
            name = "CatalogSpellRepository.spellList query='${query.trim()}' rank=$rank tradition=${tradition.orEmpty().trim()} trait=${trait.orEmpty().trim()}",
            source = catalogDao.observeSpellList(
                query = query.trim(),
                rank = rank,
                tradition = tradition.orEmpty().trim(),
                rarity = rarity.orEmpty().trim(),
                trait = trait.orEmpty().trim(),
            ),
            sizeOf = List<SpellListItem>::size,
        )
    }

    override suspend fun getSpellDetail(spellId: String): SpellDetail? {
        return PerfTrace.suspendSection("CatalogSpellRepository.getSpellDetail $spellId") {
            val row = catalogDao.getSpellDetail(spellId) ?: return@suspendSection null
            row.toSpellDetail()
        }
    }

    override suspend fun getSpellDetails(spellIds: Collection<String>): Map<String, SpellDetail> {
        val ids = spellIds.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        return PerfTrace.suspendSection("CatalogSpellRepository.getSpellDetails count=${ids.size}") {
            buildMap {
                ids.chunked(SQLITE_BIND_LIMIT).forEach { chunk ->
                    catalogDao.getSpellDetails(chunk).forEach { row ->
                        put(row.id, row.toSpellDetail())
                    }
                }
            }
        }
    }

    override suspend fun getSpellRanks(spellIds: Collection<String>): Map<String, Int> {
        val ids = spellIds.asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .toList()
        return PerfTrace.suspendSection("CatalogSpellRepository.getSpellRanks count=${ids.size}") {
            buildMap {
                ids.chunked(SQLITE_BIND_LIMIT).forEach { chunk ->
                    catalogDao.getSpellRanks(chunk).forEach { row ->
                        put(row.id, row.rank)
                    }
                }
            }
        }
    }

    override suspend fun seedFromDatasetIfEmpty(datasetJson: String) {
        // The catalog DB is prebuilt. Seeding remains only for the legacy fallback repository.
    }

    private fun CatalogSpellDetailRow.toSpellDetail(): SpellDetail {
        val heightenedEntries = HeightenedDescriptionParser.parse(
            descriptionRaw = null,
            description = description,
        )
        return SpellDetail(
            id = id,
            name = name,
            rank = rank,
            tradition = traditionSummary,
            rarity = rarity,
            traits = traitsCsv.split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() },
            castTime = castTime,
            range = rangeText,
            area = areaText.orEmpty(),
            target = targetText,
            defense = defenseText.orEmpty(),
            duration = durationText,
            description = description,
            license = license,
            sourceBook = sourceBook,
            sourcePage = sourcePageText?.toIntOrNull(),
            heightenedEntries = heightenedEntries,
        )
    }

    private companion object {
        private const val CATALOG_SCHEMA_VERSION_KEY = "catalog_schema_version"
        private const val SUPPORTED_CATALOG_SCHEMA_VERSION = 1
        private const val SQLITE_BIND_LIMIT = 900
    }
}
