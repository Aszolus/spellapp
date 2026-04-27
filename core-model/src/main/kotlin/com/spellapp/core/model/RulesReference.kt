package com.spellapp.core.model

enum class RulesReferenceCategory {
    TRAIT,
    CONDITION,
    ACTION,
    SPELL_EFFECT,
    SPELL,
    FEAT,
    ITEM,
    UNKNOWN,
    ;

    companion object {
        fun fromPackName(packName: String?): RulesReferenceCategory {
            return when (packName?.trim()?.lowercase()) {
                "conditionitems" -> CONDITION
                "actionspf2e" -> ACTION
                "spell-effects" -> SPELL_EFFECT
                "spells-srd" -> SPELL
                "feats-srd" -> FEAT
                "equipment-srd" -> ITEM
                else -> UNKNOWN
            }
        }
    }
}

sealed interface RulesReferenceKey {
    val category: RulesReferenceCategory
}

data class TraitReferenceKey private constructor(
    val slug: String,
) : RulesReferenceKey {
    override val category: RulesReferenceCategory = RulesReferenceCategory.TRAIT

    companion object {
        fun fromSlug(slug: String): TraitReferenceKey = TraitReferenceKey(
            slug = normalizeRulesReferenceSlug(slug),
        )
    }
}

data class CompendiumReferenceKey private constructor(
    val uuid: String,
    override val category: RulesReferenceCategory,
) : RulesReferenceKey {
    companion object {
        fun fromUuid(uuid: String): CompendiumReferenceKey {
            val normalizedUuid = normalizeCompendiumReferenceUuid(uuid)
            val packName = extractCompendiumPackName(normalizedUuid)
            return CompendiumReferenceKey(
                uuid = normalizedUuid,
                category = RulesReferenceCategory.fromPackName(packName),
            )
        }
    }
}

data class RulesReferenceEntry(
    val key: RulesReferenceKey,
    val label: String,
    val document: RulesTextDocument,
    val type: String? = null,
)

fun extractCompendiumPackName(uuid: String): String? {
    val parts = normalizeCompendiumReferenceUuid(uuid).split('.')
    return if (parts.size >= 4 && parts[0] == "compendium" && parts[1] == "pf2e") {
        parts[2]
    } else {
        null
    }
}
