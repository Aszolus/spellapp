package com.spellapp.core.data.local

internal fun normalizeRulesReferenceSlug(value: String): String {
    return value.trim()
        .lowercase()
        .replace(Regex("[\\s_]+"), "-")
        .replace(Regex("[^a-z0-9\\-]"), "")
        .replace(Regex("-+"), "-")
        .trim('-')
}
