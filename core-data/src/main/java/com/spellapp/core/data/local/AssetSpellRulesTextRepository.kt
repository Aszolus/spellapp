package com.spellapp.core.data.local

import android.content.Context
import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.data.local.foundry.AssetFoundryLocalizationResolver
import com.spellapp.core.data.local.foundry.FoundryMarkupParser
import com.spellapp.core.model.RulesTextDocument
import org.json.JSONArray
import org.json.JSONObject

class AssetSpellRulesTextRepository(
    context: Context,
) : SpellRulesTextRepository {
    private val appContext = context.applicationContext
    private val localizationResolver = AssetFoundryLocalizationResolver(appContext)

    @Volatile
    private var cachedSpellDescriptions: Map<String, SpellRulesSource>? = null

    override suspend fun getSpellRulesText(
        spellId: String,
        spellRank: Int?,
    ): RulesTextDocument? {
        val source = loadSpellDescriptions()[spellId] ?: return null
        return FoundryMarkupParser.parse(
            descriptionRaw = source.descriptionRaw,
            description = source.description,
            localizationResolver = localizationResolver,
            itemLevel = spellRank,
            itemRank = spellRank,
        )
    }

    private fun loadSpellDescriptions(): Map<String, SpellRulesSource> {
        cachedSpellDescriptions?.let { return it }
        return synchronized(this) {
            cachedSpellDescriptions ?: readSpellDescriptions().also { cachedSpellDescriptions = it }
        }
    }

    private fun readSpellDescriptions(): Map<String, SpellRulesSource> {
        val assetText = appContext.assets.open(SPELLS_ASSET).bufferedReader().use { it.readText() }
        val root = JSONObject(assetText)
        val spells = root.optJSONArray("spells") ?: JSONArray()
        return buildMap {
            for (index in 0 until spells.length()) {
                val spell = spells.optJSONObject(index) ?: continue
                val id = spell.optString("id").trim()
                if (id.isBlank()) continue
                put(
                    id,
                    SpellRulesSource(
                        descriptionRaw = spell.optString("descriptionRaw").takeIf { it.isNotBlank() },
                        description = spell.optString("description").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    private data class SpellRulesSource(
        val descriptionRaw: String?,
        val description: String?,
    )

    private companion object {
        private const val SPELLS_ASSET = "spells.normalized.json"
    }
}
