package com.spellapp.core.data.local

data class CatalogSpellDetailRow(
    val id: String,
    val name: String,
    val rank: Int,
    val traditionSummary: String,
    val rarity: String,
    val traitsCsv: String,
    val castTime: String,
    val rangeText: String,
    val targetText: String,
    val durationText: String,
    val areaText: String?,
    val defenseText: String?,
    val description: String,
    val license: String,
    val sourceBook: String,
    val sourcePageText: String?,
)
