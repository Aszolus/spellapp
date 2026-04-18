package com.spellapp.core.data.local.foundry

import com.spellapp.core.model.CompendiumReferenceKey
import com.spellapp.core.model.RulesReferenceCategory
import com.spellapp.core.model.extractCompendiumPackName

internal fun classifyReferenceCategory(uuid: String): RulesReferenceCategory {
    return RulesReferenceCategory.fromPackName(extractCompendiumPackName(uuid))
}

internal fun labelFromUuid(uuid: String): String {
    return uuid.substringAfterLast('.')
        .replace('_', ' ')
        .trim()
}

internal fun compendiumReferenceKey(uuid: String): CompendiumReferenceKey {
    return CompendiumReferenceKey.fromUuid(uuid)
}

internal fun isConditionReference(uuid: String): Boolean {
    return classifyReferenceCategory(uuid) == RulesReferenceCategory.CONDITION
}
