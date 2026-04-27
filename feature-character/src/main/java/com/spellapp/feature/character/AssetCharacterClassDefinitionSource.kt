package com.spellapp.feature.character

import android.content.Context
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.ClassSpellcastingCatalog
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.normalizeClassId
import org.json.JSONArray
import org.json.JSONObject

class AssetCharacterClassDefinitionSource(
    context: Context,
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource = ClassSpellcastingCatalog,
    private val fallback: CharacterClassDefinitionSource = StaticCharacterClassDefinitionSource,
) : CharacterClassDefinitionSource {
    private val appContext = context.applicationContext
    private val definitionsByClassId: Map<String, CharacterClassDefinition> by lazy {
        loadDefinitions()
    }

    override fun allDefinitions(): List<CharacterClassDefinition> {
        val orderedIds = canonicalClassOrder()
        return orderedIds.mapNotNull { definitionsByClassId[it] } +
            definitionsByClassId.values.filterNot { it.classId in orderedIds }.sortedBy { it.label }
    }

    override fun phaseOneDefinitions(): List<CharacterClassDefinition> {
        return allDefinitions()
    }

    override fun definitionFor(classId: String): CharacterClassDefinition {
        return definitionsByClassId[normalizeClassId(classId)] ?: fallback.definitionFor(classId)
    }

    private fun loadDefinitions(): Map<String, CharacterClassDefinition> {
        val fallbackMap = fallback.allDefinitions().associateBy { normalizeClassId(it.classId) }
        val spellcastingMap = classSpellcastingCatalogSource.allDefinitions()
            .associate { definition ->
                normalizeClassId(definition.classId) to CharacterClassDefinition(
                    classId = definition.classId,
                    label = definition.label,
                    defaultKeyAbility = definition.defaultKeyAbility,
                    keyAbilityOptions = definition.keyAbilityOptions,
                )
            }
        val parsedMap = runCatching { parseFromAsset() }.getOrDefault(emptyMap())
        return fallbackMap + parsedMap + spellcastingMap
    }

    private fun parseFromAsset(): Map<String, CharacterClassDefinition> {
        val rawJson = appContext.assets
            .open(ASSET_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(rawJson)
        val entries = root.optJSONArray("classes") ?: return emptyMap()
        val candidates = mutableListOf<ClassCandidate>()

        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val classId = normalizeClassId(entry.optString("id"))
            if (classId == "other") {
                continue
            }

            val label = entry.optString("name").takeIf { it.isNotBlank() }
                ?: classId
            val abilityOptions = parseAbilityOptions(entry.optJSONArray("keyAbilityOptions"))
            val normalizedOptions = abilityOptions.ifEmpty {
                fallback.definitionFor(classId).keyAbilityOptions
            }
            val publication = entry.optJSONObject("source")
            val remaster = publication?.optBoolean("remaster", false) ?: false
            candidates += ClassCandidate(
                classId = classId,
                label = label,
                keyAbilityOptions = normalizedOptions,
                remaster = remaster,
            )
        }

        return candidates
            .groupBy { normalizeClassId(it.classId) }
            .mapValues { (_, options) ->
                val preferred = options
                    .sortedWith(compareByDescending<ClassCandidate> { it.remaster })
                    .first()
                CharacterClassDefinition(
                    classId = preferred.classId,
                    label = preferred.label,
                    defaultKeyAbility = preferred.keyAbilityOptions.first(),
                    keyAbilityOptions = preferred.keyAbilityOptions,
                )
            }
    }

    private fun parseAbilityOptions(raw: JSONArray?): List<AbilityScore> {
        if (raw == null) {
            return emptyList()
        }
        val options = mutableListOf<AbilityScore>()
        for (index in 0 until raw.length()) {
            val ability = raw.optString(index).trim().lowercase()
            val mapped = when (ability) {
                "str" -> AbilityScore.STRENGTH
                "dex" -> AbilityScore.DEXTERITY
                "con" -> AbilityScore.CONSTITUTION
                "int" -> AbilityScore.INTELLIGENCE
                "wis" -> AbilityScore.WISDOM
                "cha" -> AbilityScore.CHARISMA
                else -> null
            }
            if (mapped != null && mapped !in options) {
                options += mapped
            }
        }
        return options
    }

    private fun canonicalClassOrder(): List<String> {
        return classSpellcastingCatalogSource.allDefinitions()
            .map { it.classId } + "other"
    }

    private data class ClassCandidate(
        val classId: String,
        val label: String,
        val keyAbilityOptions: List<AbilityScore>,
        val remaster: Boolean,
    )

    private companion object {
        private const val ASSET_FILE_NAME = "classes.normalized.json"
    }
}
