package com.spellapp.feature.character

import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterBuildRepository
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
    private val characterBuildRepository: CharacterBuildRepository,
) {
    private var cachedLayOnHandsSpellId: String? = null
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

    fun observeHasBlessedOneDedication(characterId: Long): Flow<Boolean> {
        return characterBuildRepository.observeBuildOptions(characterId).map { options ->
            options.any { option ->
                option.optionId.equals(BLESSED_ONE_DEDICATION_OPTION_ID, ignoreCase = true)
            }
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

    suspend fun castLayOnHands(
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

        val current = currentFocusState(characterId)
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
                spellId = resolveLayOnHandsSpellId(),
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
        val canHeightenBySpellId = mutableMapOf<String, Boolean>()

        val slotsByRank = emptySlots.groupBy { it.rank }
        for ((slotRank, rankSlots) in slotsByRank) {
            val candidates = filteredSpells.filter { spell ->
                canSpellFillSlot(
                    spellId = spell.id,
                    spellRank = spell.rank,
                    slotRank = slotRank,
                    canHeightenBySpellId = canHeightenBySpellId,
                )
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

    private suspend fun canSpellFillSlot(
        spellId: String,
        spellRank: Int,
        slotRank: Int,
        canHeightenBySpellId: MutableMap<String, Boolean>,
    ): Boolean {
        if (slotRank == 0) {
            return spellRank == 0
        }
        if (spellRank <= 0 || spellRank > slotRank) {
            return false
        }
        if (spellRank == slotRank) {
            return true
        }
        val cachedCanHeighten = canHeightenBySpellId[spellId]
        if (cachedCanHeighten != null) {
            return cachedCanHeighten
        }
        val canHeighten = spellRepository.getSpellDetail(spellId)
            ?.description
            ?.let { description ->
                HEIGHTENED_BLOCK_PATTERN.containsMatchIn(description)
            } == true
        canHeightenBySpellId[spellId] = canHeighten
        return canHeighten
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
        val spellName = event.spellId?.let { spellNameById[it] ?: it }
            ?: if (event.type == SessionEventType.CAST_FOCUS_SPELL) {
                "Lay on Hands"
            } else {
                "Unknown Spell"
            }
        return when (event.type) {
            SessionEventType.CAST_SPELL -> {
                val rankLabel = if (event.spellRank == 0) "Cantrip" else "Rank ${event.spellRank ?: "?"}"
                "Cast $rankLabel: $spellName"
            }
            SessionEventType.CAST_FOCUS_SPELL -> "Cast Focus: $spellName"
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

    private suspend fun resolveLayOnHandsSpellId(): String? {
        cachedLayOnHandsSpellId?.let { return it }
        val candidates = spellRepository.observeSpells(
            query = "Lay on Hands",
        ).first()
        val resolved = candidates.firstOrNull { spell ->
            spell.name.equals("Lay on Hands", ignoreCase = true)
        }?.id
        if (resolved != null) {
            cachedLayOnHandsSpellId = resolved
        }
        return resolved
    }

    companion object {
        private const val MAX_FOCUS_POINTS = 3
        private const val BLESSED_ONE_DEDICATION_OPTION_ID = "archetype/blessed-one/blessed-one-dedication"
        private val HEIGHTENED_BLOCK_PATTERN = Regex(
            pattern = "(?m)^\\s*Heightened\\b",
            option = RegexOption.IGNORE_CASE,
        )
    }
}
