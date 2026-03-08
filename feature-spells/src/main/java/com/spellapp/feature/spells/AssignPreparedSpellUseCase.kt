package com.spellapp.feature.spells

import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.SpellRepository

class AssignPreparedSpellUseCase(
    private val knownSpellRepository: KnownSpellRepository,
    private val preparedSlotRepository: PreparedSlotRepository,
    private val spellRepository: SpellRepository,
) {
    suspend fun assign(
        mode: SpellBrowserMode.AssignPreparedSlot,
        spellId: String,
    ): Boolean {
        if (!knownSpellRepository.isKnownSpell(mode.characterId, mode.trackKey, spellId)) {
            return false
        }

        val detail = spellRepository.getSpellDetail(spellId) ?: return false
        if (!isPreparedRankCompatible(detail.rank, mode.slotRank)) {
            return false
        }

        preparedSlotRepository.assignSpellToPreparedSlot(
            characterId = mode.characterId,
            rank = mode.slotRank,
            slotIndex = mode.slotIndex,
            spellId = spellId,
            trackKey = mode.trackKey,
        )
        return true
    }

    private fun isPreparedRankCompatible(
        spellRank: Int,
        slotRank: Int,
    ): Boolean {
        if (slotRank <= 0) {
            return spellRank == 0
        }
        if (spellRank <= 0) {
            return false
        }
        return spellRank <= slotRank
    }
}
