package com.spellapp.core.rules

import com.spellapp.core.model.SpellListItem

fun parseSpellTradition(rawTradition: String?): SpellTradition {
    return when (rawTradition?.trim()?.lowercase()) {
        "arcane" -> SpellTradition.ARCANE
        "divine" -> SpellTradition.DIVINE
        "occult" -> SpellTradition.OCCULT
        "primal" -> SpellTradition.PRIMAL
        else -> SpellTradition.OTHER
    }
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

fun DerivedCastingTrackState.isLegalPreparedSpell(spell: SpellListItem): Boolean {
    return isLegalPreparedSpell(
        spellId = spell.id,
        spellTradition = parseSpellTradition(spell.tradition),
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
