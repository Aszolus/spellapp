package com.spellapp.core.data.local

import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSpellRepositoryTest {
    @Test
    fun isAvailable_requiresSupportedSchemaAndSpellRows() = runBlocking {
        val repository = CatalogSpellRepository(
            RecordingCatalogDao(
                metadata = mapOf("catalog_schema_version" to "1"),
                spellIndexCount = 1,
            ),
        )

        assertTrue(repository.isAvailable())
    }

    @Test
    fun isAvailable_rejectsUnsupportedSchema() = runBlocking {
        val repository = CatalogSpellRepository(
            RecordingCatalogDao(
                metadata = mapOf("catalog_schema_version" to "2"),
                spellIndexCount = 1,
            ),
        )

        assertFalse(repository.isAvailable())
    }

    @Test
    fun getSpellDetail_mapsCatalogRowToSpellDetail() = runBlocking {
        val repository = CatalogSpellRepository(
            RecordingCatalogDao(
                detailRow = CatalogSpellDetailRow(
                    id = "force-barrage",
                    name = "Force Barrage",
                    rank = 1,
                    traditionSummary = "arcane, occult",
                    rarity = "common",
                    traitsCsv = "concentrate,force,manipulate",
                    castTime = "1 to 3",
                    rangeText = "120 feet",
                    targetText = "1 creature",
                    durationText = "",
                    areaText = null,
                    defenseText = null,
                    description = "You fire force.\n\nHeightened (+2) One more shard.",
                    license = "ORC",
                    sourceBook = "Pathfinder Player Core",
                    sourcePageText = "123",
                ),
            ),
        )

        val detail = repository.getSpellDetail("force-barrage")

        requireNotNull(detail)
        assertEquals("Force Barrage", detail.name)
        assertEquals(1, detail.rank)
        assertEquals(listOf("concentrate", "force", "manipulate"), detail.traits)
        assertEquals(123, detail.sourcePage)
        assertEquals(1, detail.heightenedEntries.size)
    }

    @Test
    fun catalogFirstRepository_fallsBackWhenCatalogDetailMissing() = runBlocking {
        val fallback = RecordingSpellRepository(
            detail = CatalogSpellDetailRow(
                id = "legacy-spell",
                name = "Legacy Spell",
                rank = 2,
                traditionSummary = "occult",
                rarity = "common",
                traitsCsv = "",
                castTime = "",
                rangeText = "",
                targetText = "",
                durationText = "",
                areaText = null,
                defenseText = null,
                description = "Legacy text",
                license = "ORC",
                sourceBook = "Legacy Source",
                sourcePageText = null,
            ).toSpellDetail(),
        )
        val repository = CatalogFirstSpellRepository(
            catalogRepository = CatalogSpellRepository(RecordingCatalogDao(detailRow = null)),
            fallbackRepository = fallback,
        )

        val detail = repository.getSpellDetail("legacy-spell")

        assertEquals("Legacy Spell", detail?.name)
    }
}

private class RecordingCatalogDao(
    private val metadata: Map<String, String> = emptyMap(),
    private val spellIndexCount: Int = 0,
    private val detailRow: CatalogSpellDetailRow? = null,
    private val spellList: List<SpellListItem> = emptyList(),
) : CatalogDao {
    override suspend fun getMetadataValue(key: String): String? = metadata[key]

    override fun observeCatalogRecordSummaries(
        recordType: String,
        category: String,
        query: String,
        sourceTitle: String,
        rarity: String,
        maxLevel: Int?,
        limit: Int,
    ): Flow<List<CatalogRecordSummaryRow>> = emptyFlow()

    override suspend fun getCatalogRecordDetail(recordIdOrUuid: String): CatalogRecordDetailRow? = null

    override suspend fun getCatalogLinksFromRecord(recordIdOrUuid: String): List<CatalogRecordLinkRow> = emptyList()

    override suspend fun getCatalogBacklinksToRecord(recordIdOrUuid: String): List<CatalogRecordLinkRow> = emptyList()

    override suspend fun getSpellIndexCount(): Int = spellIndexCount

    override fun observeAvailableSpellSources(): Flow<List<String>> = emptyFlow()

    override fun observeSpellTraitRows(): Flow<List<String>> = emptyFlow()

    override fun observeSpellList(
        query: String,
        rank: Int?,
        tradition: String,
        rarity: String,
        trait: String,
    ): Flow<List<SpellListItem>> = flowOf(spellList)

    override suspend fun getSpellDetail(spellId: String): CatalogSpellDetailRow? = detailRow
}

private class RecordingSpellRepository(
    private val detail: com.spellapp.core.model.SpellDetail? = null,
) : com.spellapp.core.data.SpellRepository {
    override fun observeAvailableSources(): Flow<List<String>> = emptyFlow()

    override fun observeAvailableTraits(): Flow<List<String>> = emptyFlow()

    override fun observeSpells(
        query: String,
        rank: Int?,
        tradition: String?,
        rarity: String?,
        trait: String?,
    ): Flow<List<SpellListItem>> = emptyFlow()

    override suspend fun getSpellDetail(spellId: String): com.spellapp.core.model.SpellDetail? = detail

    override suspend fun seedFromDatasetIfEmpty(datasetJson: String) = Unit
}

private fun CatalogSpellDetailRow.toSpellDetail(): com.spellapp.core.model.SpellDetail {
    return com.spellapp.core.model.SpellDetail(
        id = id,
        name = name,
        rank = rank,
        tradition = traditionSummary,
        rarity = rarity,
        traits = emptyList(),
        castTime = castTime,
        range = rangeText,
        target = targetText,
        duration = durationText,
        description = description,
        license = license,
        sourceBook = sourceBook,
        sourcePage = sourcePageText?.toIntOrNull(),
    )
}
