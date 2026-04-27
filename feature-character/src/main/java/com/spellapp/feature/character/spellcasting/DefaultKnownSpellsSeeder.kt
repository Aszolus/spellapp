package com.spellapp.feature.character.spellcasting

import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CastingStyle
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.EmptyClassSpellcastingCatalogSource
import com.spellapp.core.model.KnownSpellOrigin
import com.spellapp.core.model.SpellListItem
import com.spellapp.core.model.preferredTraditionString
import com.spellapp.core.model.selectedChoices
import com.spellapp.core.model.traditionFor
import kotlinx.coroutines.flow.first

class DefaultKnownSpellsSeeder(
    private val spellRepository: SpellRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        EmptyClassSpellcastingCatalogSource,
) {
    suspend fun seedForCharacter(
        character: CharacterProfile,
        selectedBuildOptionIds: Set<String>,
        acceptedSourceBooks: Set<String>,
    ) {
        val definition = classSpellcastingCatalogSource.definitionFor(character.characterClass) ?: return
        val selectedChoices = classSpellcastingCatalogSource.selectedChoices(
            characterClass = character.characterClass,
            selectedOptionIds = selectedBuildOptionIds,
        )
        val traditionOverride = classSpellcastingCatalogSource.traditionFor(
            characterClass = character.characterClass,
            selectedOptionIds = selectedBuildOptionIds,
        )

        definition.primaryTracks.forEach { track ->
            val tradition = (traditionOverride ?: track.tradition).preferredTraditionString()
                ?: return@forEach
            if (track.castingStyle == CastingStyle.PREPARED) {
                seedPreparedTradition(
                    character = character,
                    trackKey = track.trackKey,
                    tradition = tradition,
                    acceptedSourceBooks = acceptedSourceBooks,
                )
            }
        }

        val grantedSpellNames = selectedChoices.flatMap { choice -> choice.grantedSpellNames }.distinct()
        if (grantedSpellNames.isEmpty()) {
            return
        }
        val grantTrackKey = definition.primaryTracks
            .firstOrNull { track -> track.castingStyle == CastingStyle.SPONTANEOUS }
            ?.trackKey
            ?: definition.primaryTracks.firstOrNull()?.trackKey
            ?: return
        grantedSpellNames.forEach { spellName ->
            val spell = resolveSpellByName(
                name = spellName,
                acceptedSourceBooks = acceptedSourceBooks,
            ) ?: return@forEach
            knownSpellRepository.addKnownSpell(
                characterId = character.id,
                trackKey = grantTrackKey,
                spellId = spell.id,
                knownRank = spell.rank,
                origin = KnownSpellOrigin.SUBCLASS,
                isLocked = true,
            )
        }
    }

    private suspend fun seedPreparedTradition(
        character: CharacterProfile,
        trackKey: String,
        tradition: String,
        acceptedSourceBooks: Set<String>,
    ) {
        val knownSpells = spellRepository.observeSpells(
            tradition = tradition,
            rarity = "common",
        ).first().filter { spell ->
            spell.sourceBook in acceptedSourceBooks
        }

        knownSpells.forEach { spell ->
            knownSpellRepository.addKnownSpell(
                characterId = character.id,
                trackKey = trackKey,
                spellId = spell.id,
                knownRank = spell.rank,
                origin = KnownSpellOrigin.CLASS,
            )
        }
    }

    private suspend fun resolveSpellByName(
        name: String,
        acceptedSourceBooks: Set<String>,
    ): SpellListItem? {
        val matches = spellRepository.observeSpells(query = name).first()
        return matches.firstOrNull { spell ->
            spell.name.equals(name, ignoreCase = true) &&
                (acceptedSourceBooks.isEmpty() || spell.sourceBook in acceptedSourceBooks)
        } ?: matches.firstOrNull { spell ->
            spell.name.equals(name, ignoreCase = true)
        }
    }
}
