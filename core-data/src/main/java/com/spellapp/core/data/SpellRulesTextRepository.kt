package com.spellapp.core.data

import com.spellapp.core.model.SpellRulesText

interface SpellRulesTextRepository {
    suspend fun getSpellRulesText(spellId: String): SpellRulesText?
}
