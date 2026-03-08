package com.spellapp.feature.spells

import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.SpellListItem

class AssignPreparedSpellUseCase(
    private val characterCrudRepository: CharacterCrudRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val preparedSlotRepository: PreparedSlotRepository,
    private val spellRepository: SpellRepository,
    private val assignmentPolicy: PreparedSpellAssignmentPolicy = DefaultPreparedSpellAssignmentPolicy(),
) {
    suspend fun assign(
        mode: SpellBrowserMode.AssignPreparedSlot,
        spellId: String,
    ): Boolean {
        if (!knownSpellRepository.isKnownSpell(mode.characterId, mode.trackKey, spellId)) {
            return false
        }

        val character = characterCrudRepository.getCharacter(mode.characterId) ?: return false
        val detail = spellRepository.getSpellDetail(spellId) ?: return false
        val spell = SpellListItem(
            id = detail.id,
            name = detail.name,
            rank = detail.rank,
            tradition = detail.tradition,
            rarity = detail.rarity,
            sourceBook = detail.sourceBook,
            isCantrip = detail.rank == 0,
        )
        val isLegal = assignmentPolicy.isSpellLegalTarget(
            spell = spell,
            context = PreparedSlotAssignmentContext(
                characterClass = character.characterClass,
                trackKey = mode.trackKey,
                slotRank = mode.slotRank,
            ),
        )
        if (!isLegal) {
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
}
