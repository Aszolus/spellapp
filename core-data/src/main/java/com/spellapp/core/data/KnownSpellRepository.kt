package com.spellapp.core.data

import com.spellapp.core.model.KnownSpell
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
    ): Long

    suspend fun removeKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
    ): Boolean

    suspend fun isKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
    ): Boolean
}
