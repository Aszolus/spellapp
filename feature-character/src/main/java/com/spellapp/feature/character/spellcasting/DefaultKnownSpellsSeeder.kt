package com.spellapp.feature.character.spellcasting

import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.PreparedSlot
import kotlinx.coroutines.flow.first

class DefaultKnownSpellsSeeder(
    private val spellRepository: SpellRepository,
    private val knownSpellRepository: KnownSpellRepository,
) {
    suspend fun seedForCharacter(
        character: CharacterProfile,
        acceptedSourceBooks: Set<String>,
    ) {
        val tradition = traditionFor(character.characterClass) ?: return
        val knownSpellIds = spellRepository.observeSpells(
            tradition = tradition,
            rarity = "common",
        ).first().filter { spell ->
            spell.sourceBook in acceptedSourceBooks
        }.map { it.id }

        knownSpellIds.forEach { spellId ->
            knownSpellRepository.addKnownSpell(
                characterId = character.id,
                trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                spellId = spellId,
            )
        }
    }

    private fun traditionFor(characterClass: CharacterClass): String? {
        return when (characterClass) {
            CharacterClass.CLERIC -> "divine"
            CharacterClass.DRUID -> "primal"
            CharacterClass.WIZARD,
            CharacterClass.OTHER -> null
        }
    }
}
