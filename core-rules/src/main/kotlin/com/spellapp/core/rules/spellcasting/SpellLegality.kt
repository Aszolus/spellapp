package com.spellapp.core.rules.spellcasting

import com.spellapp.core.model.SpellListItem

fun parseSpellTradition(rawTradition: String?): SpellTradition {
    return when (rawTradition?.trim()?.lowercase()?.removeSurrounding("\"")) {
        "arcane" -> SpellTradition.ARCANE
        "divine" -> SpellTradition.DIVINE
        "occult" -> SpellTradition.OCCULT
        "primal" -> SpellTradition.PRIMAL
        else -> SpellTradition.OTHER
    }
}

fun parseSpellTraditions(rawTraditionSummary: String?): Set<SpellTradition> {
    if (rawTraditionSummary.isNullOrBlank()) {
        return setOf(SpellTradition.OTHER)
    }

    val parts = rawTraditionSummary
        .split(',', ';', '|', '/')
        .map { part -> part.trim() }
        .filter { part -> part.isNotBlank() }

    if (parts.isEmpty()) {
        return setOf(SpellTradition.OTHER)
    }

    return parts
        .map { part -> parseSpellTradition(part) }
        .toSet()
}

fun DerivedCastingTrackState.isLegalPreparedSpell(
    spellId: String,
    spellTradition: SpellTradition,
): Boolean {
    return legalityProfile.isSpellLegal(
        spellId = spellId,
        spellTradition = spellTradition,
    )
}

fun DerivedCastingTrackState.isLegalPreparedSpell(
    spellId: String,
    spellTraditions: Set<SpellTradition>,
): Boolean {
    return legalityProfile.isSpellLegal(
        spellId = spellId,
        spellTraditions = spellTraditions,
    )
}

fun DerivedCastingTrackState.isLegalPreparedSpell(spell: SpellListItem): Boolean {
    return isLegalPreparedSpell(
        spellId = spell.id,
        spellTraditions = parseSpellTraditions(spell.tradition),
    )
}

fun DerivedCastingTrackState.filterLegalPreparedSpellTargets(
    spells: Iterable<SpellListItem>,
): List<SpellListItem> {
    return spells.filter { spell -> isLegalPreparedSpell(spell) }
}

fun buildInvalidTraditionWarning(
    track: DerivedCastingTrackState,
    spellId: String,
    spellTradition: SpellTradition,
    source: RuleSourceRef? = null,
): DerivationWarning {
    return DerivationWarning(
        code = CastingWarningCodes.INVALID_TRADITION_FOR_TRACK,
        message = "Spell '$spellId' with tradition '$spellTradition' is not legal for track '${track.trackKey}'.",
        source = source,
    )
}
