package com.spellapp.core.data.local

import com.spellapp.core.data.PerfTrace
import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.model.RulesTextDocument

class CatalogSpellRulesTextRepository(
    private val catalogDao: CatalogDao,
) : SpellRulesTextRepository {
    override suspend fun getSpellRulesText(
        spellId: String,
        spellRank: Int?,
    ): RulesTextDocument? {
        return PerfTrace.suspendSection("CatalogSpellRulesTextRepository.rulesText $spellId") {
            val row = runCatching { catalogDao.getSpellDetail(spellId) }.getOrNull() ?: return@suspendSection null
            RulesTextDocument.fromPlainText(row.description).takeIf { !it.isEmpty }
        }
    }

    override suspend fun getSpellHeightenedRulesText(
        spellId: String,
        spellRank: Int?,
    ): List<RulesTextDocument> {
        return PerfTrace.suspendSection("CatalogSpellRulesTextRepository.heightenedText $spellId") {
            val row = runCatching { catalogDao.getSpellDetail(spellId) }.getOrNull() ?: return@suspendSection emptyList()
            HeightenedRulesTextParser.parse(
                descriptionRaw = null,
                description = row.description,
                localizationResolver = null,
                itemLevel = spellRank,
                itemRank = spellRank,
            )
        }
    }
}
