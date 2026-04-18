package com.spellapp.core.model

data class SpellRulesText(
    val text: String,
    val references: List<SpellRulesReference> = emptyList(),
)

data class SpellRulesReference(
    val key: RulesReferenceKey,
    val label: String,
    val start: Int,
    val end: Int,
)
