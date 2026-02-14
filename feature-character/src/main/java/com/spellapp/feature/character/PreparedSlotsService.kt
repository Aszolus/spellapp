package com.spellapp.feature.character

import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.FocusState
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SessionEventType
import com.spellapp.core.model.SpellSlotSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PreparedSlotsService(
    private val preparedSlotRepository: PreparedSlotRepository,
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val focusStateRepository: FocusStateRepository,
    private val spellRepository: SpellRepository,
    private val characterCrudRepository: CharacterCrudRepository,
) {
    suspend fun syncPreparedSlots(characterId: Long) {
        preparedSlotSyncRepository.syncPreparedSlotsForCharacter(characterId)
    }

    fun observeCastingTracks(characterId: Long): Flow<List<CastingTrack>> {
        return castingTrackRepository.observeCastingTracks(characterId)
    }

    fun observePreparedSlots(
        characterId: Long,
        trackKey: String,
    ): Flow<List<PreparedSlot>> {
        return preparedSlotRepository.observePreparedSlots(
            characterId = characterId,
            trackKey = trackKey,
        )
    }

    fun observeSessionEvents(
        characterId: Long,
        trackKey: String,
    ): Flow<List<SessionEvent>> {
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

    suspend fun resolveSpellSummaries(
        preparedSlots: List<PreparedSlot>,
        sessionEvents: List<SessionEvent>,
    ): Map<String, SpellSlotSummary> {
        val ids = (preparedSlots.mapNotNull { it.preparedSpellId } + sessionEvents.mapNotNull { it.spellId })
            .distinct()
        return ids.mapNotNull { spellId ->
            spellRepository.getSpellDetail(spellId)?.let { detail ->
                spellId to SpellSlotSummary(
                    spellId = detail.id,
                    name = detail.name,
                    castTime = detail.castTime,
                    range = detail.range,
                    traits = detail.traits,
                )
            }
        }.toMap()
    }

    fun formatRecentEventLines(
        sessionEvents: List<SessionEvent>,
        spellSummaryById: Map<String, SpellSlotSummary>,
        limit: Int = 8,
    ): List<String> {
        val nameMap = spellSummaryById.mapValues { it.value.name }
        return sessionEvents
            .take(limit)
            .map { event -> formatSessionEventLine(event, nameMap) }
    }

    suspend fun getCharacterProfile(characterId: Long): CharacterProfile? {
        return characterCrudRepository.getCharacter(characterId)
    }

    suspend fun clearSpell(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String,
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
        trackKey: String,
    ): Boolean {
        return preparedSlotRepository.castPreparedSlot(
            characterId = characterId,
            rank = rank,
            slotIndex = slotIndex,
            trackKey = trackKey,
        )
    }

    suspend fun uncastSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String,
    ): Boolean {
        return preparedSlotRepository.uncastSlot(
            characterId = characterId,
            rank = rank,
            slotIndex = slotIndex,
            trackKey = trackKey,
        )
    }

    suspend fun undoLastCast(
        characterId: Long,
        trackKey: String,
    ): Boolean {
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

    suspend fun refocus(
        characterId: Long,
        trackKey: String,
    ): Boolean {
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

    suspend fun rest(
        characterId: Long,
        trackKey: String,
    ): Boolean {
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

    suspend fun newDayPreparation(
        characterId: Long,
        trackKey: String,
    ): Boolean {
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

    suspend fun prepareRandom(
        characterId: Long,
        trackKey: String,
        sourceFilter: String? = null,
        rarityFilter: String? = null,
    ) {
        val character = characterCrudRepository.getCharacter(characterId) ?: return
        val tradition = traditionStringForTrack(trackKey, character.characterClass) ?: return
        val normalizedSourceFilter = sourceFilter.orEmpty().trim()
        val normalizedRarityFilter = rarityFilter
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        val slots = preparedSlotRepository.observePreparedSlots(
            characterId = characterId,
            trackKey = trackKey,
        ).first()
        val emptySlots = slots.filter { it.preparedSpellId == null }
        if (emptySlots.isEmpty()) return

        // Fetch all spells for this tradition once, then filter per-rank in memory.
        // A spell of rank N can fill any slot of rank >= N (heightening).
        // Cantrip slots (rank 0) only accept cantrips.
        val allTraditionSpells = spellRepository.observeSpells(
            tradition = tradition,
            rarity = normalizedRarityFilter,
        ).first()
        val filteredSpells = if (normalizedSourceFilter.isBlank()) {
            allTraditionSpells
        } else {
            allTraditionSpells.filter { spell ->
                spell.sourceBook.contains(normalizedSourceFilter, ignoreCase = true)
            }
        }

        val slotsByRank = emptySlots.groupBy { it.rank }
        for ((slotRank, rankSlots) in slotsByRank) {
            val candidates = if (slotRank == 0) {
                filteredSpells.filter { it.rank == 0 }
            } else {
                filteredSpells.filter { it.rank in 1..slotRank }
            }
            if (candidates.isEmpty()) continue
            for (slot in rankSlots) {
                val chosen = candidates.random()
                preparedSlotRepository.assignSpellToPreparedSlot(
                    characterId = characterId,
                    rank = slotRank,
                    slotIndex = slot.slotIndex,
                    spellId = chosen.id,
                    trackKey = trackKey,
                )
            }
        }
    }

    private fun traditionStringForTrack(
        trackKey: String,
        characterClass: CharacterClass,
    ): String? {
        if (trackKey == PreparedSlot.PRIMARY_TRACK_KEY) {
            return when (characterClass) {
                CharacterClass.WIZARD -> "arcane"
                CharacterClass.CLERIC -> "divine"
                CharacterClass.DRUID -> "primal"
                CharacterClass.OTHER -> null
            }
        }
        if (trackKey.startsWith("archetype-")) {
            val archetypeId = trackKey.removePrefix("archetype-").trim().lowercase()
            return when (archetypeId) {
                "wizard" -> "arcane"
                "cleric" -> "divine"
                "druid" -> "primal"
                else -> null
            }
        }
        return null
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
