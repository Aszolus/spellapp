package com.spellapp.core.model

data class KnownSpell(
    val id: Long = 0L,
    val characterId: Long,
    val trackKey: String,
    val spellId: String,
)
