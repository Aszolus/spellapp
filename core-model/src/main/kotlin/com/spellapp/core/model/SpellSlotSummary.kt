package com.spellapp.core.model

data class SpellSlotSummary(
    val spellId: String,
    val name: String,
    val rank: Int,
    val castTime: String,
    val range: String,
    val area: String = "",
    val target: String = "",
    val defense: String = "",
    val duration: String = "",
    val description: String = "",
    val traits: List<String>,
    val heightenedEntries: List<HeightenedEntry> = emptyList(),
)
