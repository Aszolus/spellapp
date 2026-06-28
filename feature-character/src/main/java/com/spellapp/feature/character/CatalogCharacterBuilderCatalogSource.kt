package com.spellapp.feature.character

import com.spellapp.core.data.local.CatalogBuilderAssetEntity
import com.spellapp.core.data.local.CatalogDao
import com.spellapp.core.data.local.CatalogRecordTextRow
import com.spellapp.core.data.PerfTrace
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.normalizeClassId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

class CatalogCharacterBuilderCatalogSource(
    private val catalogDao: CatalogDao,
) : CharacterBuilderCatalogSource {
    @Volatile
    private var cachedResult: CharacterBuilderCatalogResult? = null

    @Volatile
    private var cachedFeatRecords: List<BuilderFeatRecord>? = null

    @Volatile
    private var cachedSourceTitles: List<String>? = null

    override suspend fun loadCatalog(): CharacterBuilderCatalogResult {
        cachedResult?.let { return it }
        return PerfTrace.suspendSection("CatalogCharacterBuilderCatalogSource.loadCatalog") {
            withContext(Dispatchers.IO) {
                val loaded = loadCatalogResult()
                synchronized(this@CatalogCharacterBuilderCatalogSource) {
                    cachedResult ?: loaded.also { cachedResult = it }
                }
            }
        }
    }

    override suspend fun loadAvailableSourceTitles(): List<String> {
        cachedSourceTitles?.let { return it }
        return PerfTrace.suspendSection("CatalogCharacterBuilderCatalogSource.sourceTitles") {
            withContext(Dispatchers.IO) {
                val loaded = catalogDao.getAvailableBuilderSourceTitles()
                synchronized(this@CatalogCharacterBuilderCatalogSource) {
                    cachedSourceTitles ?: loaded.also { cachedSourceTitles = it }
                }
            }
        }
    }

    override suspend fun loadFeatRecords(): List<BuilderFeatRecord> {
        cachedFeatRecords?.let { return it }
        return PerfTrace.suspendSection("CatalogCharacterBuilderCatalogSource.loadFeatRecords") {
            withContext(Dispatchers.IO) {
                val loaded = loadFeatRecordsResult()
                synchronized(this@CatalogCharacterBuilderCatalogSource) {
                    cachedFeatRecords ?: loaded.also { cachedFeatRecords = it }
                }
            }
        }
    }

    private suspend fun loadCatalogResult(): CharacterBuilderCatalogResult {
        return runCatching {
            verifyAvailable()
            val assets = loadAssets(REQUIRED_BUILDER_ASSETS)
            val descriptions = loadDescriptions(
                assets
                    .filterKeys { name -> name in DISPLAY_DESCRIPTION_ASSET_NAMES }
                    .values,
            )
            val featIndexRoot = assets.requireAsset("feats.index").root
            val featShards = CatalogBuilderJsonParser.parseFeatShards(
                raw = featIndexRoot.optJSONArray("shards"),
                availableAssetNames = emptySet(),
            )
            CharacterBuilderCatalogResult(
                catalog = CharacterBuilderCatalog(
                    classes = CatalogBuilderJsonParser.parseClasses(
                        assets.requireAsset("classes").root.optJSONArray("classes"),
                        descriptions,
                    ),
                    ancestries = CatalogBuilderJsonParser.parseAncestries(
                        assets.requireAsset("ancestries").root.optJSONArray("ancestries"),
                        descriptions,
                    ),
                    heritages = CatalogBuilderJsonParser.parseHeritages(
                        assets.requireAsset("heritages").root.optJSONArray("heritages"),
                        descriptions,
                    ),
                    backgrounds = CatalogBuilderJsonParser.parseBackgrounds(
                        assets.requireAsset("backgrounds").root.optJSONArray("backgrounds"),
                        descriptions,
                    ),
                    featIndex = CatalogBuilderJsonParser.parseFeatIndex(featIndexRoot.optJSONArray("feats")),
                    feats = emptyList(),
                    featShards = featShards,
                    classFeatures = CatalogBuilderJsonParser.parseFeatures(
                        assets.requireAsset("class-features").root.optJSONArray("features"),
                        emptyMap(),
                    ),
                    ancestryFeatures = CatalogBuilderJsonParser.parseFeatures(
                        assets.requireAsset("ancestry-features").root.optJSONArray("features"),
                        emptyMap(),
                    ),
                ),
            )
        }.getOrElse { error ->
            CharacterBuilderCatalogResult(
                loadError = error.message ?: "Catalog character builder data could not be loaded.",
            )
        }
    }

    private suspend fun loadFeatRecordsResult(): List<BuilderFeatRecord> {
        val catalog = loadCatalog().catalog ?: error("Catalog character builder data is not available.")
        val shardNames = catalog.featShards.map { shard -> shard.name }
        if (shardNames.isEmpty()) return emptyList()
        val assets = loadAssets(shardNames)
        val descriptions = loadDescriptions(assets.values)
        return catalog.featShards.flatMap { shard ->
            CatalogBuilderJsonParser.parseFeats(
                raw = assets.requireAsset(shard.name).root.optJSONArray("feats"),
                shardName = shard.name,
                descriptions = descriptions,
            )
        }
    }

    private suspend fun verifyAvailable() {
        val schemaVersion = catalogDao.getMetadataValue(CATALOG_SCHEMA_VERSION_KEY)
        if (schemaVersion != SUPPORTED_CATALOG_SCHEMA_VERSION) {
            error("Catalog schema $schemaVersion is not supported.")
        }
        if (catalogDao.getBuilderAssetCount() == 0) {
            error("Catalog builder assets are missing.")
        }
    }

    private suspend fun loadAssets(names: List<String>): Map<String, LoadedBuilderAsset> {
        return PerfTrace.suspendSection("CatalogCharacterBuilderCatalogSource.loadAssets count=${names.size}") {
            val rows = catalogDao.getCatalogBuilderAssets(names)
            val byName = rows.associateBy { row -> row.name }
            val missing = names.filterNot(byName::containsKey)
            if (missing.isNotEmpty()) {
                error("Catalog builder assets are missing: ${missing.joinToString()}")
            }
            byName.mapValues { (_, row) -> LoadedBuilderAsset(row.name, row.payloadJsonObject()) }
        }
    }

    private suspend fun loadDescriptions(assets: Collection<LoadedBuilderAsset>): Map<String, String> {
        return PerfTrace.suspendSection("CatalogCharacterBuilderCatalogSource.loadDescriptions") {
            val recordIds = assets
                .flatMap { asset -> asset.root.catalogRecordIds() }
                .distinct()
            if (recordIds.isEmpty()) return@suspendSection emptyMap()
            recordIds
                .chunked(SQLITE_BIND_CHUNK_SIZE)
                .flatMap { chunk -> catalogDao.getCatalogRecordTexts(chunk) }
                .associate { row -> row.id to row.detailText }
        }
    }

    private companion object {
        private const val CATALOG_SCHEMA_VERSION_KEY = "catalog_schema_version"
        private const val SUPPORTED_CATALOG_SCHEMA_VERSION = "1"
        private const val SQLITE_BIND_CHUNK_SIZE = 800
        private val REQUIRED_BUILDER_ASSETS = listOf(
            "classes",
            "ancestries",
            "heritages",
            "backgrounds",
            "class-features",
            "ancestry-features",
            "feats.index",
        )
        private val DISPLAY_DESCRIPTION_ASSET_NAMES = setOf(
            "classes",
            "ancestries",
            "heritages",
            "backgrounds",
        )
    }
}

private data class LoadedBuilderAsset(
    val name: String,
    val root: JSONObject,
)

class FallbackCharacterBuilderCatalogSource(
    private val primary: CharacterBuilderCatalogSource,
    private val fallback: CharacterBuilderCatalogSource,
) : CharacterBuilderCatalogSource {
    override suspend fun loadCatalog(): CharacterBuilderCatalogResult {
        val primaryResult = runCatching { primary.loadCatalog() }.getOrNull()
        if (primaryResult?.catalog != null) return primaryResult
        return fallback.loadCatalog()
    }

    override suspend fun loadAvailableSourceTitles(): List<String> {
        val primaryTitles = runCatching { primary.loadAvailableSourceTitles() }.getOrDefault(emptyList())
        return primaryTitles.takeIf { it.isNotEmpty() }
            ?: runCatching { fallback.loadAvailableSourceTitles() }.getOrDefault(emptyList())
    }

    override suspend fun loadFeatRecords(): List<BuilderFeatRecord> {
        val primaryFeats = runCatching { primary.loadFeatRecords() }.getOrDefault(emptyList())
        return primaryFeats.takeIf { it.isNotEmpty() }
            ?: runCatching { fallback.loadFeatRecords() }.getOrDefault(emptyList())
    }
}

private object CatalogBuilderJsonParser {
    fun parseClasses(
        raw: JSONArray?,
        descriptions: Map<String, String>,
    ): List<BuilderClassRecord> {
        return raw.objects().map { item ->
            BuilderClassRecord(
                id = normalizeClassId(item.optString("id")),
                name = item.optString("name").ifBlank { item.optString("id") },
                hp = item.optNullableInt("hp"),
                keyAbilityOptions = item.optJSONArray("keyAbilityOptions").strings().mapNotNull(::parseAbility),
                featSlots = item.optJSONArray("featSlots").objects().map { slot ->
                    BuilderFeatSlot(
                        slotId = slot.optString("slotId"),
                        kind = slot.optString("kind"),
                        level = slot.optInt("level"),
                    )
                },
                source = parseSource(item.optJSONObject("source")),
                traits = parseTraits(item.optJSONObject("traits")),
                description = item.descriptionFrom(descriptions),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
                trainedSkills = parseTrainedSkills(item.optJSONObject("trainedSkills")),
                skillIncreaseLevels = item.optJSONArray("skillIncreaseLevels").ints(),
                skillFeatLevels = item.optJSONArray("skillFeatLevels").ints(),
                baseProficiencies = parseProficiencyGrants(item.optJSONArray("baseProficiencies")),
                featureRefs = item.optJSONArray("featureRefs").strings(),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
            )
        }.sortedBy { record -> record.name }
    }

    fun parseAncestries(
        raw: JSONArray?,
        descriptions: Map<String, String>,
    ): List<BuilderAncestryRecord> {
        return raw.objects().map { item ->
            BuilderAncestryRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                hp = item.optNullableInt("hp"),
                speed = item.opt("speed")?.toString().orEmpty(),
                size = item.opt("size")?.toString().orEmpty(),
                source = parseSource(item.optJSONObject("source")),
                traits = parseTraits(item.optJSONObject("traits")),
                description = item.descriptionFrom(descriptions),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
                boosts = parseAbilityBoosts(item.optJSONArray("boosts")),
                flaws = parseAbilityBoosts(item.optJSONArray("flaws")),
            )
        }.sortedBy { record -> record.name }
    }

    fun parseHeritages(
        raw: JSONArray?,
        descriptions: Map<String, String>,
    ): List<BuilderHeritageRecord> {
        return raw.objects().map { item ->
            BuilderHeritageRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                ancestryId = item.optString("ancestryId"),
                source = parseSource(item.optJSONObject("source")),
                traits = parseTraits(item.optJSONObject("traits")),
                description = item.descriptionFrom(descriptions),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
            )
        }.sortedWith(compareBy<BuilderHeritageRecord> { record -> record.ancestryId }.thenBy { record -> record.name })
    }

    fun parseBackgrounds(
        raw: JSONArray?,
        descriptions: Map<String, String>,
    ): List<BuilderBackgroundRecord> {
        return raw.objects().map { item ->
            BuilderBackgroundRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                source = parseSource(item.optJSONObject("source")),
                traits = parseTraits(item.optJSONObject("traits")),
                description = item.descriptionFrom(descriptions),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
                boosts = parseAbilityBoosts(item.optJSONArray("boosts")),
                trainedSkills = parseTrainedSkills(item.optJSONObject("trainedSkills")),
            )
        }.sortedBy { record -> record.name }
    }

    fun parseFeatures(
        raw: JSONArray?,
        descriptions: Map<String, String>,
    ): List<BuilderFeatureRecord> {
        return raw.objects().map { item ->
            BuilderFeatureRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                level = item.optInt("level"),
                category = item.optString("category"),
                source = parseSource(item.optJSONObject("source")),
                traits = parseTraits(item.optJSONObject("traits")),
                description = item.descriptionFrom(descriptions),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
                proficiencyGrants = parseProficiencyGrants(item.optJSONArray("proficiencyGrants")),
            )
        }
    }

    fun parseFeatIndex(raw: JSONArray?): List<BuilderFeatIndexRecord> {
        return raw.objects().map { item ->
            BuilderFeatIndexRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                category = item.optString("category"),
                level = item.optInt("level"),
                rarity = item.optString("rarity").ifBlank { "common" },
                traits = item.optJSONArray("traits").strings(),
                shard = item.optString("shard"),
                source = parseSource(item.optJSONObject("source")),
            )
        }
    }

    fun parseFeats(
        raw: JSONArray?,
        shardName: String,
        descriptions: Map<String, String>,
    ): List<BuilderFeatRecord> {
        return raw.objects().map { item ->
            BuilderFeatRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                category = item.optString("category"),
                level = item.optInt("level"),
                rarity = item.optJSONObject("traits")?.optString("rarity")?.ifBlank { "common" } ?: "common",
                traits = item.optJSONObject("traits")?.optJSONArray("value").strings(),
                source = parseSource(item.optJSONObject("source")),
                description = item.descriptionFrom(descriptions),
                prerequisites = item.optJSONArray("prerequisites").strings(),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                actionType = item.optString("actionType").ifBlank { null },
                actions = item.opt("actions")?.toString()?.takeIf { value -> value.isNotBlank() && value != "null" },
                shard = shardName,
                uuid = item.optString("uuid").ifBlank { null },
                proficiencyGrants = parseProficiencyGrants(item.optJSONArray("proficiencyGrants")),
            )
        }
    }

    fun parseFeatShards(
        raw: JSONArray?,
        availableAssetNames: Set<String>,
    ): List<BuilderFeatShard> {
        return raw.objects().map { item ->
            BuilderFeatShard(
                name = item.optString("name"),
                category = item.optString("category"),
                count = item.optInt("count"),
            )
        }.filter { shard -> availableAssetNames.isEmpty() || shard.name in availableAssetNames }
    }

    private fun parseSource(raw: JSONObject?): BuilderSourceRecord {
        return BuilderSourceRecord(
            title = raw?.optString("title")?.ifBlank { null },
            license = raw?.optString("license")?.ifBlank { null },
            remaster = raw?.optBoolean("remaster", false) ?: false,
        )
    }

    private fun parseTraits(raw: JSONObject?): BuilderTraitsRecord {
        return BuilderTraitsRecord(
            rarity = raw?.optString("rarity")?.ifBlank { "common" } ?: "common",
            values = raw?.optJSONArray("value").strings(),
        )
    }

    private fun parseWarnings(raw: JSONArray?): List<BuilderWarningRecord> {
        return raw.objects().map { item ->
            BuilderWarningRecord(
                warningId = item.optString("warningId"),
                recordId = item.optString("recordId"),
                recordType = item.optString("recordType"),
                ruleType = item.optString("ruleType"),
                sourceRulePath = item.optString("sourceRulePath"),
                originalText = item.optString("originalText"),
                severity = item.optString("severity").ifBlank { "WARNING" },
                message = item.optString("message"),
            )
        }
    }

    private fun parseGrants(raw: JSONArray?): List<BuilderGrantRecord> {
        return raw.objects().map { item ->
            BuilderGrantRecord(
                grantId = item.optString("grantId"),
                name = item.optString("name").ifBlank { null },
                uuid = item.optString("uuid").ifBlank { null },
                level = item.optNullableInt("level"),
                source = item.optString("source"),
            )
        }
    }

    private fun parseChoicePrompts(raw: JSONArray?): List<BuilderChoicePromptRecord> {
        return raw.objects().map { item ->
            val choiceConfig = item.opt("choiceConfig")
            BuilderChoicePromptRecord(
                promptId = item.optString("promptId"),
                label = item.optString("label").ifBlank { "Choice" },
                sourceRulePath = item.optString("sourceRulePath"),
                required = item.optBoolean("required", true),
                choiceValues = parseChoiceValues(choiceConfig),
                choiceDomain = (choiceConfig as? String)?.takeIf { value -> value.isNotBlank() },
            )
        }
    }

    private fun parseChoiceValues(raw: Any?): List<BuilderChoiceValueRecord> {
        val array = raw as? JSONArray ?: return emptyList()
        return array.objects().mapNotNull { item ->
            val value = item.optString("value").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BuilderChoiceValueRecord(
                value = value,
                label = item.optString("label").ifBlank { value },
            )
        }
    }

    private fun parseAbilityBoosts(raw: JSONArray?): List<BuilderAbilityBoostRecord> {
        return raw.objects().map { item ->
            BuilderAbilityBoostRecord(
                id = item.optString("id"),
                abilities = item.optJSONArray("abilities").strings().mapNotNull(::parseAbility),
                selected = parseAbility(item.optString("selected")),
            )
        }
    }

    private fun parseTrainedSkills(raw: JSONObject?): BuilderTrainedSkillsRecord {
        return BuilderTrainedSkillsRecord(
            value = raw?.optJSONArray("value").strings().map(::normalizeSkillId),
            lore = raw?.optJSONArray("lore").strings(),
            additional = raw?.optNullableInt("additional"),
        )
    }

    private fun parseProficiencyGrants(raw: JSONArray?): List<BuilderProficiencyGrant> {
        return raw.objects().mapNotNull { item ->
            val category = item.optString("category").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val target = item.optString("target").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            BuilderProficiencyGrant(
                category = category,
                target = target,
                rank = item.optInt("rank").coerceIn(0, 4),
                source = item.optString("source"),
            )
        }
    }
}

private fun CatalogBuilderAssetEntity.payloadJsonObject(): JSONObject {
    val text = GZIPInputStream(ByteArrayInputStream(payloadJsonGzip)).bufferedReader(Charsets.UTF_8).use { reader ->
        reader.readText()
    }
    return JSONObject(text)
}

private fun Map<String, LoadedBuilderAsset>.requireAsset(
    name: String,
): LoadedBuilderAsset {
    return get(name) ?: error("Catalog builder asset is missing: $name")
}

private fun JSONObject.catalogRecordIds(): List<String> {
    return CATALOG_RECORD_ARRAY_KEYS.flatMap { key ->
        optJSONArray(key).objects().mapNotNull { item ->
            item.optString("catalogRecordId").takeIf { value -> value.isNotBlank() }
        }
    }
}

private fun JSONObject.descriptionFrom(descriptions: Map<String, String>): String {
    return optString("description").ifBlank {
        descriptions[optString("catalogRecordId")].orEmpty()
    }
}

private fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }
}

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private fun JSONArray?.ints(): List<Int> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            add(optInt(index))
        }
    }
}

private fun JSONObject.optNullableInt(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return optInt(name)
}

private fun parseAbility(raw: String): AbilityScore? {
    return when (raw.trim().lowercase()) {
        "str" -> AbilityScore.STRENGTH
        "dex" -> AbilityScore.DEXTERITY
        "con" -> AbilityScore.CONSTITUTION
        "int" -> AbilityScore.INTELLIGENCE
        "wis" -> AbilityScore.WISDOM
        "cha" -> AbilityScore.CHARISMA
        else -> null
    }
}

private val CATALOG_RECORD_ARRAY_KEYS = listOf(
    "classes",
    "ancestries",
    "heritages",
    "backgrounds",
    "features",
    "feats",
)
