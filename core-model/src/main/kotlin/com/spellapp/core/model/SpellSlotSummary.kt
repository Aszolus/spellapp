package com.spellapp.core.model

data class SpellSlotSummary(
    val spellId: String,
    val name: String,
    val castTime: String,
    val range: String,
    val traits: List<String>,
)
