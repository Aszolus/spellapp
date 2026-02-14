package com.spellapp.feature.character

import android.content.Context
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CharacterClass
import org.json.JSONArray
import org.json.JSONObject

class AssetCharacterClassDefinitionSource(
    context: Context,
    private val fallback: CharacterClassDefinitionSource = StaticCharacterClassDefinitionSource,
) : CharacterClassDefinitionSource {
    private val appContext = context.applicationContext
    private val definitionsByClass: Map<CharacterClass, CharacterClassDefinition> by lazy {
        loadDefinitions()
    }

    override fun allDefinitions(): List<CharacterClassDefinition> {
        return canonicalClassOrder().mapNotNull { definitionsByClass[it] }
    }

    override fun phaseOneDefinitions(): List<CharacterClassDefinition> {
        val phaseOneOrder = listOf(
            CharacterClass.WIZARD,
            CharacterClass.CLERIC,
            CharacterClass.DRUID,
        )
        val fromDataset = phaseOneOrder.mapNotNull { definitionsByClass[it] }
        return if (fromDataset.isNotEmpty()) {
            fromDataset
        } else {
            fallback.phaseOneDefinitions()
        }
    }

    override fun definitionFor(characterClass: CharacterClass): CharacterClassDefinition {
        return definitionsByClass[characterClass] ?: fallback.definitionFor(characterClass)
    }

    private fun loadDefinitions(): Map<CharacterClass, CharacterClassDefinition> {
        val fallbackMap = fallback.allDefinitions().associateBy { it.characterClass }
        val parsedMap = runCatching { parseFromAsset() }.getOrDefault(emptyMap())
        return fallbackMap + parsedMap
    }

    private fun parseFromAsset(): Map<CharacterClass, CharacterClassDefinition> {
        val rawJson = appContext.assets
            .open(ASSET_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(rawJson)
        val entries = root.optJSONArray("classes") ?: return emptyMap()
        val candidates = mutableListOf<ClassCandidate>()

        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val classId = entry.optString("id").trim().lowercase()
            val characterClass = classFromId(classId) ?: continue
            val spellcastingFlag = entry.optInt("spellcastingFlag", 0)
            if (spellcastingFlag <= 0) {
                continue
            }

            val label = entry.optString("name").takeIf { it.isNotBlank() }
                ?: fallback.definitionFor(characterClass).label
            val abilityOptions = parseAbilityOptions(entry.optJSONArray("keyAbilityOptions"))
            val normalizedOptions = abilityOptions.ifEmpty {
                fallback.definitionFor(characterClass).keyAbilityOptions
            }
            val publication = entry.optJSONObject("publication")
            val remaster = publication?.optBoolean("remaster", false) ?: false
            candidates += ClassCandidate(
                characterClass = characterClass,
                label = label,
                keyAbilityOptions = normalizedOptions,
                remaster = remaster,
            )
        }

        return candidates
            .groupBy { it.characterClass }
            .mapValues { (_, options) ->
                val preferred = options
                    .sortedWith(compareByDescending<ClassCandidate> { it.remaster })
                    .first()
                CharacterClassDefinition(
                    characterClass = preferred.characterClass,
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

    private fun classFromId(id: String): CharacterClass? {
        return when (id) {
            "wizard" -> CharacterClass.WIZARD
            "cleric" -> CharacterClass.CLERIC
            "druid" -> CharacterClass.DRUID
            else -> null
        }
    }

    private fun canonicalClassOrder(): List<CharacterClass> {
        return listOf(
            CharacterClass.WIZARD,
            CharacterClass.CLERIC,
            CharacterClass.DRUID,
            CharacterClass.OTHER,
        )
    }

    private data class ClassCandidate(
        val characterClass: CharacterClass,
        val label: String,
        val keyAbilityOptions: List<AbilityScore>,
        val remaster: Boolean,
    )

    private companion object {
        private const val ASSET_FILE_NAME = "classes.normalized.json"
    }
}
