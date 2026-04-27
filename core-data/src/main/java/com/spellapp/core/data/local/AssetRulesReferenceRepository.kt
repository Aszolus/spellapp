package com.spellapp.core.data.local

import android.content.Context
import com.spellapp.core.data.RulesReferenceRepository
import com.spellapp.core.data.local.foundry.AssetFoundryLocalizationResolver
import com.spellapp.core.data.local.foundry.FoundryMarkupParser
import com.spellapp.core.model.CompendiumReferenceKey
import com.spellapp.core.model.RulesReferenceCategory
import com.spellapp.core.model.RulesReferenceEntry
import com.spellapp.core.model.RulesReferenceKey
import com.spellapp.core.model.TraitReferenceKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.zip.GZIPInputStream

class AssetRulesReferenceRepository(
    context: Context,
) : RulesReferenceRepository {
    private val appContext = context.applicationContext
    private val localizationResolver = AssetFoundryLocalizationResolver(appContext)

    @Volatile
    private var cachedTraitRecords: Map<String, StoredTraitRecord>? = null

    @Volatile
    private var cachedReferenceRecordsByCategory: Map<RulesReferenceCategory, Map<String, StoredReferenceRecord>> =
        emptyMap()

    @Volatile
    private var cachedEntries: Map<RulesReferenceKey, RulesReferenceEntry>? = null

    override suspend fun getEntry(key: RulesReferenceKey): RulesReferenceEntry? {
        val normalizedKey = normalizeKey(key)
        cachedEntries?.get(normalizedKey)?.let { return it }

        val entry = when (normalizedKey) {
            is TraitReferenceKey -> {
                val record = loadTraitRecords()[normalizedKey.slug] ?: return null
                RulesReferenceEntry(
                    key = normalizedKey,
                    label = record.label,
                    document = FoundryMarkupParser.parse(
                        descriptionRaw = record.descriptionRaw,
                        description = null,
                        localizationResolver = localizationResolver,
                    ),
                    type = null,
                )
            }

            is CompendiumReferenceKey -> {
                val record = loadReferenceAliases(normalizedKey.category)[normalizedKey.uuid] ?: return null
                RulesReferenceEntry(
                    key = normalizedKey,
                    label = record.name,
                    document = FoundryMarkupParser.parse(
                        descriptionRaw = record.descriptionRaw,
                        description = null,
                        localizationResolver = localizationResolver,
                    ),
                    type = record.type,
                )
            }
        }

        cachedEntries = (cachedEntries ?: emptyMap()) + (normalizedKey to entry)
        return entry
    }

    private fun loadTraitRecords(): Map<String, StoredTraitRecord> {
        cachedTraitRecords?.let { return it }
        return synchronized(this) {
            cachedTraitRecords ?: readTraitRecords().also { cachedTraitRecords = it }
        }
    }

    private fun loadReferenceAliases(category: RulesReferenceCategory): Map<String, StoredReferenceRecord> {
        val assetName = referenceShardAssetName(category) ?: return emptyMap()
        cachedReferenceRecordsByCategory[category]?.let { return it }
        return synchronized(this) {
            cachedReferenceRecordsByCategory[category] ?: readReferenceAliases(assetName).also { aliases ->
                cachedReferenceRecordsByCategory = cachedReferenceRecordsByCategory + (category to aliases)
            }
        }
    }

    private fun readTraitRecords(): Map<String, StoredTraitRecord> {
        val root = readJsonObjectAsset(TRAITS_SHARD_ASSET, "$TRAITS_SHARD_ASSET.gz") ?: return emptyMap()
        return buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val slugKey = keys.next()
                val item = root.optJSONObject(slugKey) ?: continue
                val traitKey = TraitReferenceKey.fromSlug(slugKey)
                val label = item.optString(FIELD_LABEL).ifBlank { slugKey }
                val descriptionRaw = item.optString(FIELD_DESCRIPTION_RAW).trim()
                if (descriptionRaw.isBlank()) continue
                put(
                    traitKey.slug,
                    StoredTraitRecord(
                        label = label,
                        descriptionRaw = descriptionRaw,
                    ),
                )
            }
        }
    }

    private fun readReferenceAliases(assetName: String): Map<String, StoredReferenceRecord> {
        val references = readJsonArrayAsset(assetName, "$assetName.gz") ?: JSONArray()
        val aliasMap = mutableMapOf<String, StoredReferenceRecord>()
        for (index in 0 until references.length()) {
            val item = references.optJSONObject(index) ?: continue
            val canonicalKey = CompendiumReferenceKey.fromUuid(item.optString(FIELD_UUID))
            val descriptionRaw = item.optString(FIELD_DESCRIPTION_RAW).trim()
            val name = item.optString(FIELD_NAME).trim()
            if (descriptionRaw.isBlank() || name.isBlank()) {
                continue
            }
            val record = StoredReferenceRecord(
                name = name,
                type = item.optString(FIELD_TYPE).trim().ifBlank { null },
                descriptionRaw = descriptionRaw,
            )
            aliasMap[canonicalKey.uuid] = record
            val aliases = item.optJSONArray(FIELD_ALIASES) ?: JSONArray()
            for (aliasIndex in 0 until aliases.length()) {
                val alias = CompendiumReferenceKey.fromUuid(aliases.optString(aliasIndex)).uuid
                aliasMap.putIfAbsent(alias, record)
            }
        }
        return aliasMap
    }

    private fun readJsonObjectAsset(vararg assetNames: String): JSONObject? {
        val assetText = readAssetText(*assetNames) ?: return null
        return JSONObject(assetText)
    }

    private fun readJsonArrayAsset(vararg assetNames: String): JSONArray? {
        val assetText = readAssetText(*assetNames) ?: return null
        return JSONArray(assetText)
    }

    private fun readAssetText(vararg assetNames: String): String? {
        assetNames.forEach { assetName ->
            val assetText = runCatching {
                appContext.assets.open(assetName).use { assetStream ->
                    val decodedStream: InputStream = if (assetName.endsWith(".gz")) {
                        GZIPInputStream(assetStream)
                    } else {
                        assetStream
                    }
                    decodedStream.bufferedReader().use { it.readText() }
                }
            }.getOrNull()
            if (assetText != null) {
                return assetText
            }
        }
        return null
    }

    private fun normalizeKey(key: RulesReferenceKey): RulesReferenceKey {
        return when (key) {
            is TraitReferenceKey -> TraitReferenceKey.fromSlug(key.slug)
            is CompendiumReferenceKey -> CompendiumReferenceKey.fromUuid(key.uuid)
        }
    }

    private fun referenceShardAssetName(category: RulesReferenceCategory): String? {
        return when (category) {
            RulesReferenceCategory.CONDITION -> CONDITIONS_SHARD_ASSET
            RulesReferenceCategory.ACTION -> ACTIONS_SHARD_ASSET
            RulesReferenceCategory.SPELL_EFFECT -> SPELL_EFFECTS_SHARD_ASSET
            RulesReferenceCategory.SPELL -> SPELLS_SHARD_ASSET
            RulesReferenceCategory.FEAT -> FEATS_SHARD_ASSET
            RulesReferenceCategory.ITEM -> ITEMS_SHARD_ASSET
            RulesReferenceCategory.TRAIT,
            RulesReferenceCategory.UNKNOWN -> null
        }
    }

    private companion object {
        private const val TRAITS_SHARD_ASSET = "rules.reference.traits.json"
        private const val CONDITIONS_SHARD_ASSET = "rules.reference.conditions.json"
        private const val ACTIONS_SHARD_ASSET = "rules.reference.actions.json"
        private const val SPELL_EFFECTS_SHARD_ASSET = "rules.reference.spell-effects.json"
        private const val FEATS_SHARD_ASSET = "rules.reference.feats.json"
        private const val SPELLS_SHARD_ASSET = "rules.reference.spells.json"
        private const val ITEMS_SHARD_ASSET = "rules.reference.items.json"

        private const val FIELD_ALIASES = "aliases"
        private const val FIELD_DESCRIPTION_RAW = "descriptionRaw"
        private const val FIELD_LABEL = "label"
        private const val FIELD_NAME = "name"
        private const val FIELD_TYPE = "type"
        private const val FIELD_UUID = "uuid"
    }

    private data class StoredTraitRecord(
        val label: String,
        val descriptionRaw: String,
    )

    private data class StoredReferenceRecord(
        val name: String,
        val type: String?,
        val descriptionRaw: String,
    )
}
