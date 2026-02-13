package com.spellapp.core.data

import com.spellapp.core.model.SpellListItem

interface SpellRepository {
    suspend fun getAllSpells(): List<SpellListItem>
}
