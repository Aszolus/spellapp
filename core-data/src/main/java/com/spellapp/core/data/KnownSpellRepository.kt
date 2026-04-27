package com.spellapp.core.data

import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.KnownSpellOrigin
import kotlinx.coroutines.flow.Flow

interface KnownSpellRepository {
    fun observeKnownSpells(
        characterId: Long,
        trackKey: String,
    ): Flow<List<KnownSpell>>

    fun observeKnownSpellIds(
        characterId: Long,
        trackKey: String,
    ): Flow<Set<String>>

    suspend fun addKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int? = null,
        origin: KnownSpellOrigin = KnownSpellOrigin.MANUAL,
        isLocked: Boolean = false,
        isSignature: Boolean = false,
    ): Long

    suspend fun removeKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int? = null,
    ): Boolean

    suspend fun setSignatureSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        isSignature: Boolean,
        knownRank: Int? = null,
    ): Boolean = false

    suspend fun isKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int? = null,
    ): Boolean
}
