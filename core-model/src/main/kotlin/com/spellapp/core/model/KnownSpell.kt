package com.spellapp.core.model

data class KnownSpell(
    val id: Long = 0L,
    val characterId: Long,
    val trackKey: String,
    val spellId: String,
    val knownRank: Int? = null,
    val origin: KnownSpellOrigin = KnownSpellOrigin.MANUAL,
    val isLocked: Boolean = false,
    val isSignature: Boolean = false,
)

enum class KnownSpellOrigin {
    MANUAL,
    CLASS,
    SUBCLASS,
    ARCHETYPE,
}
