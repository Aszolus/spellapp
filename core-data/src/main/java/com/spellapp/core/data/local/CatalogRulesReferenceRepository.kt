package com.spellapp.core.data.local

import com.spellapp.core.data.RulesReferenceRepository
import com.spellapp.core.data.local.foundry.FoundryMarkupParser
import com.spellapp.core.model.CompendiumReferenceKey
import com.spellapp.core.model.RulesReferenceEntry
import com.spellapp.core.model.RulesReferenceKey
import com.spellapp.core.model.TraitReferenceKey

class CatalogRulesReferenceRepository(
    private val catalogDao: CatalogDao,
    private val fallback: RulesReferenceRepository,
) : RulesReferenceRepository {
    override suspend fun getEntry(key: RulesReferenceKey): RulesReferenceEntry? {
        return when (key) {
            is TraitReferenceKey -> fallback.getEntry(key)
            is CompendiumReferenceKey -> catalogEntry(key) ?: fallback.getEntry(key)
        }
    }

    override suspend fun getEntries(keys: Collection<RulesReferenceKey>): Map<RulesReferenceKey, RulesReferenceEntry> {
        return buildMap {
            keys.toSet().forEach { key ->
                getEntry(key)?.let { entry -> put(key, entry) }
            }
        }
    }

    private suspend fun catalogEntry(key: CompendiumReferenceKey): RulesReferenceEntry? {
        val row = catalogDao.getCatalogRecordDetail(key.uuid) ?: return null
        return RulesReferenceEntry(
            key = key,
            label = row.name,
            document = FoundryMarkupParser.parse(
                descriptionRaw = null,
                description = row.detailText,
                localizationResolver = null,
            ),
            type = row.recordType,
        )
    }
}
