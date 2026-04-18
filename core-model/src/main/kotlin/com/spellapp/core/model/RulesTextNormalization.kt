package com.spellapp.core.model

private val nonAlphaNumericRegex = Regex("[^a-z0-9\\-]")
private val repeatedDashRegex = Regex("-+")
private val whitespaceOrUnderscoreRegex = Regex("[\\s_]+")

fun normalizeRulesReferenceSlug(value: String): String {
    return value.trim()
        .lowercase()
        .replace(whitespaceOrUnderscoreRegex, "-")
        .replace(nonAlphaNumericRegex, "")
        .replace(repeatedDashRegex, "-")
        .trim('-')
}

fun normalizeCompendiumReferenceUuid(value: String): String {
    return value.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase()
}
