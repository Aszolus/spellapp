package com.spellapp.feature.character

import com.spellapp.core.data.local.CatalogBuilderAssetEntity
import com.spellapp.core.data.local.CatalogDao
import com.spellapp.core.data.local.CatalogRecordDetailRow
import com.spellapp.core.data.local.CatalogRecordLinkRow
import com.spellapp.core.data.local.CatalogRecordSummaryRow
import com.spellapp.core.data.local.CatalogRecordTextRow
import com.spellapp.core.data.local.CatalogSpellDetailRow
import com.spellapp.core.data.local.SpellRankRow
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
class CatalogCharacterBuilderCatalogSourceTest {
    @Test
    fun loadCatalog_readsBuilderAssetsAndHydratesDescriptions() = runBlocking {
        val dao = RecordingCatalogDao(
            assets = sampleBuilderAssets(),
            descriptions = sampleDescriptions(),
        )
        val source = CatalogCharacterBuilderCatalogSource(dao)

        val result = source.loadCatalog()

        assertNull(result.loadError)
        val catalog = requireNotNull(result.catalog)
        assertEquals("Fighter", catalog.classes.single().name)
        assertEquals("Fighter details", catalog.classes.single().description)
        assertEquals(1, catalog.classes.single().featSlots.size)
        assertEquals("Goblin details", catalog.ancestries.single().description)
        assertEquals("Irongut Goblin details", catalog.heritages.single().description)
        assertEquals("Acolyte details", catalog.backgrounds.single().description)
        assertEquals("feats.general", catalog.featShards.single().name)
        assertEquals("Toughness", catalog.featIndex.single().name)
        assertTrue(dao.requestedAssetNames.containsAll(REQUIRED_ASSET_NAMES_FOR_TEST))
        assertTrue(dao.requestedRecordTextIds.contains("classes:fighter"))
        assertFalse(dao.requestedRecordTextIds.contains("classfeatures:attack-of-opportunity"))
    }

    @Test
    fun loadAvailableSourceTitles_doesNotLoadBuilderAssetsOrDescriptions() = runBlocking {
        val dao = RecordingCatalogDao(
            assets = sampleBuilderAssets(),
            descriptions = sampleDescriptions(),
            sourceTitles = listOf("Pathfinder Player Core", "Pathfinder Player Core 2"),
        )
        val source = CatalogCharacterBuilderCatalogSource(dao)

        val titles = source.loadAvailableSourceTitles()

        assertEquals(listOf("Pathfinder Player Core", "Pathfinder Player Core 2"), titles)
        assertTrue(dao.requestedAssetNames.isEmpty())
        assertTrue(dao.requestedRecordTextIds.isEmpty())
    }

    @Test
    fun loadFeatRecords_readsFeatShardAndHydratesDescriptions() = runBlocking {
        val dao = RecordingCatalogDao(
            assets = sampleBuilderAssets(),
            descriptions = sampleDescriptions(),
        )
        val source = CatalogCharacterBuilderCatalogSource(dao)

        val feats = source.loadFeatRecords()

        assertEquals("Toughness", feats.single().name)
        assertEquals("Toughness details", feats.single().description)
        assertEquals("feats.general", feats.single().shard)
        assertTrue(dao.requestedAssetNames.contains("feats.general"))
    }

    @Test
    fun fallbackSource_usesFallbackWhenPrimaryCatalogIsUnavailable() = runBlocking {
        val fallbackCatalog = CharacterBuilderCatalog(
            classes = emptyList(),
            ancestries = emptyList(),
            heritages = emptyList(),
            backgrounds = emptyList(),
            featIndex = emptyList(),
            feats = emptyList(),
            featShards = emptyList(),
            classFeatures = emptyList(),
            ancestryFeatures = emptyList(),
        )
        val source = FallbackCharacterBuilderCatalogSource(
            primary = EmptyCharacterBuilderCatalogSource,
            fallback = StaticCharacterBuilderCatalogSource(fallbackCatalog),
        )

        val result = source.loadCatalog()

        assertEquals(fallbackCatalog, result.catalog)
        assertFalse(source.loadAvailableSourceTitles().isNotEmpty())
    }

    @Test
    fun fallbackSource_toleratesMissingFallbackAssetsForOptionalLoads() = runBlocking {
        val source = FallbackCharacterBuilderCatalogSource(
            primary = EmptyCharacterBuilderCatalogSource,
            fallback = ThrowingCharacterBuilderCatalogSource,
        )

        assertTrue(source.loadAvailableSourceTitles().isEmpty())
        assertTrue(source.loadFeatRecords().isEmpty())
    }
}

private class StaticCharacterBuilderCatalogSource(
    private val catalog: CharacterBuilderCatalog,
) : CharacterBuilderCatalogSource {
    override suspend fun loadCatalog(): CharacterBuilderCatalogResult {
        return CharacterBuilderCatalogResult(catalog = catalog)
    }
}

private object ThrowingCharacterBuilderCatalogSource : CharacterBuilderCatalogSource {
    override suspend fun loadCatalog(): CharacterBuilderCatalogResult {
        error("Fallback catalog assets are not packaged.")
    }

    override suspend fun loadAvailableSourceTitles(): List<String> {
        error("Fallback source assets are not packaged.")
    }

    override suspend fun loadFeatRecords(): List<BuilderFeatRecord> {
        error("Fallback feat assets are not packaged.")
    }
}

private class RecordingCatalogDao(
    private val assets: Map<String, CatalogBuilderAssetEntity>,
    private val descriptions: Map<String, String>,
    private val sourceTitles: List<String> = emptyList(),
) : CatalogDao {
    val requestedAssetNames = mutableListOf<String>()
    val requestedRecordTextIds = mutableListOf<String>()

    override suspend fun getMetadataValue(key: String): String? = "1"

    override suspend fun getBuilderAssetCount(): Int = assets.size

    override suspend fun getAvailableBuilderSourceTitles(): List<String> = sourceTitles

    override suspend fun getCatalogBuilderAssets(names: List<String>): List<CatalogBuilderAssetEntity> {
        requestedAssetNames += names
        return names.mapNotNull(assets::get)
    }

    override suspend fun getCatalogRecordTexts(recordIds: List<String>): List<CatalogRecordTextRow> {
        requestedRecordTextIds += recordIds
        return recordIds.mapNotNull { id ->
            descriptions[id]?.let { text -> CatalogRecordTextRow(id = id, detailText = text) }
        }
    }

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

    override suspend fun getSpellDetails(spellIds: List<String>): List<CatalogSpellDetailRow> = emptyList()

    override suspend fun getSpellRanks(spellIds: List<String>): List<SpellRankRow> = emptyList()
}

private fun sampleDescriptions(): Map<String, String> {
    return mapOf(
        "classes:fighter" to "Fighter details",
        "ancestries:goblin" to "Goblin details",
        "heritages:irongut-goblin" to "Irongut Goblin details",
        "backgrounds:acolyte" to "Acolyte details",
        "classfeatures:attack-of-opportunity" to "Feature details that should not load during initial catalog load",
        "feats-srd:toughness" to "Toughness details",
    )
}

private fun sampleBuilderAssets(): Map<String, CatalogBuilderAssetEntity> {
    return listOf(
        builderAsset(
            name = "classes",
            type = "class",
            recordCount = 1,
            payload = """
                {
                  "classes": [
                    {
                      "id": "fighter",
                      "catalogRecordId": "classes:fighter",
                      "name": "Fighter",
                      "hp": 10,
                      "keyAbilityOptions": ["str", "dex"],
                      "featSlots": [{"slotId": "fighter/class/1", "kind": "class", "level": 1}],
                      "source": {"title": "Pathfinder Player Core", "license": "ORC", "remaster": true},
                      "traits": {"rarity": "common", "value": []},
                      "warnings": [],
                      "trainedSkills": {"value": ["athletics"], "lore": [], "additional": 3},
                      "skillIncreaseLevels": [3],
                      "skillFeatLevels": [2],
                      "baseProficiencies": [{"category": "save", "target": "fortitude", "rank": 2, "source": "system.savingThrows.fortitude"}],
                      "featureRefs": [],
                      "choicePrompts": []
                    }
                  ]
                }
            """.trimIndent(),
        ),
        builderAsset(
            name = "ancestries",
            type = "ancestry",
            recordCount = 1,
            payload = """
                {
                  "ancestries": [
                    {
                      "id": "goblin",
                      "catalogRecordId": "ancestries:goblin",
                      "name": "Goblin",
                      "hp": 6,
                      "speed": 25,
                      "size": "sm",
                      "source": {"title": "Pathfinder Player Core", "license": "ORC", "remaster": true},
                      "traits": {"rarity": "common", "value": ["goblin", "humanoid"]},
                      "grants": [],
                      "choicePrompts": [],
                      "warnings": [],
                      "boosts": [{"id": "0", "abilities": ["dex"], "selected": "dex"}],
                      "flaws": []
                    }
                  ]
                }
            """.trimIndent(),
        ),
        builderAsset(
            name = "heritages",
            type = "heritage",
            recordCount = 1,
            payload = """
                {
                  "heritages": [
                    {
                      "id": "irongut-goblin",
                      "catalogRecordId": "heritages:irongut-goblin",
                      "name": "Irongut Goblin",
                      "ancestryId": "goblin",
                      "source": {"title": "Pathfinder Player Core", "license": "ORC", "remaster": true},
                      "traits": {"rarity": "common", "value": []},
                      "grants": [],
                      "choicePrompts": [],
                      "warnings": []
                    }
                  ]
                }
            """.trimIndent(),
        ),
        builderAsset(
            name = "backgrounds",
            type = "background",
            recordCount = 1,
            payload = """
                {
                  "backgrounds": [
                    {
                      "id": "acolyte",
                      "catalogRecordId": "backgrounds:acolyte",
                      "name": "Acolyte",
                      "source": {"title": "Pathfinder Player Core", "license": "ORC", "remaster": true},
                      "traits": {"rarity": "common", "value": []},
                      "grants": [],
                      "choicePrompts": [],
                      "warnings": [],
                      "boosts": [{"id": "0", "abilities": ["int", "wis"], "selected": null}],
                      "trainedSkills": {"value": ["religion"], "lore": ["Scribing Lore"], "additional": null}
                    }
                  ]
                }
            """.trimIndent(),
        ),
        builderAsset(
            name = "class-features",
            type = "class-feature",
            recordCount = 1,
            payload = """
                {
                  "features": [
                    {
                      "id": "attack-of-opportunity",
                      "catalogRecordId": "classfeatures:attack-of-opportunity",
                      "name": "Attack of Opportunity",
                      "level": 1,
                      "category": "classfeature",
                      "source": {"title": "Pathfinder Player Core", "license": "ORC", "remaster": true},
                      "traits": {"rarity": "common", "value": ["fighter"]},
                      "grants": [],
                      "choicePrompts": [],
                      "warnings": [],
                      "proficiencyGrants": []
                    }
                  ]
                }
            """.trimIndent(),
        ),
        builderAsset(name = "ancestry-features", type = "ancestry-feature", recordCount = 0, payload = """{"features": []}"""),
        builderAsset(
            name = "feats.index",
            type = "feat-index",
            recordCount = 1,
            payload = """
                {
                  "shards": [{"category": "general", "name": "feats.general", "count": 1}],
                  "feats": [
                    {
                      "id": "toughness",
                      "name": "Toughness",
                      "category": "general",
                      "level": 1,
                      "rarity": "common",
                      "traits": ["general"],
                      "shard": "feats.general",
                      "source": {"title": "Pathfinder Player Core", "license": "ORC", "remaster": true}
                    }
                  ]
                }
            """.trimIndent(),
        ),
        builderAsset(
            name = "feats.general",
            type = "feat",
            category = "general",
            recordCount = 1,
            payload = """
                {
                  "category": "general",
                  "feats": [
                    {
                      "id": "toughness",
                      "catalogRecordId": "feats-srd:toughness",
                      "name": "Toughness",
                      "category": "general",
                      "level": 1,
                      "traits": {"rarity": "common", "value": ["general"]},
                      "source": {"title": "Pathfinder Player Core", "license": "ORC", "remaster": true},
                      "prerequisites": [],
                      "grants": [],
                      "choicePrompts": [],
                      "warnings": [],
                      "actionType": null,
                      "actions": null,
                      "proficiencyGrants": []
                    }
                  ]
                }
            """.trimIndent(),
        ),
    ).associateBy { asset -> asset.name }
}

private fun builderAsset(
    name: String,
    type: String,
    recordCount: Int,
    payload: String,
    category: String? = null,
): CatalogBuilderAssetEntity {
    return CatalogBuilderAssetEntity(
        name = name,
        builderType = type,
        category = category,
        recordCount = recordCount,
        payloadJsonGzip = gzip(payload),
    )
}

private fun gzip(text: String): ByteArray {
    val output = ByteArrayOutputStream()
    GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { writer ->
        writer.write(text)
    }
    return output.toByteArray()
}

private val REQUIRED_ASSET_NAMES_FOR_TEST = listOf(
    "classes",
    "ancestries",
    "heritages",
    "backgrounds",
    "class-features",
    "ancestry-features",
    "feats.index",
)
