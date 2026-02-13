package com.spellapp.feature.character

import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.FocusState
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SessionEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PreparedSlotsService(
    private val preparedSlotRepository: PreparedSlotRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val focusStateRepository: FocusStateRepository,
    private val spellRepository: SpellRepository,
    private val trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
) {
    fun observePreparedSlots(characterId: Long): Flow<List<PreparedSlot>> {
        return preparedSlotRepository.observePreparedSlots(
            characterId = characterId,
            trackKey = trackKey,
        )
    }

    fun observeSessionEvents(characterId: Long): Flow<List<SessionEvent>> {
        return sessionEventRepository.observeSessionEvents(
            characterId = characterId,
            trackKey = trackKey,
        )
    }

    fun observeFocusState(characterId: Long): Flow<FocusState> {
        return focusStateRepository.observeFocusState(characterId).map { state ->
            state ?: FocusState(
                characterId = characterId,
                currentPoints = 0,
                maxPoints = 1,
            )
        }
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
        preparedSlotRepository.addPreparedSlot(
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
        preparedSlotRepository.removePreparedSlot(
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
        preparedSlotRepository.clearPreparedSlotSpell(
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
        return preparedSlotRepository.castPreparedSlot(
            characterId = characterId,
            rank = rank,
            slotIndex = slotIndex,
            trackKey = trackKey,
        )
    }

    suspend fun undoLastCast(characterId: Long): Boolean {
        return preparedSlotRepository.undoLastCast(
            characterId = characterId,
            trackKey = trackKey,
        )
    }

    suspend fun useFocusPoint(characterId: Long): Boolean {
        val current = currentFocusState(characterId)
        if (current.currentPoints <= 0) {
            return false
        }
        focusStateRepository.upsertFocusState(
            current.copy(currentPoints = current.currentPoints - 1),
        )
        return true
    }

    suspend fun increaseFocusMax(characterId: Long): FocusState {
        val current = currentFocusState(characterId)
        val newMax = (current.maxPoints + 1).coerceAtMost(MAX_FOCUS_POINTS)
        val updated = current.copy(
            currentPoints = current.currentPoints.coerceAtMost(newMax),
            maxPoints = newMax,
        )
        focusStateRepository.upsertFocusState(updated)
        return updated
    }

    suspend fun decreaseFocusMax(characterId: Long): FocusState {
        val current = currentFocusState(characterId)
        val newMax = (current.maxPoints - 1).coerceAtLeast(0)
        val updated = current.copy(
            currentPoints = current.currentPoints.coerceAtMost(newMax),
            maxPoints = newMax,
        )
        focusStateRepository.upsertFocusState(updated)
        return updated
    }

    suspend fun refocus(characterId: Long): Boolean {
        val current = currentFocusState(characterId)
        val updated = current.copy(
            currentPoints = (current.currentPoints + 1).coerceAtMost(current.maxPoints),
        )
        focusStateRepository.upsertFocusState(updated)
        sessionEventRepository.appendSessionEvent(
            SessionEvent(
                characterId = characterId,
                type = SessionEventType.REFOCUS,
                metadataJson = metadataForTrack(trackKey),
            ),
        )
        return true
    }

    suspend fun rest(characterId: Long): Boolean {
        val current = currentFocusState(characterId)
        focusStateRepository.upsertFocusState(
            current.copy(currentPoints = current.maxPoints),
        )
        sessionEventRepository.appendSessionEvent(
            SessionEvent(
                characterId = characterId,
                type = SessionEventType.REST,
                metadataJson = metadataForTrack(trackKey),
            ),
        )
        return true
    }

    suspend fun newDayPreparation(characterId: Long): Boolean {
        val current = currentFocusState(characterId)
        preparedSlotRepository.clearPreparedSlotsForTrack(
            characterId = characterId,
            trackKey = trackKey,
        )
        focusStateRepository.upsertFocusState(
            current.copy(currentPoints = current.maxPoints),
        )
        sessionEventRepository.appendSessionEvent(
            SessionEvent(
                characterId = characterId,
                type = SessionEventType.NEW_DAY_PREPARATION,
                metadataJson = metadataForTrack(trackKey),
            ),
        )
        return true
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

    private suspend fun currentFocusState(characterId: Long): FocusState {
        return focusStateRepository.observeFocusState(characterId).first() ?: FocusState(
            characterId = characterId,
            currentPoints = 0,
            maxPoints = 1,
        )
    }

    private fun metadataForTrack(trackKey: String): String {
        return "{\"trackKey\":\"$trackKey\"}"
    }

    companion object {
        private const val MAX_FOCUS_POINTS = 3
    }
}
