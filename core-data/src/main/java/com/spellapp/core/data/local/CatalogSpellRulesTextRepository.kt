package com.spellapp.core.data.local

import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.model.RulesTextDocument

class CatalogSpellRulesTextRepository(
    private val catalogDao: CatalogDao,
) : SpellRulesTextRepository {
    override suspend fun getSpellRulesText(
        spellId: String,
        spellRank: Int?,
    ): RulesTextDocument? {
        val row = runCatching { catalogDao.getSpellDetail(spellId) }.getOrNull() ?: return null
        return RulesTextDocument.fromPlainText(row.description).takeIf { !it.isEmpty }
    }

    override suspend fun getSpellHeightenedRulesText(
        spellId: String,
        spellRank: Int?,
    ): List<RulesTextDocument> {
        val row = runCatching { catalogDao.getSpellDetail(spellId) }.getOrNull() ?: return emptyList()
        return HeightenedRulesTextParser.parse(
            descriptionRaw = null,
            description = row.description,
            localizationResolver = null,
            itemLevel = spellRank,
            itemRank = spellRank,
        )
    }
}
