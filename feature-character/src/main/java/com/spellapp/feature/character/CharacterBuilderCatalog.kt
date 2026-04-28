package com.spellapp.feature.character

import android.content.Context
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.normalizeClassId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

data class CharacterBuilderCatalogResult(
    val catalog: CharacterBuilderCatalog? = null,
    val loadError: String? = null,
)

data class CharacterBuilderCatalog(
    val classes: List<BuilderClassRecord>,
    val ancestries: List<BuilderAncestryRecord>,
    val heritages: List<BuilderHeritageRecord>,
    val backgrounds: List<BuilderBackgroundRecord>,
    val featIndex: List<BuilderFeatIndexRecord>,
    val feats: List<BuilderFeatRecord>,
    val featShards: List<BuilderFeatShard>,
    val classFeatures: List<BuilderFeatureRecord>,
    val ancestryFeatures: List<BuilderFeatureRecord>,
) {
    private val sourceFilterCache = object : LinkedHashMap<Set<String>, CharacterBuilderCatalog>(8, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Set<String>, CharacterBuilderCatalog>?,
        ): Boolean = size > SOURCE_FILTER_CACHE_SIZE
    }

    val classesById: Map<String, BuilderClassRecord> = classes.associateBy { normalizeClassId(it.id) }
    val ancestriesById: Map<String, BuilderAncestryRecord> = ancestries.associateBy { it.id }
    val heritagesById: Map<String, BuilderHeritageRecord> = heritages.associateBy { it.id }
    val backgroundsById: Map<String, BuilderBackgroundRecord> = backgrounds.associateBy { it.id }
    val featIndexById: Map<String, BuilderFeatIndexRecord> = featIndex.associateBy { it.id }
    val featsById: Map<String, BuilderFeatRecord> = feats.associateBy { it.id }
    val classFeaturesByUuid: Map<String, BuilderFeatureRecord> = classFeatures
        .mapNotNull { feature -> feature.uuid?.let { uuid -> uuid to feature } }
        .toMap()
    private val cachedSourceTitles: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        buildSet {
            classes.mapNotNullTo(this) { it.source.title }
            ancestries.mapNotNullTo(this) { it.source.title }
            heritages.mapNotNullTo(this) { it.source.title }
            backgrounds.mapNotNullTo(this) { it.source.title }
            featIndex.mapNotNullTo(this) { it.source.title }
            classFeatures.mapNotNullTo(this) { it.source.title }
            ancestryFeatures.mapNotNullTo(this) { it.source.title }
        }.sorted()
    }

    fun heritagesForAncestry(ancestryId: String?): List<BuilderHeritageRecord> {
        val normalized = ancestryId?.trim().orEmpty()
        if (normalized.isBlank()) return emptyList()
        return heritages
            .filter { heritage -> heritage.ancestryId == normalized || heritage.ancestryId == VERSATILE_HERITAGE_ANCESTRY_ID }
            .sortedWith(compareBy<BuilderHeritageRecord> { it.ancestryId != normalized }.thenBy { it.name })
    }

    fun featSlotsFor(classId: String, level: Int): List<BuilderFeatSlot> {
        return classesById[normalizeClassId(classId)]
            ?.featSlots
            .orEmpty()
            .filter { slot -> slot.level <= level }
            .sortedWith(compareBy<BuilderFeatSlot> { it.level }.thenBy { it.kind })
    }

    fun featCandidatesFor(slot: BuilderFeatSlot): List<BuilderFeatRecord> {
        return feats
            .filter { feat -> feat.category == slot.kind && feat.level <= slot.level }
            .sortedWith(compareBy<BuilderFeatRecord> { it.level }.thenBy { it.name })
    }

    fun sourceTitles(): List<String> {
        return cachedSourceTitles
    }

    fun filteredBySources(sourceBooks: Set<String>): CharacterBuilderCatalog {
        val sourceKeys = sourceBooks.normalizedSourceBookKeys()
        synchronized(sourceFilterCache) {
            sourceFilterCache[sourceKeys]?.let { return it }
        }
        val filtered = if (sourceKeys.isEmpty()) {
            copy(
                classes = emptyList(),
                ancestries = emptyList(),
                heritages = emptyList(),
                backgrounds = emptyList(),
                featIndex = emptyList(),
                feats = emptyList(),
                classFeatures = emptyList(),
                ancestryFeatures = emptyList(),
            )
        } else {
            val ancestryIds = ancestries
                .filter { it.source.isAllowedBySourceKeys(sourceKeys) }
                .map { it.id }
                .toSet()
            copy(
                classes = classes.filter { it.source.isAllowedBySourceKeys(sourceKeys) },
                ancestries = ancestries.filter { it.id in ancestryIds },
                heritages = heritages.filter { heritage ->
                    heritage.source.isAllowedBySourceKeys(sourceKeys) &&
                        (heritage.ancestryId in ancestryIds || heritage.ancestryId == VERSATILE_HERITAGE_ANCESTRY_ID)
                },
                backgrounds = backgrounds.filter { it.source.isAllowedBySourceKeys(sourceKeys) },
                featIndex = featIndex.filter { it.source.isAllowedBySourceKeys(sourceKeys) },
                feats = feats.filter { it.source.isAllowedBySourceKeys(sourceKeys) },
                classFeatures = classFeatures.filter { it.source.isAllowedBySourceKeys(sourceKeys) },
                ancestryFeatures = ancestryFeatures.filter { it.source.isAllowedBySourceKeys(sourceKeys) },
            )
        }
        synchronized(sourceFilterCache) {
            sourceFilterCache[sourceKeys] = filtered
        }
        return filtered
    }

    private companion object {
        const val SOURCE_FILTER_CACHE_SIZE = 8
    }
}

private const val VERSATILE_HERITAGE_ANCESTRY_ID = "unknown"

data class BuilderSourceRecord(
    val title: String?,
    val license: String?,
    val remaster: Boolean,
)

data class BuilderTraitsRecord(
    val rarity: String,
    val values: List<String>,
)

data class BuilderWarningRecord(
    val warningId: String,
    val recordId: String,
    val recordType: String,
    val ruleType: String,
    val sourceRulePath: String,
    val originalText: String,
    val severity: String,
    val message: String,
)

data class BuilderGrantRecord(
    val grantId: String,
    val name: String?,
    val uuid: String?,
    val level: Int?,
    val source: String,
)

data class BuilderChoicePromptRecord(
    val promptId: String,
    val label: String,
    val sourceRulePath: String,
    val required: Boolean,
    val choiceValues: List<BuilderChoiceValueRecord> = emptyList(),
)

data class BuilderFeatSlot(
    val slotId: String,
    val kind: String,
    val level: Int,
)

data class BuilderAbilityBoostRecord(
    val id: String,
    val abilities: List<AbilityScore>,
    val selected: AbilityScore?,
)

data class BuilderTrainedSkillsRecord(
    val value: List<String> = emptyList(),
    val lore: List<String> = emptyList(),
    val additional: Int? = null,
)

data class BuilderProficiencyGrant(
    val category: String,
    val target: String,
    val rank: Int,
    val source: String,
)

data class BuilderChoiceValueRecord(
    val value: String,
    val label: String,
)

data class BuilderClassRecord(
    val id: String,
    val name: String,
    val hp: Int?,
    val keyAbilityOptions: List<AbilityScore>,
    val featSlots: List<BuilderFeatSlot>,
    val source: BuilderSourceRecord,
    val traits: BuilderTraitsRecord,
    val description: String,
    val warnings: List<BuilderWarningRecord>,
    val uuid: String? = null,
    val trainedSkills: BuilderTrainedSkillsRecord = BuilderTrainedSkillsRecord(),
    val skillIncreaseLevels: List<Int> = emptyList(),
    val skillFeatLevels: List<Int> = emptyList(),
    val baseProficiencies: List<BuilderProficiencyGrant> = emptyList(),
    val featureRefs: List<String> = emptyList(),
)

data class BuilderAncestryRecord(
    val id: String,
    val name: String,
    val hp: Int?,
    val speed: String,
    val size: String,
    val source: BuilderSourceRecord,
    val traits: BuilderTraitsRecord,
    val description: String,
    val grants: List<BuilderGrantRecord>,
    val choicePrompts: List<BuilderChoicePromptRecord>,
    val warnings: List<BuilderWarningRecord>,
    val uuid: String? = null,
    val boosts: List<BuilderAbilityBoostRecord> = emptyList(),
    val flaws: List<BuilderAbilityBoostRecord> = emptyList(),
)

data class BuilderHeritageRecord(
    val id: String,
    val name: String,
    val ancestryId: String,
    val source: BuilderSourceRecord,
    val traits: BuilderTraitsRecord,
    val description: String,
    val grants: List<BuilderGrantRecord>,
    val choicePrompts: List<BuilderChoicePromptRecord>,
    val warnings: List<BuilderWarningRecord>,
    val uuid: String? = null,
)

data class BuilderBackgroundRecord(
    val id: String,
    val name: String,
    val source: BuilderSourceRecord,
    val traits: BuilderTraitsRecord,
    val description: String,
    val grants: List<BuilderGrantRecord>,
    val choicePrompts: List<BuilderChoicePromptRecord>,
    val warnings: List<BuilderWarningRecord>,
    val uuid: String? = null,
    val boosts: List<BuilderAbilityBoostRecord> = emptyList(),
    val trainedSkills: BuilderTrainedSkillsRecord = BuilderTrainedSkillsRecord(),
)

data class BuilderFeatureRecord(
    val id: String,
    val name: String,
    val level: Int,
    val category: String,
    val source: BuilderSourceRecord,
    val traits: BuilderTraitsRecord,
    val description: String,
    val grants: List<BuilderGrantRecord>,
    val choicePrompts: List<BuilderChoicePromptRecord>,
    val warnings: List<BuilderWarningRecord>,
    val uuid: String? = null,
    val proficiencyGrants: List<BuilderProficiencyGrant> = emptyList(),
)

data class BuilderFeatShard(
    val name: String,
    val category: String,
    val count: Int,
)

data class BuilderFeatIndexRecord(
    val id: String,
    val name: String,
    val category: String,
    val level: Int,
    val rarity: String,
    val traits: List<String>,
    val shard: String,
    val source: BuilderSourceRecord = BuilderSourceRecord(title = null, license = null, remaster = false),
)

data class BuilderFeatRecord(
    val id: String,
    val name: String,
    val category: String,
    val level: Int,
    val rarity: String,
    val traits: List<String>,
    val source: BuilderSourceRecord,
    val description: String,
    val prerequisites: List<String>,
    val grants: List<BuilderGrantRecord>,
    val choicePrompts: List<BuilderChoicePromptRecord>,
    val warnings: List<BuilderWarningRecord>,
    val actionType: String?,
    val actions: String?,
    val shard: String,
    val uuid: String? = null,
    val proficiencyGrants: List<BuilderProficiencyGrant> = emptyList(),
)

interface CharacterBuilderCatalogSource {
    suspend fun loadCatalog(): CharacterBuilderCatalogResult
    suspend fun loadAvailableSourceTitles(): List<String> =
        loadCatalog().catalog?.sourceTitles().orEmpty()
    suspend fun loadFeatRecords(): List<BuilderFeatRecord> = emptyList()
}

object EmptyCharacterBuilderCatalogSource : CharacterBuilderCatalogSource {
    override suspend fun loadCatalog(): CharacterBuilderCatalogResult =
        CharacterBuilderCatalogResult(loadError = "Character builder catalog is not available.")
}

class AssetCharacterBuilderCatalogSource(
    context: Context,
) : CharacterBuilderCatalogSource {
    private val appContext = context.applicationContext

    @Volatile
    private var cachedResult: CharacterBuilderCatalogResult? = null

    @Volatile
    private var cachedFeatRecords: List<BuilderFeatRecord>? = null

    @Volatile
    private var cachedSourceTitles: List<String>? = null

    override suspend fun loadCatalog(): CharacterBuilderCatalogResult {
        cachedResult?.let { return it }
        return withContext(Dispatchers.IO) {
            synchronized(this@AssetCharacterBuilderCatalogSource) {
                cachedResult ?: loadCatalogResult().also { cachedResult = it }
            }
        }
    }

    override suspend fun loadAvailableSourceTitles(): List<String> {
        cachedSourceTitles?.let { return it }
        return withContext(Dispatchers.IO) {
            val manifestSources = synchronized(this@AssetCharacterBuilderCatalogSource) {
                cachedSourceTitles ?: run {
                    val manifest = readJsonObjectAsset("builder.manifest.normalized.json")
                    val sources = manifest.optJSONArray("sources")
                        .strings()
                    sources.takeIf { it.isNotEmpty() }?.also { cachedSourceTitles = it }
                }
            }
            manifestSources ?: loadCatalog().catalog?.sourceTitles().orEmpty()
        }
    }

    private fun loadCatalogResult(): CharacterBuilderCatalogResult {
        return runCatching {
            val manifest = readJsonObjectAsset("builder.manifest.normalized.json")
            verifyManifestAssets(manifest)
            val featIndexRoot = readJsonObjectAsset("feats.index.normalized.json")
            val featShards = parseFeatShards(manifest.optJSONArray("assets"), featIndexRoot)
            CharacterBuilderCatalogResult(
                catalog = CharacterBuilderCatalog(
                    classes = parseClasses(readJsonObjectAsset("classes.normalized.json").optJSONArray("classes")),
                    ancestries = parseAncestries(readJsonObjectAsset("ancestries.normalized.json").optJSONArray("ancestries")),
                    heritages = parseHeritages(readJsonObjectAsset("heritages.normalized.json").optJSONArray("heritages")),
                    backgrounds = parseBackgrounds(readJsonObjectAsset("backgrounds.normalized.json").optJSONArray("backgrounds")),
                    featIndex = parseFeatIndex(featIndexRoot.optJSONArray("feats")),
                    feats = emptyList(),
                    featShards = featShards,
                    classFeatures = parseFeatures(readJsonObjectAsset("class-features.normalized.json.gz").optJSONArray("features")),
                    ancestryFeatures = parseFeatures(readJsonObjectAsset("ancestry-features.normalized.json.gz").optJSONArray("features")),
                ),
            )
        }.getOrElse { error ->
            CharacterBuilderCatalogResult(
                loadError = error.message ?: "Character builder catalog could not be loaded.",
            )
        }
    }

    override suspend fun loadFeatRecords(): List<BuilderFeatRecord> {
        cachedFeatRecords?.let { return it }
        return withContext(Dispatchers.IO) {
            synchronized(this@AssetCharacterBuilderCatalogSource) {
                cachedFeatRecords ?: loadFeatRecordsResult().also { cachedFeatRecords = it }
            }
        }
    }

    private fun loadFeatRecordsResult(): List<BuilderFeatRecord> {
        val manifest = readJsonObjectAsset("builder.manifest.normalized.json")
        val featIndexRoot = readJsonObjectAsset("feats.index.normalized.json")
        return parseFeatShards(manifest.optJSONArray("assets"), featIndexRoot)
            .flatMap { shard ->
                parseFeats(
                    raw = readJsonObjectAsset(shard.name).optJSONArray("feats"),
                    shardName = shard.name,
                )
            }
    }

    private fun parseClasses(raw: JSONArray?): List<BuilderClassRecord> {
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
                description = item.optString("description"),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
                trainedSkills = parseTrainedSkills(item.optJSONObject("trainedSkills")),
                skillIncreaseLevels = item.optJSONArray("skillIncreaseLevels").ints(),
                skillFeatLevels = item.optJSONArray("skillFeatLevels").ints(),
                baseProficiencies = parseProficiencyGrants(item.optJSONArray("baseProficiencies")),
                featureRefs = item.optJSONArray("featureRefs").strings(),
            )
        }.sortedBy { it.name }
    }

    private fun parseAncestries(raw: JSONArray?): List<BuilderAncestryRecord> {
        return raw.objects().map { item ->
            BuilderAncestryRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                hp = item.optNullableInt("hp"),
                speed = item.opt("speed")?.toString().orEmpty(),
                size = item.opt("size")?.toString().orEmpty(),
                source = parseSource(item.optJSONObject("source")),
                traits = parseTraits(item.optJSONObject("traits")),
                description = item.optString("description"),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
                boosts = parseAbilityBoosts(item.optJSONArray("boosts")),
                flaws = parseAbilityBoosts(item.optJSONArray("flaws")),
            )
        }.sortedBy { it.name }
    }

    private fun parseHeritages(raw: JSONArray?): List<BuilderHeritageRecord> {
        return raw.objects().map { item ->
            BuilderHeritageRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                ancestryId = item.optString("ancestryId"),
                source = parseSource(item.optJSONObject("source")),
                traits = parseTraits(item.optJSONObject("traits")),
                description = item.optString("description"),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
            )
        }.sortedWith(compareBy<BuilderHeritageRecord> { it.ancestryId }.thenBy { it.name })
    }

    private fun parseBackgrounds(raw: JSONArray?): List<BuilderBackgroundRecord> {
        return raw.objects().map { item ->
            BuilderBackgroundRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                source = parseSource(item.optJSONObject("source")),
                traits = parseTraits(item.optJSONObject("traits")),
                description = item.optString("description"),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
                boosts = parseAbilityBoosts(item.optJSONArray("boosts")),
                trainedSkills = parseTrainedSkills(item.optJSONObject("trainedSkills")),
            )
        }.sortedBy { it.name }
    }

    private fun parseFeatures(raw: JSONArray?): List<BuilderFeatureRecord> {
        return raw.objects().map { item ->
            BuilderFeatureRecord(
                id = item.optString("id"),
                name = item.optString("name").ifBlank { item.optString("id") },
                level = item.optInt("level"),
                category = item.optString("category"),
                source = parseSource(item.optJSONObject("source")),
                traits = parseTraits(item.optJSONObject("traits")),
                description = item.optString("description"),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                uuid = item.optString("uuid").ifBlank { null },
                proficiencyGrants = parseProficiencyGrants(item.optJSONArray("proficiencyGrants")),
            )
        }
    }

    private fun parseFeatIndex(raw: JSONArray?): List<BuilderFeatIndexRecord> {
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

    private fun parseFeats(
        raw: JSONArray?,
        shardName: String,
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
                description = item.optString("description"),
                prerequisites = item.optJSONArray("prerequisites").strings(),
                grants = parseGrants(item.optJSONArray("grants")),
                choicePrompts = parseChoicePrompts(item.optJSONArray("choicePrompts")),
                warnings = parseWarnings(item.optJSONArray("warnings")),
                actionType = item.optString("actionType").ifBlank { null },
                actions = item.opt("actions")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                shard = shardName,
                uuid = item.optString("uuid").ifBlank { null },
                proficiencyGrants = parseProficiencyGrants(item.optJSONArray("proficiencyGrants")),
            )
        }
    }

    private fun parseFeatShards(
        manifestAssets: JSONArray?,
        featIndexRoot: JSONObject,
    ): List<BuilderFeatShard> {
        return featIndexRoot.optJSONArray("shards").objects().map { item ->
            BuilderFeatShard(
                name = item.optString("name"),
                category = item.optString("category"),
                count = item.optInt("count"),
            )
        }.filter { shard ->
            manifestAssets.objects().any { asset -> asset.optString("name") == shard.name }
        }
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
            BuilderChoicePromptRecord(
                promptId = item.optString("promptId"),
                label = item.optString("label").ifBlank { "Choice" },
                sourceRulePath = item.optString("sourceRulePath"),
                required = item.optBoolean("required", true),
                choiceValues = parseChoiceValues(item.opt("choiceConfig")),
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

    private fun readJsonObjectAsset(assetName: String): JSONObject {
        val assetText = decodedAssetText(assetName)
        return JSONObject(assetText)
    }

    private fun verifyManifestAssets(manifest: JSONObject) {
        val assets = manifest.optJSONArray("assets").objects()
        if (manifest.optInt("assetCount") != assets.size) {
            error("Builder manifest asset count does not match listed assets.")
        }
        val assetNames = assets.map { asset -> asset.optString("name") }.toSet()
        REQUIRED_BUILDER_ASSETS.forEach { assetName ->
            if (assetName !in assetNames) {
                error("Builder manifest is missing required asset: $assetName")
            }
        }
        assets.forEach { asset ->
            if (asset.optString("name").isBlank()) {
                error("Builder manifest contains an asset without a name.")
            }
        }
    }

    private fun decodedAssetText(assetName: String): String {
        val assetBytes = readAssetBytes(assetName)
        return assetBytes.decodedContent().decodeToString()
    }

    private fun readAssetBytes(assetName: String): BuilderAssetBytes {
        runCatching {
            appContext.assets.open(assetName).use { assetStream ->
                return BuilderAssetBytes(
                    bytes = assetStream.readBytes(),
                    isGzipped = assetName.endsWith(".gz"),
                    wasAndroidExpandedGzip = false,
                )
            }
        }
        if (assetName.endsWith(".gz")) {
            val expandedName = assetName.removeSuffix(".gz")
            runCatching {
                appContext.assets.open(expandedName).use { assetStream ->
                    return BuilderAssetBytes(
                        bytes = assetStream.readBytes(),
                        isGzipped = false,
                        wasAndroidExpandedGzip = true,
                    )
                }
            }
        }
        error("Builder asset is missing: $assetName")
    }

    private companion object {
        val REQUIRED_BUILDER_ASSETS = setOf(
            "classes.normalized.json",
            "ancestries.normalized.json",
            "heritages.normalized.json",
            "backgrounds.normalized.json",
            "class-features.normalized.json.gz",
            "ancestry-features.normalized.json.gz",
            "feats.index.normalized.json",
        )
    }

    private data class BuilderAssetBytes(
        val bytes: ByteArray,
        val isGzipped: Boolean,
        val wasAndroidExpandedGzip: Boolean,
    ) {
        fun decodedContent(): ByteArray {
            return if (isGzipped) {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            } else {
                bytes
            }
        }
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

internal fun normalizeSkillId(raw: String): String {
    return raw.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
}

internal fun BuilderSourceRecord.isAllowedBy(sourceBooks: Set<String>): Boolean {
    return isAllowedBySourceKeys(sourceBooks.normalizedSourceBookKeys())
}

private fun BuilderSourceRecord.isAllowedBySourceKeys(sourceBookKeys: Set<String>): Boolean {
    val titleKey = title?.sourceBookKey() ?: return false
    return titleKey in sourceBookKeys
}

private fun Set<String>.normalizedSourceBookKeys(): Set<String> {
    return mapTo(sortedSetOf()) { sourceBook -> sourceBook.sourceBookKey() }
        .filterTo(sortedSetOf()) { it.isNotBlank() }
}

internal fun String.sourceBookKey(): String {
    return trim()
        .lowercase()
        .removePrefix("pathfinder ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
}
