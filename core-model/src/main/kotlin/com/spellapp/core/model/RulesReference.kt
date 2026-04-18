package com.spellapp.core.model

enum class RulesReferenceCategory {
    TRAIT,
    CONDITION,
}

data class RulesReferenceKey(
    val category: RulesReferenceCategory,
    val slug: String,
)

data class RulesReferenceEntry(
    val key: RulesReferenceKey,
    val label: String,
    val description: String,
)
