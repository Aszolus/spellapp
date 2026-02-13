package com.spellapp.core.model

data class SpellDetail(
    val id: String,
    val name: String,
    val rank: Int,
    val tradition: String,
    val rarity: String,
    val traits: List<String>,
    val castTime: String,
    val range: String,
    val target: String,
    val duration: String,
    val description: String,
    val license: String,
    val sourceBook: String,
    val sourcePage: Int?,
)
