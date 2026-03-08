package com.spellapp.feature.character

import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.FocusState
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SessionEventType
import com.spellapp.core.model.SpellSlotSummary
import com.spellapp.core.model.preferredSpellTradition
import com.spellapp.core.model.spellSupportsTradition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PreparedSlotsService(
    private val preparedSlotRepository: PreparedSlotRepository,
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val focusStateRepository: FocusStateRepository,
    private val knownSpellRepository: KnownSpellRepository,
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

    fun observeKnownSpells(
        characterId: Long,
        trackKey: String,
    ): Flow<List<KnownSpell>> {
        return knownSpellRepository.observeKnownSpells(
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

    suspend fun resolveSpellSummaries(spellIds: Set<String>): Map<String, SpellSlotSummary> {
        return spellIds.mapNotNull { spellId ->
            spellRepository.getSpellDetail(spellId)?.let { detail ->
                spellId to SpellSlotSummary(
                    spellId = detail.id,
                    name = detail.name,
                    rank = detail.rank,
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
    ) {
        val slots = preparedSlotRepository.observePreparedSlots(
            characterId = characterId,
            trackKey = trackKey,
        ).first()
        val emptySlots = slots.filter { it.preparedSpellId == null }
        if (emptySlots.isEmpty()) return

        val knownSpellIds = knownSpellRepository.observeKnownSpellIds(
            characterId = characterId,
            trackKey = trackKey,
        ).first()
        if (knownSpellIds.isEmpty()) return

        val filteredSpells = knownSpellIds.mapNotNull { spellId ->
            spellRepository.getSpellDetail(spellId)
        }
        val preferredTradition = castingTrackRepository.getCastingTracks(characterId)
            .firstOrNull { track -> track.trackKey == trackKey }
            ?.preferredSpellTradition()
        val candidateSpells = preferredTradition?.let { tradition ->
            filteredSpells.filter { spell ->
                spellSupportsTradition(
                    traditions = spell.tradition,
                    preferredTradition = tradition,
                )
            }
        } ?: filteredSpells
        if (candidateSpells.isEmpty()) return
        val heightenProgressionBySpellId = mutableMapOf<String, HeightenedProgression>()

        val slotsByRank = emptySlots.groupBy { it.rank }
        for ((slotRank, rankSlots) in slotsByRank) {
            val candidates = candidateSpells.filter { spell ->
                canSpellFillSlot(
                    spellId = spell.id,
                    spellRank = spell.rank,
                    slotRank = slotRank,
                    heightenProgressionBySpellId = heightenProgressionBySpellId,
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
        heightenProgressionBySpellId: MutableMap<String, HeightenedProgression>,
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
        val progression = resolveHeightenedProgression(
            spellId = spellId,
            heightenProgressionBySpellId = heightenProgressionBySpellId,
        )
        return progression.grantsBenefitForTargetRank(
            baseRank = spellRank,
            targetRank = slotRank,
        )
    }

    private suspend fun resolveHeightenedProgression(
        spellId: String,
        heightenProgressionBySpellId: MutableMap<String, HeightenedProgression>,
    ): HeightenedProgression {
        heightenProgressionBySpellId[spellId]?.let { cached ->
            return cached
        }
        val progression = spellRepository.getSpellDetail(spellId)
            ?.description
            ?.let { description ->
                parseHeightenedProgression(description)
            } ?: HeightenedProgression.EMPTY
        heightenProgressionBySpellId[spellId] = progression
        return progression
    }

    private fun parseHeightenedProgression(description: String): HeightenedProgression {
        val increments = linkedSetOf<Int>()
        val absoluteRanks = linkedSetOf<Int>()
        HEIGHTENED_LINE_PATTERN.findAll(description).forEach { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty().trim().lowercase()
            if (raw.isBlank()) {
                return@forEach
            }
            val normalized = raw.replace(" ", "")
            val incrementRank = HEIGHTEN_INCREMENT_PATTERN.matchEntire(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            if (incrementRank != null && incrementRank > 0) {
                increments += incrementRank
                return@forEach
            }
            val absoluteRank = HEIGHTEN_ABSOLUTE_PATTERN.matchEntire(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            if (absoluteRank != null && absoluteRank > 0) {
                absoluteRanks += absoluteRank
            }
        }
        return HeightenedProgression(
            incrementSteps = increments,
            absoluteRanks = absoluteRanks,
        )
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

    private data class HeightenedProgression(
        val incrementSteps: Set<Int>,
        val absoluteRanks: Set<Int>,
    ) {
        fun grantsBenefitForTargetRank(
            baseRank: Int,
            targetRank: Int,
        ): Boolean {
            if (targetRank <= baseRank) {
                return false
            }
            val rankIncrease = targetRank - baseRank
            if (incrementSteps.any { step -> rankIncrease % step == 0 }) {
                return true
            }
            return absoluteRanks.any { absoluteRank ->
                absoluteRank in (baseRank + 1)..targetRank
            }
        }

        companion object {
            val EMPTY = HeightenedProgression(
                incrementSteps = emptySet(),
                absoluteRanks = emptySet(),
            )
        }
    }

    companion object {
        private const val MAX_FOCUS_POINTS = 3
        private const val BLESSED_ONE_DEDICATION_OPTION_ID = "archetype/blessed-one/blessed-one-dedication"
        private val HEIGHTENED_LINE_PATTERN = Regex(
            pattern = "(?m)^\\s*Heightened\\s*\\(([^)]+)\\)",
            option = RegexOption.IGNORE_CASE,
        )
        private val HEIGHTEN_INCREMENT_PATTERN = Regex("^\\+(\\d+)$")
        private val HEIGHTEN_ABSOLUTE_PATTERN = Regex("^(\\d+)(st|nd|rd|th)$")
    }
}
