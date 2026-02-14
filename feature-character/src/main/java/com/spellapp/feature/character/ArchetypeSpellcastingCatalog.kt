package com.spellapp.feature.character

import android.content.Context
import com.spellapp.core.model.CharacterBuildOptionType
import org.json.JSONObject

data class ArchetypeSpellcastingPackage(
    val archetypeId: String,
    val label: String,
    val dedicationOptionId: String,
    val basicSpellcastingOptionId: String?,
    val expertSpellcastingOptionId: String?,
    val masterSpellcastingOptionId: String?,
)

interface ArchetypeSpellcastingCatalogSource {
    fun phaseOnePackages(): List<ArchetypeSpellcastingPackage>
    fun managedOptionIds(): Set<String>
    fun optionTypeForOptionId(optionId: String): CharacterBuildOptionType?
}

object StaticArchetypeSpellcastingCatalogSource : ArchetypeSpellcastingCatalogSource {
    private val packages = listOf(
        ArchetypeSpellcastingPackage(
            archetypeId = "wizard",
            label = "Wizard",
            dedicationOptionId = "archetype/wizard/wizard-dedication",
            basicSpellcastingOptionId = "archetype/wizard/basic-wizard-spellcasting",
            expertSpellcastingOptionId = "archetype/wizard/expert-wizard-spellcasting",
            masterSpellcastingOptionId = "archetype/wizard/master-wizard-spellcasting",
        ),
        ArchetypeSpellcastingPackage(
            archetypeId = "cleric",
            label = "Cleric",
            dedicationOptionId = "archetype/cleric/cleric-dedication",
            basicSpellcastingOptionId = "archetype/cleric/basic-cleric-spellcasting",
            expertSpellcastingOptionId = "archetype/cleric/expert-cleric-spellcasting",
            masterSpellcastingOptionId = "archetype/cleric/master-cleric-spellcasting",
        ),
        ArchetypeSpellcastingPackage(
            archetypeId = "druid",
            label = "Druid",
            dedicationOptionId = "archetype/druid/druid-dedication",
            basicSpellcastingOptionId = "archetype/druid/basic-druid-spellcasting",
            expertSpellcastingOptionId = "archetype/druid/expert-druid-spellcasting",
            masterSpellcastingOptionId = "archetype/druid/master-druid-spellcasting",
        ),
    )
    private val optionTypeById: Map<String, CharacterBuildOptionType> = packages
        .flatMap { pack ->
            listOfNotNull(
                pack.dedicationOptionId to CharacterBuildOptionType.ARCHETYPE,
                pack.basicSpellcastingOptionId
                    ?.let { optionId -> optionId to CharacterBuildOptionType.FEAT },
                pack.expertSpellcastingOptionId
                    ?.let { optionId -> optionId to CharacterBuildOptionType.FEAT },
                pack.masterSpellcastingOptionId
                    ?.let { optionId -> optionId to CharacterBuildOptionType.FEAT },
            )
        }
        .toMap()

    override fun phaseOnePackages(): List<ArchetypeSpellcastingPackage> = packages

    override fun managedOptionIds(): Set<String> = optionTypeById.keys

    override fun optionTypeForOptionId(optionId: String): CharacterBuildOptionType? {
        return optionTypeById[optionId]
    }
}

class AssetArchetypeSpellcastingCatalogSource(
    context: Context,
    private val fallback: ArchetypeSpellcastingCatalogSource = StaticArchetypeSpellcastingCatalogSource,
) : ArchetypeSpellcastingCatalogSource {
    private val appContext = context.applicationContext

    private val packages: List<ArchetypeSpellcastingPackage> by lazy {
        val parsed = runCatching { parsePackagesFromAsset() }.getOrDefault(emptyList())
        if (parsed.isEmpty()) {
            fallback.phaseOnePackages()
        } else {
            parsed
        }
    }
    private val optionTypeById: Map<String, CharacterBuildOptionType> by lazy {
        packages
            .flatMap { pack ->
                listOfNotNull(
                    pack.dedicationOptionId to CharacterBuildOptionType.ARCHETYPE,
                    pack.basicSpellcastingOptionId
                        ?.let { optionId -> optionId to CharacterBuildOptionType.FEAT },
                    pack.expertSpellcastingOptionId
                        ?.let { optionId -> optionId to CharacterBuildOptionType.FEAT },
                    pack.masterSpellcastingOptionId
                        ?.let { optionId -> optionId to CharacterBuildOptionType.FEAT },
                )
            }
            .toMap()
    }

    override fun phaseOnePackages(): List<ArchetypeSpellcastingPackage> = packages

    override fun managedOptionIds(): Set<String> = optionTypeById.keys

    override fun optionTypeForOptionId(optionId: String): CharacterBuildOptionType? {
        return optionTypeById[optionId]
    }

    private fun parsePackagesFromAsset(): List<ArchetypeSpellcastingPackage> {
        val rawJson = appContext.assets
            .open(RULES_ASSET_FILE_NAME)
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(rawJson)
        val options = root.optJSONArray("options") ?: return emptyList()
        val supportedIds = setOf("wizard", "cleric", "druid")
        val grouped = linkedMapOf<String, MutableList<RuleOption>>()

        for (index in 0 until options.length()) {
            val option = options.optJSONObject(index) ?: continue
            if (option.optString("sourceType") != "feat") {
                continue
            }
            val optionId = option.optString("optionId").trim().lowercase()
            val archetypeId = optionId.split('/').let { parts ->
                if (parts.size >= 3 && parts[0] == "archetype") {
                    parts[1]
                } else {
                    null
                }
            } ?: continue
            if (archetypeId !in supportedIds) {
                continue
            }
            val name = option.optString("name").trim()
            val traits = parseStringArray(option.optJSONArray("traits"))
            grouped.getOrPut(archetypeId) { mutableListOf() }
                .add(
                    RuleOption(
                        optionId = optionId,
                        name = name,
                        traits = traits,
                    ),
                )
        }

        val order = listOf("wizard", "cleric", "druid")
        return order.mapNotNull { archetypeId ->
            val feats = grouped[archetypeId].orEmpty()
            val dedication = feats.firstOrNull { feat ->
                feat.name.endsWith(" Dedication") &&
                    (feat.traits.contains("dedication") || feat.optionId.endsWith("-dedication"))
            } ?: return@mapNotNull null

            val basic = feats.firstOrNull { feat -> feat.name.startsWith("Basic ") && feat.name.contains("Spellcasting") }
            val expert = feats.firstOrNull { feat -> feat.name.startsWith("Expert ") && feat.name.contains("Spellcasting") }
            val master = feats.firstOrNull { feat -> feat.name.startsWith("Master ") && feat.name.contains("Spellcasting") }
            val label = dedication.name.removeSuffix(" Dedication").ifBlank { archetypeId.replaceFirstChar { it.uppercase() } }

            ArchetypeSpellcastingPackage(
                archetypeId = archetypeId,
                label = label,
                dedicationOptionId = dedication.optionId,
                basicSpellcastingOptionId = basic?.optionId,
                expertSpellcastingOptionId = expert?.optionId,
                masterSpellcastingOptionId = master?.optionId,
            )
        }
    }

    private fun parseStringArray(raw: org.json.JSONArray?): Set<String> {
        if (raw == null) {
            return emptySet()
        }
        val values = linkedSetOf<String>()
        for (index in 0 until raw.length()) {
            val value = raw.optString(index).trim().lowercase()
            if (value.isNotBlank()) {
                values += value
            }
        }
        return values
    }

    private data class RuleOption(
        val optionId: String,
        val name: String,
        val traits: Set<String>,
    )

    private companion object {
        private const val RULES_ASSET_FILE_NAME = "rules.catalog.normalized.json"
    }
}
