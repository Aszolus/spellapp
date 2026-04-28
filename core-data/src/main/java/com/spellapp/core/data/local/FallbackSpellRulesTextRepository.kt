package com.spellapp.core.data.local

import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.model.RulesTextDocument

class FallbackSpellRulesTextRepository(
    private val primary: SpellRulesTextRepository,
    private val fallback: SpellRulesTextRepository,
) : SpellRulesTextRepository {
    override suspend fun getSpellRulesText(
        spellId: String,
        spellRank: Int?,
    ): RulesTextDocument? {
        return runCatching { primary.getSpellRulesText(spellId, spellRank) }.getOrNull()
            ?: fallback.getSpellRulesText(spellId, spellRank)
    }

    override suspend fun getSpellHeightenedRulesText(
        spellId: String,
        spellRank: Int?,
    ): List<RulesTextDocument> {
        val primaryResult = runCatching {
            primary.getSpellHeightenedRulesText(spellId, spellRank)
        }.getOrDefault(emptyList())
        return primaryResult.ifEmpty {
            fallback.getSpellHeightenedRulesText(spellId, spellRank)
        }
    }
}
