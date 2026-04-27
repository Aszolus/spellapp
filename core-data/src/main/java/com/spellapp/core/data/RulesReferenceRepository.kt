package com.spellapp.core.data

import com.spellapp.core.model.RulesReferenceEntry
import com.spellapp.core.model.RulesReferenceKey

interface RulesReferenceRepository {
    suspend fun getEntry(key: RulesReferenceKey): RulesReferenceEntry?

    suspend fun getEntries(keys: Collection<RulesReferenceKey>): Map<RulesReferenceKey, RulesReferenceEntry> {
        return buildMap {
            keys.toSet().forEach { key ->
                getEntry(key)?.let { entry ->
                    put(key, entry)
                }
            }
        }
    }
}
