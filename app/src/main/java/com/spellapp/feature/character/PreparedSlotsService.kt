package com.spellapp.feature.character

import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SessionEventType
import kotlinx.coroutines.flow.Flow

class PreparedSlotsService(
    private val characterRepository: CharacterRepository,
    private val spellRepository: SpellRepository,
    private val trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
) {
    fun observePreparedSlots(characterId: Long): Flow<List<PreparedSlot>> {
        return characterRepository.observePreparedSlots(
            characterId = characterId,
            trackKey = trackKey,
        )
    }

    fun observeSessionEvents(characterId: Long): Flow<List<SessionEvent>> {
        return characterRepository.observeSessionEvents(characterId)
    }

    suspend fun resolveSpellNames(
        preparedSlots: List<PreparedSlot>,
        sessionEvents: List<SessionEvent>,
    ): Map<String, String> {
        val ids = (preparedSlots.mapNotNull { it.preparedSpellId } + sessionEvents.mapNotNull { it.spellId })
            .distinct()
        return ids.associateWith { spellId ->
            spellRepository.getSpellDetail(spellId)?.name ?: spellId
        }
    }

    fun formatRecentEventLines(
        sessionEvents: List<SessionEvent>,
        spellNameById: Map<String, String>,
        limit: Int = 8,
    ): List<String> {
        return sessionEvents
            .take(limit)
            .map { event -> formatSessionEventLine(event, spellNameById) }
    }

    suspend fun addSlot(
        characterId: Long,
        rank: Int,
    ) {
        characterRepository.addPreparedSlot(
            characterId = characterId,
            rank = rank,
            trackKey = trackKey,
        )
    }

    suspend fun removeSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
    ) {
        characterRepository.removePreparedSlot(
            characterId = characterId,
            rank = rank,
            slotIndex = slotIndex,
            trackKey = trackKey,
        )
    }

    suspend fun clearSpell(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
    ) {
        characterRepository.clearPreparedSlotSpell(
            characterId = characterId,
            rank = rank,
            slotIndex = slotIndex,
            trackKey = trackKey,
        )
    }

    suspend fun castSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
    ): Boolean {
        return characterRepository.castPreparedSlot(
            characterId = characterId,
            rank = rank,
            slotIndex = slotIndex,
            trackKey = trackKey,
        )
    }

    suspend fun undoLastCast(characterId: Long): Boolean {
        return characterRepository.undoLastCast(characterId)
    }

    private fun formatSessionEventLine(
        event: SessionEvent,
        spellNameById: Map<String, String>,
    ): String {
        val spellName = event.spellId?.let { spellNameById[it] ?: it } ?: "Unknown Spell"
        return when (event.type) {
            SessionEventType.CAST_SPELL -> {
                val rankLabel = if (event.spellRank == 0) "Cantrip" else "Rank ${event.spellRank ?: "?"}"
                "Cast $rankLabel: $spellName"
            }
            SessionEventType.UNDO_LAST_ACTION -> "Undo last action"
            SessionEventType.REFOCUS -> "Refocus"
            SessionEventType.REST -> "Rest"
            SessionEventType.NEW_DAY_PREPARATION -> "New day preparation"
        }
    }
}
