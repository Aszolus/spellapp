package com.spellapp.feature.character.spellcasting

import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.FocusState
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SessionEventType
import com.spellapp.core.model.SpellSlotSummary
import com.spellapp.core.model.ordinalRank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SpellcastingSupportService(
    private val castingTrackRepository: CastingTrackRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val focusStateRepository: FocusStateRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val spellRepository: SpellRepository,
    private val characterCrudRepository: CharacterCrudRepository,
    private val characterBuildRepository: CharacterBuildRepository,
) {
    private var cachedLayOnHandsSpellId: String? = null

    fun observeCastingTracks(characterId: Long) = castingTrackRepository.observeCastingTracks(characterId)

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
                    heightenedEntries = detail.heightenedEntries,
                )
            }
        }.toMap()
    }

    fun formatRecentEventLines(
        sessionEvents: List<SessionEvent>,
        spellSummaryById: Map<String, SpellSlotSummary>,
        limit: Int = 8,
    ): List<String> {
        return sessionEvents
            .take(limit)
            .map { event -> formatSessionEventLine(event, spellSummaryById) }
    }

    suspend fun getCharacterProfile(characterId: Long): CharacterProfile? {
        return characterCrudRepository.getCharacter(characterId)
    }

    suspend fun useFocusPoint(characterId: Long): Boolean {
        val current = currentFocusState(characterId)
        if (current.currentPoints <= 0) {
            return false
        }
        focusStateRepository.upsertFocusState(current.copy(currentPoints = current.currentPoints - 1))
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
        focusStateRepository.upsertFocusState(current.copy(currentPoints = current.maxPoints))
        sessionEventRepository.appendSessionEvent(
            SessionEvent(
                characterId = characterId,
                type = SessionEventType.REST,
                metadataJson = metadataForTrack(trackKey),
            ),
        )
        return true
    }

    suspend fun resolveLayOnHandsSpellId(): String? {
        cachedLayOnHandsSpellId?.let { return it }
        val candidates = spellRepository.observeSpells(query = "Lay on Hands").first()
        val resolved = candidates.firstOrNull { spell ->
            spell.name.equals("Lay on Hands", ignoreCase = true)
        }?.id
        if (resolved != null) {
            cachedLayOnHandsSpellId = resolved
        }
        return resolved
    }

    suspend fun currentFocusState(characterId: Long): FocusState {
        return focusStateRepository.observeFocusState(characterId).first() ?: FocusState(
            characterId = characterId,
            currentPoints = 0,
            maxPoints = 1,
        )
    }

    fun metadataForTrack(trackKey: String): String {
        return "{\"trackKey\":\"$trackKey\"}"
    }

    private fun formatSessionEventLine(
        event: SessionEvent,
        spellSummaryById: Map<String, SpellSlotSummary>,
    ): String {
        val summary = event.spellId?.let { spellSummaryById[it] }
        val spellName = summary?.name
            ?: event.spellId
            ?: if (event.type == SessionEventType.CAST_FOCUS_SPELL) "Lay on Hands" else "Unknown Spell"
        return when (event.type) {
            SessionEventType.CAST_SPELL -> {
                val slotRank = event.spellRank
                when {
                    slotRank == null -> "Cast $spellName"
                    slotRank == 0 -> "Cast Cantrip: $spellName"
                    else -> {
                        val baseRank = summary?.rank
                        val delta = if (baseRank != null && slotRank > baseRank) slotRank - baseRank else 0
                        val suffix = if (delta > 0) " (+$delta)" else ""
                        "Cast $spellName @ ${ordinalRank(slotRank)}$suffix"
                    }
                }
            }

            SessionEventType.CAST_FOCUS_SPELL -> "Cast Focus: $spellName"
            SessionEventType.UNDO_LAST_ACTION -> "Undo last action"
            SessionEventType.REFOCUS -> "Refocus"
            SessionEventType.REST -> "Rest"
            SessionEventType.NEW_DAY_PREPARATION -> "New day preparation"
        }
    }

    private companion object {
        private const val MAX_FOCUS_POINTS = 3
        private const val BLESSED_ONE_DEDICATION_OPTION_ID = "archetype/blessed-one/blessed-one-dedication"
    }
}
