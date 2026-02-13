package com.spellapp.core.model

data class CharacterProfile(
    val id: Long = 0L,
    val name: String,
    val level: Int,
    val characterClass: CharacterClass,
    val keyAbility: AbilityScore,
    val spellDc: Int,
    val spellAttackModifier: Int,
    val legacyTerminologyEnabled: Boolean = false,
)
