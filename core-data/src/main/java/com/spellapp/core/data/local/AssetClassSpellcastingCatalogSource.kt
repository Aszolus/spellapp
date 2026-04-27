package com.spellapp.core.data.local

import android.content.Context
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingStyle
import com.spellapp.core.model.CharacterBuildOptionType
import com.spellapp.core.model.ClassChoice
import com.spellapp.core.model.ClassChoiceGroup
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.ClassSpellcastingDefinition
import com.spellapp.core.model.EmptyClassSpellcastingCatalogSource
import com.spellapp.core.model.InMemoryClassSpellcastingCatalogSource
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.PrimaryTrackDefinition
import com.spellapp.core.model.SpellAllowanceKind
import com.spellapp.core.model.SpellAllowancePolicy
import com.spellapp.core.model.SpellAllowanceRule
import com.spellapp.core.model.SpellcastingTradition
import com.spellapp.core.model.normalizeClassId
import org.json.JSONArray
import org.json.JSONObject

class AssetClassSpellcastingCatalogSource(
    context: Context,
    private val fallback: ClassSpellcastingCatalogSource = EmptyClassSpellcastingCatalogSource,
) : ClassSpellcastingCatalogSource {
    private val appContext = context.applicationContext
    private val delegate: ClassSpellcastingCatalogSource by lazy {
        val parsed = runCatching {
            appContext.assets
                .open(ASSET_FILE_NAME)
                .bufferedReader()
                .use { reader -> ClassSpellcastingCatalogJsonParser.parse(reader.readText()) }
        }.getOrNull()
        parsed?.takeIf { source -> source.allDefinitions().isNotEmpty() } ?: fallback
    }

    override fun allDefinitions(): List<ClassSpellcastingDefinition> =
        delegate.allDefinitions()

    override fun definitionFor(classId: String): ClassSpellcastingDefinition? =
        delegate.definitionFor(classId)

    private companion object {
        private const val ASSET_FILE_NAME = "class-spellcasting.normalized.json"
    }
}

object ClassSpellcastingCatalogJsonParser {
    fun parse(rawJson: String): ClassSpellcastingCatalogSource {
        val root = JSONObject(rawJson)
        val entries = root.optJSONArray("classes") ?: JSONArray()
        val definitions = mutableListOf<ClassSpellcastingDefinition>()
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val classId = normalizeClassId(entry.optString("id"))
            val keyAbilityOptions = parseEnumArray<AbilityScore>(entry.optJSONArray("keyAbilityOptions"))
            val defaultKeyAbility = parseEnum<AbilityScore>(entry.optString("defaultKeyAbility"))
                ?: keyAbilityOptions.firstOrNull()
                ?: AbilityScore.INTELLIGENCE
            definitions += ClassSpellcastingDefinition(
                classId = classId,
                label = entry.optString("name").ifBlank { classId },
                defaultKeyAbility = defaultKeyAbility,
                keyAbilityOptions = keyAbilityOptions.ifEmpty { listOf(defaultKeyAbility) },
                baseTradition = parseNullableEnum<SpellcastingTradition>(entry, "baseTradition"),
                primaryTracks = parsePrimaryTracks(entry.optJSONArray("primaryTracks")),
                choiceGroups = parseChoiceGroups(entry.optJSONArray("choiceGroups")),
            )
        }
        return InMemoryClassSpellcastingCatalogSource(definitions)
    }

    private fun parsePrimaryTracks(raw: JSONArray?): List<PrimaryTrackDefinition> {
        if (raw == null) {
            return emptyList()
        }
        val tracks = mutableListOf<PrimaryTrackDefinition>()
        for (index in 0 until raw.length()) {
            val entry = raw.optJSONObject(index) ?: continue
            tracks += PrimaryTrackDefinition(
                trackKey = entry.optString("trackKey").ifBlank { PreparedSlot.PRIMARY_TRACK_KEY },
                displayName = entry.optString("displayName"),
                progressionType = parseEnum<CastingProgressionType>(entry.optString("progressionType"))
                    ?: CastingProgressionType.FULL_PREPARED,
                castingStyle = parseEnum<CastingStyle>(entry.optString("castingStyle"))
                    ?: CastingStyle.PREPARED,
                tradition = parseNullableEnum<SpellcastingTradition>(entry, "tradition"),
                slotProgressionKey = entry.optString("slotProgressionKey"),
                slotsByLevel = parseSlotsByLevel(entry.optJSONObject("slotsByLevel")),
                allowanceRules = parseAllowanceRules(entry.optJSONArray("allowanceRules")),
            )
        }
        return tracks
    }

    private fun parseAllowanceRules(raw: JSONArray?): List<SpellAllowanceRule> {
        if (raw == null) {
            return emptyList()
        }
        val rules = mutableListOf<SpellAllowanceRule>()
        for (index in 0 until raw.length()) {
            val entry = raw.optJSONObject(index) ?: continue
            rules += SpellAllowanceRule(
                trackKey = entry.optString("trackKey").ifBlank { PreparedSlot.PRIMARY_TRACK_KEY },
                kind = parseEnum<SpellAllowanceKind>(entry.optString("kind"))
                    ?: SpellAllowanceKind.PREPARED_SLOTS,
                label = entry.optString("label"),
                policy = parseEnum<SpellAllowancePolicy>(entry.optString("policy"))
                    ?: SpellAllowancePolicy.WARNING_ONLY,
                countsByLevel = parseSlotsByLevel(entry.optJSONObject("countsByLevel")),
                totalsByLevel = parseTotalsByLevel(entry.optJSONObject("totalsByLevel")),
                source = entry.optString("source").ifBlank { null },
                note = entry.optString("note").ifBlank { null },
            )
        }
        return rules
    }

    private fun parseChoiceGroups(raw: JSONArray?): List<ClassChoiceGroup> {
        if (raw == null) {
            return emptyList()
        }
        val groups = mutableListOf<ClassChoiceGroup>()
        for (index in 0 until raw.length()) {
            val entry = raw.optJSONObject(index) ?: continue
            groups += ClassChoiceGroup(
                id = entry.optString("id"),
                label = entry.optString("label"),
                optionType = parseEnum<CharacterBuildOptionType>(entry.optString("optionType"))
                    ?: CharacterBuildOptionType.CLASS_FEATURE,
                required = entry.optBoolean("required", true),
                choices = parseChoices(entry.optJSONArray("choices")),
            )
        }
        return groups
    }

    private fun parseChoices(raw: JSONArray?): List<ClassChoice> {
        if (raw == null) {
            return emptyList()
        }
        val choices = mutableListOf<ClassChoice>()
        for (index in 0 until raw.length()) {
            val entry = raw.optJSONObject(index) ?: continue
            choices += ClassChoice(
                optionId = entry.optString("optionId"),
                label = entry.optString("label"),
                tradition = parseNullableEnum<SpellcastingTradition>(entry, "tradition"),
                keyAbility = parseNullableEnum<AbilityScore>(entry, "keyAbility"),
                grantedSpellNames = parseStringArray(entry.optJSONArray("grantedSpellNames")),
                focusSpellNames = parseStringArray(entry.optJSONArray("focusSpellNames")),
            )
        }
        return choices
    }

    private fun parseSlotsByLevel(raw: JSONObject?): Map<Int, Map<Int, Int>> {
        if (raw == null) {
            return emptyMap()
        }
        val levels = linkedMapOf<Int, Map<Int, Int>>()
        raw.keys().forEach { levelKey ->
            val rankObject = raw.optJSONObject(levelKey) ?: return@forEach
            val ranks = linkedMapOf<Int, Int>()
            rankObject.keys().forEach { rankKey ->
                ranks[rankKey.toIntOrNull() ?: return@forEach] = rankObject.optInt(rankKey)
            }
            levels[levelKey.toIntOrNull() ?: return@forEach] = ranks
        }
        return levels
    }

    private fun parseTotalsByLevel(raw: JSONObject?): Map<Int, Int> {
        if (raw == null) {
            return emptyMap()
        }
        val totals = linkedMapOf<Int, Int>()
        raw.keys().forEach { levelKey ->
            val level = levelKey.toIntOrNull() ?: return@forEach
            totals[level] = raw.optInt(levelKey)
        }
        return totals
    }

    private fun parseStringArray(raw: JSONArray?): List<String> {
        if (raw == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until raw.length()) {
                raw.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: String): T? {
        return value.trim().takeIf { it.isNotBlank() }?.let { normalized ->
            enumValues<T>().firstOrNull { enumValue ->
                enumValue.name.equals(normalized, ignoreCase = true)
            }
        }
    }

    private inline fun <reified T : Enum<T>> parseNullableEnum(
        entry: JSONObject,
        fieldName: String,
    ): T? {
        if (!entry.has(fieldName) || entry.isNull(fieldName)) {
            return null
        }
        return parseEnum<T>(entry.optString(fieldName))
    }

    private inline fun <reified T : Enum<T>> parseEnumArray(raw: JSONArray?): List<T> {
        if (raw == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until raw.length()) {
                parseEnum<T>(raw.optString(index))?.let(::add)
            }
        }
    }
}
