package com.spellapp.core.data.local

import com.spellapp.core.data.CatalogAutomationStatus
import com.spellapp.core.data.CatalogRecordQuery
import com.spellapp.core.data.CatalogRecordTypes
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomCatalogRecordRepositoryTest {
    @Test
    fun observeRecords_trimsFiltersCapsLimitAndMapsAutomationStatus() = runBlocking {
        val dao = RecordingCatalogRecordDao(
            summaryRows = listOf(
                CatalogRecordSummaryRow(
                    id = "feats-srd:toughness",
                    uuid = "Compendium.pf2e.feats-srd.Item.Toughness",
                    packName = "feats-srd",
                    recordType = "feat",
                    category = "general",
                    name = "Toughness",
                    level = 1,
                    rarity = "common",
                    sourceTitle = "Pathfinder Player Core",
                    imagePath = "icons/toughness.svg",
                    imageMissing = false,
                    automationStatus = "partially_automated",
                ),
            ),
        )
        val repository = RoomCatalogRecordRepository(dao)

        val records = repository.observeRecords(
            CatalogRecordQuery(
                recordType = " feat ",
                category = " general ",
                text = " tough ",
                sourceTitle = " Pathfinder Player Core ",
                rarity = " COMMON ",
                maxLevel = 1,
                limit = 5_000,
            ),
        ).first()

        assertEquals(CatalogRecordTypes.FEAT, dao.lastRecordType)
        assertEquals("general", dao.lastCategory)
        assertEquals("tough", dao.lastQuery)
        assertEquals("Pathfinder Player Core", dao.lastSourceTitle)
        assertEquals("COMMON", dao.lastRarity)
        assertEquals(1, dao.lastMaxLevel)
        assertEquals(500, dao.lastLimit)
        assertEquals("Toughness", records.single().name)
        assertEquals(CatalogAutomationStatus.PARTIALLY_AUTOMATED, records.single().automationStatus)
    }

    @Test
    fun getRecordDetail_mapsFullRecordDetail() = runBlocking {
        val dao = RecordingCatalogRecordDao(
            detailRow = CatalogRecordDetailRow(
                id = "backgrounds:acolyte",
                uuid = "Compendium.pf2e.backgrounds.Item.Acolyte",
                packName = "backgrounds",
                packLabel = "Backgrounds",
                recordType = "background",
                category = "background",
                name = "Acolyte",
                level = null,
                rarity = "common",
                sourceTitle = "Pathfinder Player Core",
                sourceLicense = "ORC",
                sourcePage = "84",
                imagePath = null,
                imageMissing = false,
                automationStatus = "reference_only",
                detailText = "You spent your early days in a religious monastery.",
                normalizedJson = """{"id":"backgrounds:acolyte"}""",
                rawJsonGzip = byteArrayOf(1, 2, 3),
            ),
        )
        val repository = RoomCatalogRecordRepository(dao)

        val detail = repository.getRecordDetail(" Compendium.pf2e.backgrounds.Item.Acolyte ")

        requireNotNull(detail)
        assertEquals("Compendium.pf2e.backgrounds.Item.Acolyte", dao.lastDetailId)
        assertEquals("Acolyte", detail.name)
        assertEquals(CatalogAutomationStatus.REFERENCE_ONLY, detail.automationStatus)
        assertEquals("84", detail.sourcePage)
        assertEquals("""{"id":"backgrounds:acolyte"}""", detail.normalizedJson)
    }

    @Test
    fun getRecordLinksAndBacklinks_mapRelatedRecords() = runBlocking {
        val dao = RecordingCatalogRecordDao(
            linkRows = listOf(
                CatalogRecordLinkRow(
                    fromRecordId = "feats-srd:archer",
                    toUuid = "Compendium.pf2e.feat-effects.Item.EffectArcher",
                    toRecordId = "feat-effects:effect-archer",
                    linkType = "uuid",
                    sourcePath = "system.rules.0.uuid",
                    label = "Effect: Archer",
                    resolved = true,
                    relatedName = "Effect: Archer",
                    relatedRecordType = "effect",
                ),
            ),
            backlinkRows = listOf(
                CatalogRecordLinkRow(
                    fromRecordId = "feats-srd:archer",
                    toUuid = "Compendium.pf2e.feat-effects.Item.EffectArcher",
                    toRecordId = "feat-effects:effect-archer",
                    linkType = "uuid",
                    sourcePath = "system.rules.0.uuid",
                    label = "Effect: Archer",
                    resolved = true,
                    relatedName = "Archer",
                    relatedRecordType = "feat",
                ),
            ),
        )
        val repository = RoomCatalogRecordRepository(dao)

        val links = repository.getRecordLinks(" feats-srd:archer ")
        val backlinks = repository.getRecordBacklinks(" feat-effects:effect-archer ")

        assertEquals("feats-srd:archer", dao.lastLinksId)
        assertEquals("feat-effects:effect-archer", dao.lastBacklinksId)
        assertTrue(links.single().resolved)
        assertEquals("Effect: Archer", links.single().relatedName)
        assertEquals("effect", links.single().relatedRecordType)
        assertFalse(backlinks.isEmpty())
        assertEquals("Archer", backlinks.single().relatedName)
    }
}

private class RecordingCatalogRecordDao(
    private val summaryRows: List<CatalogRecordSummaryRow> = emptyList(),
    private val detailRow: CatalogRecordDetailRow? = null,
    private val linkRows: List<CatalogRecordLinkRow> = emptyList(),
    private val backlinkRows: List<CatalogRecordLinkRow> = emptyList(),
) : CatalogDao {
    var lastRecordType: String? = null
        private set
    var lastCategory: String? = null
        private set
    var lastQuery: String? = null
        private set
    var lastSourceTitle: String? = null
        private set
    var lastRarity: String? = null
        private set
    var lastMaxLevel: Int? = null
        private set
    var lastLimit: Int? = null
        private set
    var lastDetailId: String? = null
        private set
    var lastLinksId: String? = null
        private set
    var lastBacklinksId: String? = null
        private set

    override suspend fun getMetadataValue(key: String): String? = null

    override fun observeCatalogRecordSummaries(
        recordType: String,
        category: String,
        query: String,
        sourceTitle: String,
        rarity: String,
        maxLevel: Int?,
        limit: Int,
    ): Flow<List<CatalogRecordSummaryRow>> {
        lastRecordType = recordType
        lastCategory = category
        lastQuery = query
        lastSourceTitle = sourceTitle
        lastRarity = rarity
        lastMaxLevel = maxLevel
        lastLimit = limit
        return flowOf(summaryRows)
    }

    override suspend fun getCatalogRecordDetail(recordIdOrUuid: String): CatalogRecordDetailRow? {
        lastDetailId = recordIdOrUuid
        return detailRow
    }

    override suspend fun getCatalogLinksFromRecord(recordIdOrUuid: String): List<CatalogRecordLinkRow> {
        lastLinksId = recordIdOrUuid
        return linkRows
    }

    override suspend fun getCatalogBacklinksToRecord(recordIdOrUuid: String): List<CatalogRecordLinkRow> {
        lastBacklinksId = recordIdOrUuid
        return backlinkRows
    }

    override suspend fun getSpellIndexCount(): Int = 0

    override fun observeAvailableSpellSources(): Flow<List<String>> = emptyFlow()

    override fun observeSpellTraitRows(): Flow<List<String>> = emptyFlow()

    override fun observeSpellList(
        query: String,
        rank: Int?,
        tradition: String,
        rarity: String,
        trait: String,
    ): Flow<List<SpellListItem>> = emptyFlow()

    override suspend fun getSpellDetail(spellId: String): CatalogSpellDetailRow? = null
}
