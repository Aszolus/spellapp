package com.spellapp.feature.character

import android.content.Context
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.normalizeClassId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.MessageDigest
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
    val featShards: List<BuilderFeatShard>,
    val classFeatures: List<BuilderFeatureRecord>,
    val ancestryFeatures: List<BuilderFeatureRecord>,
) {
    val classesById: Map<String, BuilderClassRecord> = classes.associateBy { normalizeClassId(it.id) }
    val ancestriesById: Map<String, BuilderAncestryRecord> = ancestries.associateBy { it.id }
    val heritagesById: Map<String, BuilderHeritageRecord> = heritages.associateBy { it.id }
    val backgroundsById: Map<String, BuilderBackgroundRecord> = backgrounds.associateBy { it.id }
    val featIndexById: Map<String, BuilderFeatIndexRecord> = featIndex.associateBy { it.id }

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

    fun featCandidatesFor(slot: BuilderFeatSlot): List<BuilderFeatIndexRecord> {
        return featIndex
            .filter { feat -> feat.category == slot.kind && feat.level <= slot.level }
            .sortedWith(compareBy<BuilderFeatIndexRecord> { it.level }.thenBy { it.name })
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
)

data class BuilderFeatSlot(
    val slotId: String,
    val kind: String,
    val level: Int,
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
)

interface CharacterBuilderCatalogSource {
    suspend fun loadCatalog(): CharacterBuilderCatalogResult
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

    override suspend fun loadCatalog(): CharacterBuilderCatalogResult {
        cachedResult?.let { return it }
        return withContext(Dispatchers.IO) {
            synchronized(this@AssetCharacterBuilderCatalogSource) {
                cachedResult ?: loadCatalogResult().also { cachedResult = it }
            }
        }
    }

    private fun loadCatalogResult(): CharacterBuilderCatalogResult {
        return runCatching {
            val manifest = readJsonObjectAsset("builder.manifest.normalized.json")
            verifyManifestAssets(manifest)
            val featShards = parseFeatShards(manifest.optJSONArray("assets"), readJsonObjectAsset("feats.index.normalized.json"))
            CharacterBuilderCatalogResult(
                catalog = CharacterBuilderCatalog(
                    classes = parseClasses(readJsonObjectAsset("classes.normalized.json").optJSONArray("classes")),
                    ancestries = parseAncestries(readJsonObjectAsset("ancestries.normalized.json").optJSONArray("ancestries")),
                    heritages = parseHeritages(readJsonObjectAsset("heritages.normalized.json").optJSONArray("heritages")),
                    backgrounds = parseBackgrounds(readJsonObjectAsset("backgrounds.normalized.json").optJSONArray("backgrounds")),
                    featIndex = parseFeatIndex(readJsonObjectAsset("feats.index.normalized.json").optJSONArray("feats")),
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
            val name = asset.optString("name").takeIf { it.isNotBlank() }
                ?: error("Builder manifest contains an asset without a name.")
            val assetBytes = readAssetBytes(name)
            val content = assetBytes.decodedContent()
            if (!assetBytes.wasAndroidExpandedGzip) {
                if (asset.optInt("bytes") != assetBytes.bytes.size) {
                    error("Builder asset size mismatch: $name")
                }
                if (asset.optString("artifactSha256") != sha256(assetBytes.bytes)) {
                    error("Builder asset hash mismatch: $name")
                }
            }
            if (asset.optString("contentSha256") != sha256(content)) {
                error("Builder asset content hash mismatch: $name")
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

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
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
