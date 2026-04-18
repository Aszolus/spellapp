package com.spellapp.core.data

import com.spellapp.core.model.RulesTextDocument

interface SpellRulesTextRepository {
    suspend fun getSpellRulesText(
        spellId: String,
        spellRank: Int? = null,
    ): RulesTextDocument?
}
