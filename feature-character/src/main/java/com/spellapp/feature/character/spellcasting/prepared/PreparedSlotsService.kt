package com.spellapp.feature.character.spellcasting.prepared

import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.HeightenTrigger
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SessionEventType
import com.spellapp.core.model.preferredSpellTradition
import com.spellapp.core.model.spellSupportsTradition
import com.spellapp.feature.character.spellcasting.SpellcastingSupportService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PreparedSlotsService(
    private val preparedSlotRepository: PreparedSlotRepository,
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val focusStateRepository: FocusStateRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val spellRepository: SpellRepository,
    private val spellcastingSupportService: SpellcastingSupportService,
) {
    suspend fun syncPreparedSlots(characterId: Long) {
        preparedSlotSyncRepository.syncPreparedSlotsForCharacter(characterId)
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

    suspend fun newDayPreparation(
        characterId: Long,
        trackKey: String,
    ): Boolean {
        val current = spellcastingSupportService.currentFocusState(characterId)
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
                metadataJson = spellcastingSupportService.metadataForTrack(trackKey),
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
        val entries = spellRepository.getSpellDetail(spellId)?.heightenedEntries.orEmpty()
        val progression = if (entries.isEmpty()) {
            HeightenedProgression.EMPTY
        } else {
            HeightenedProgression(
                incrementSteps = entries
                    .mapNotNull { (it.trigger as? HeightenTrigger.Step)?.increment }
                    .toSet(),
                absoluteRanks = entries
                    .mapNotNull { (it.trigger as? HeightenTrigger.Absolute)?.rank }
                    .toSet(),
            )
        }
        heightenProgressionBySpellId[spellId] = progression
        return progression
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
}
