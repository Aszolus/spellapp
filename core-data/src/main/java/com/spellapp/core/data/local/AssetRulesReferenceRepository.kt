package com.spellapp.core.data.local

import android.content.Context
import com.spellapp.core.data.RulesReferenceRepository
import com.spellapp.core.model.RulesReferenceCategory
import com.spellapp.core.model.RulesReferenceEntry
import com.spellapp.core.model.RulesReferenceKey
import org.json.JSONObject

class AssetRulesReferenceRepository(
    context: Context,
) : RulesReferenceRepository {
    private val appContext = context.applicationContext

    @Volatile
    private var cachedDataset: Map<RulesReferenceKey, RulesReferenceEntry>? = null

    override suspend fun getEntry(key: RulesReferenceKey): RulesReferenceEntry? {
        val normalizedKey = key.copy(slug = normalizeRulesReferenceSlug(key.slug))
        return loadDataset()[normalizedKey]
    }

    private fun loadDataset(): Map<RulesReferenceKey, RulesReferenceEntry> {
        cachedDataset?.let { return it }
        return synchronized(this) {
            cachedDataset ?: readDataset().also { cachedDataset = it }
        }
    }

    private fun readDataset(): Map<RulesReferenceKey, RulesReferenceEntry> {
        val assetText = runCatching {
            appContext.assets.open(RULES_LOOKUP_ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyMap()
        val root = JSONObject(assetText)
        val entries = buildMap {
            addSection(root, SECTION_TRAITS, RulesReferenceCategory.TRAIT)
            addSection(root, SECTION_CONDITIONS, RulesReferenceCategory.CONDITION)
        }
        return entries
    }

    private fun MutableMap<RulesReferenceKey, RulesReferenceEntry>.addSection(
        root: JSONObject,
        sectionName: String,
        category: RulesReferenceCategory,
    ) {
        val section = root.optJSONObject(sectionName) ?: return
        val keys = section.keys()
        while (keys.hasNext()) {
            val slugKey = keys.next()
            val item = section.optJSONObject(slugKey) ?: continue
            val slug = normalizeRulesReferenceSlug(item.optString("slug").ifBlank { slugKey })
            val label = item.optString("label").ifBlank { slugKey }
            val description = item.optString("description").trim()
            if (description.isBlank()) continue
            val key = RulesReferenceKey(category = category, slug = slug)
            put(
                key,
                RulesReferenceEntry(
                    key = key,
                    label = label,
                    description = description,
                ),
            )
        }
    }

    private companion object {
        private const val RULES_LOOKUP_ASSET = "rules.lookup.normalized.json"
        private const val SECTION_TRAITS = "traits"
        private const val SECTION_CONDITIONS = "conditions"
    }
}
