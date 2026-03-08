package com.spellapp.feature.spells

import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.SpellRepository

class ToggleKnownSpellUseCase(
    private val knownSpellRepository: KnownSpellRepository,
    private val spellRepository: SpellRepository,
    private val warningPolicy: KnownSpellWarningPolicy,
) {
    suspend fun toggle(
        mode: SpellBrowserMode.ManageKnownSpells,
        spellId: String,
    ): PendingKnownSpellWarning? {
        val isKnown = knownSpellRepository.isKnownSpell(
            characterId = mode.characterId,
            trackKey = mode.trackKey,
            spellId = spellId,
        )
        if (isKnown) {
            knownSpellRepository.removeKnownSpell(
                characterId = mode.characterId,
                trackKey = mode.trackKey,
                spellId = spellId,
            )
            return null
        }
        val warning = spellRepository.getSpellDetail(spellId)?.let { detail ->
            warningPolicy.warningFor(
                detail = detail,
                preferredTradition = mode.preferredTradition,
                trackSourceId = mode.trackSourceId,
            )
        }
        if (warning != null) {
            return warning
        }
        knownSpellRepository.addKnownSpell(
            characterId = mode.characterId,
            trackKey = mode.trackKey,
            spellId = spellId,
        )
        return null
    }

    suspend fun confirmAdd(
        mode: SpellBrowserMode.ManageKnownSpells,
        spellId: String,
    ) {
        knownSpellRepository.addKnownSpell(
            characterId = mode.characterId,
            trackKey = mode.trackKey,
            spellId = spellId,
        )
    }
}
