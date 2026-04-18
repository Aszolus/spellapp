package com.spellapp.feature.character.spellcasting

import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SessionEventType

class CastLayOnHandsUseCase(
    private val characterBuildRepository: CharacterBuildRepository,
    private val focusStateRepository: FocusStateRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val spellcastingSupportService: SpellcastingSupportService,
) {
    suspend fun cast(
        characterId: Long,
        trackKey: String,
    ): Boolean {
        val hasBlessedOneDedication = characterBuildRepository.getBuildOptions(characterId)
            .any { option ->
                option.optionId.equals(BLESSED_ONE_DEDICATION_OPTION_ID, ignoreCase = true)
            }
        if (!hasBlessedOneDedication) {
            return false
        }

        val current = spellcastingSupportService.currentFocusState(characterId)
        if (current.currentPoints <= 0) {
            return false
        }

        focusStateRepository.upsertFocusState(
            current.copy(currentPoints = current.currentPoints - 1),
        )
        sessionEventRepository.appendSessionEvent(
            SessionEvent(
                characterId = characterId,
                type = SessionEventType.CAST_FOCUS_SPELL,
                spellId = spellcastingSupportService.resolveLayOnHandsSpellId(),
                metadataJson = spellcastingSupportService.metadataForTrack(trackKey),
            ),
        )
        return true
    }

    private companion object {
        private const val BLESSED_ONE_DEDICATION_OPTION_ID = "archetype/blessed-one/blessed-one-dedication"
    }
}
