package com.spellapp.core.data.local

import org.json.JSONArray
import org.json.JSONObject

internal object SpellDatasetParser {
    fun parseEntities(datasetJson: String): List<SpellEntity> {
        val root = JSONObject(datasetJson)
        val spellsArray = root.optJSONArray("spells") ?: JSONArray()
        val entities = mutableListOf<SpellEntity>()

        for (index in 0 until spellsArray.length()) {
            val spellObj = spellsArray.getJSONObject(index)
            val id = spellObj.optString("id")
            val name = spellObj.optString("name")
            if (id.isBlank() || name.isBlank()) {
                continue
            }

            val traditions = spellObj.optJSONArray("traditions").toStringList()
            val traits = spellObj.optJSONArray("traits").toStringList()
            val sourceObj = spellObj.optJSONObject("source")
            val traditionSummary = if (traditions.isNotEmpty()) {
                traditions.joinToString(", ")
            } else {
                spellObj.optString("tradition")
            }

            entities += SpellEntity(
                id = id,
                name = name,
                rank = spellObj.optInt("rank", 0),
                traditionSummary = traditionSummary,
                rarity = spellObj.optString("rarity"),
                traitsCsv = traits.joinToString(","),
                castTime = spellObj.optString("cast"),
                rangeText = spellObj.optString("range"),
                targetText = spellObj.optString("target"),
                durationText = spellObj.optString("duration"),
                description = spellObj.optString("description"),
                license = spellObj.optString("license"),
                sourceBook = sourceObj?.optString("book").orEmpty(),
                sourcePage = sourceObj?.optIntOrNull("page"),
            )
        }

        return entities
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val values = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) {
                values += value
            }
        }
        return values
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }
}
