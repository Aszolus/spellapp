package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEventType
import com.spellapp.core.model.SpellSlotSummary
import com.spellapp.core.model.effectiveCantripRank
import com.spellapp.core.model.preferredSpellTradition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreparedSlotsUiState(
    val characterName: String = "Character",
    val characterLevel: Int = 1,
    val effectiveCantripRank: Int = 1,
    val spellDc: Int = 0,
    val spellAttackModifier: Int = 0,
    val selectedTrackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    val castingTracks: List<CastingTrack> = emptyList(),
    val selectedTrackPreferredTradition: String? = null,
    val selectedTrackSourceId: String? = null,
    val allSlots: List<PreparedSlot> = emptyList(),
    val knownSpellSummaries: List<SpellSlotSummary> = emptyList(),
    val spellSummaryById: Map<String, SpellSlotSummary> = emptyMap(),
    val focusCurrentPoints: Int = 0,
    val focusMaxPoints: Int = 1,
    val recentEventLines: List<String> = emptyList(),
    val canUndoLastCast: Boolean = false,
    val hasBlessedOneDedication: Boolean = false,
)

private data class SlotContext(
    val selectedTrackKey: String,
    val castingTracks: List<CastingTrack>,
    val allSlots: List<PreparedSlot>,
    val knownSpells: List<KnownSpell>,
)

private data class EventContext(
    val spellSummaryById: Map<String, SpellSlotSummary>,
    val recentEventLines: List<String>,
    val canUndoLastCast: Boolean,
)

private data class CharacterContext(
    val characterName: String,
    val characterLevel: Int,
    val spellDc: Int,
    val spellAttackModifier: Int,
)

private data class UiMetaContext(
    val focusCurrentPoints: Int,
    val focusMaxPoints: Int,
    val hasBlessedOneDedication: Boolean,
    val characterName: String,
    val characterLevel: Int,
    val spellDc: Int,
    val spellAttackModifier: Int,
)

class PreparedSlotsViewModel(
    private val characterId: Long,
    private val service: PreparedSlotsService,
) : ViewModel() {
    private val selectedTrackKey = MutableStateFlow(PreparedSlot.PRIMARY_TRACK_KEY)
    private val characterProfile = MutableStateFlow(
        CharacterContext(
            characterName = "Character",
            characterLevel = 1,
            spellDc = 0,
            spellAttackModifier = 0,
        )
    )
    private val castingTracks = service.observeCastingTracks(characterId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeTrackKey = combine(
        selectedTrackKey,
        castingTracks,
    ) { selected, tracks ->
        if (tracks.any { it.trackKey == selected }) {
            selected
        } else {
            tracks.firstOrNull()?.trackKey ?: PreparedSlot.PRIMARY_TRACK_KEY
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreparedSlot.PRIMARY_TRACK_KEY,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val preparedSlots = activeTrackKey.flatMapLatest { trackKey ->
        service.observePreparedSlots(
            characterId = characterId,
            trackKey = trackKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val knownSpells = activeTrackKey.flatMapLatest { trackKey ->
        service.observeKnownSpells(
            characterId = characterId,
            trackKey = trackKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sessionEvents = activeTrackKey.flatMapLatest { trackKey ->
        service.observeSessionEvents(
            characterId = characterId,
            trackKey = trackKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val focusState = service.observeFocusState(characterId)
    private val hasBlessedOneDedication = service.observeHasBlessedOneDedication(characterId)
    private val uiMetaContext = combine(
        focusState,
        hasBlessedOneDedication,
        characterProfile,
    ) { focus, blessedOne, character ->
        UiMetaContext(
            focusCurrentPoints = focus.currentPoints,
            focusMaxPoints = focus.maxPoints,
            hasBlessedOneDedication = blessedOne,
            characterName = character.characterName,
            characterLevel = character.characterLevel,
            spellDc = character.spellDc,
            spellAttackModifier = character.spellAttackModifier,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val spellSummaryById = combine(preparedSlots, sessionEvents, knownSpells) { slots, events, known ->
        buildSet {
            addAll(slots.mapNotNull { it.preparedSpellId })
            addAll(events.mapNotNull { it.spellId })
            addAll(known.map { it.spellId })
        }
    }.mapLatest { spellIds ->
        service.resolveSpellSummaries(spellIds)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
    )

    private val slotContext = combine(
        activeTrackKey,
        castingTracks,
        preparedSlots,
        knownSpells,
    ) { trackKey, tracks, slots, known ->
        val sortedSlots = slots.sortedWith(
            compareBy<PreparedSlot> { it.rank }.thenBy { it.slotIndex },
        )
        SlotContext(
            selectedTrackKey = trackKey,
            castingTracks = tracks,
            allSlots = sortedSlots,
            knownSpells = known.sortedBy { it.spellId },
        )
    }

    private val eventContext = combine(
        sessionEvents,
        spellSummaryById,
    ) { events, summaries ->
        EventContext(
            spellSummaryById = summaries,
            recentEventLines = service.formatRecentEventLines(
                sessionEvents = events,
                spellSummaryById = summaries,
            ),
            canUndoLastCast = events.any { event -> event.type == SessionEventType.CAST_SPELL },
        )
    }

    val uiState = combine(
        slotContext,
        eventContext,
        uiMetaContext,
    ) { slots, events, meta ->
        PreparedSlotsUiState(
            characterName = meta.characterName,
            characterLevel = meta.characterLevel,
            effectiveCantripRank = effectiveCantripRank(meta.characterLevel),
            spellDc = meta.spellDc,
            spellAttackModifier = meta.spellAttackModifier,
            selectedTrackKey = slots.selectedTrackKey,
            castingTracks = slots.castingTracks,
            selectedTrackPreferredTradition = slots.castingTracks
                .firstOrNull { track -> track.trackKey == slots.selectedTrackKey }
                ?.preferredSpellTradition(),
            selectedTrackSourceId = slots.castingTracks
                .firstOrNull { track -> track.trackKey == slots.selectedTrackKey }
                ?.sourceId,
            allSlots = slots.allSlots,
            knownSpellSummaries = slots.knownSpells.mapNotNull { knownSpell ->
                events.spellSummaryById[knownSpell.spellId]
            }.sortedWith(
                compareBy<SpellSlotSummary> { it.rank }.thenBy { it.name },
            ),
            spellSummaryById = events.spellSummaryById,
            focusCurrentPoints = meta.focusCurrentPoints,
            focusMaxPoints = meta.focusMaxPoints,
            recentEventLines = events.recentEventLines,
            canUndoLastCast = events.canUndoLastCast,
            hasBlessedOneDedication = meta.hasBlessedOneDedication,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreparedSlotsUiState(),
    )

    init {
        viewModelScope.launch {
            service.syncPreparedSlots(characterId)
        }
        viewModelScope.launch {
            service.getCharacterProfile(characterId)?.let { profile ->
                characterProfile.value = CharacterContext(
                    characterName = profile.name,
                    characterLevel = profile.level,
                    spellDc = profile.spellDc,
                    spellAttackModifier = profile.spellAttackModifier,
                )
            }
        }
    }

    fun onTrackChange(trackKey: String) {
        selectedTrackKey.update { trackKey }
    }

    fun clearSpell(rank: Int, slotIndex: Int) {
        viewModelScope.launch {
            service.clearSpell(
                characterId = characterId,
                rank = rank,
                slotIndex = slotIndex,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun castSlot(rank: Int, slotIndex: Int) {
        viewModelScope.launch {
            service.castSlot(
                characterId = characterId,
                rank = rank,
                slotIndex = slotIndex,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun uncastSlot(rank: Int, slotIndex: Int) {
        viewModelScope.launch {
            service.uncastSlot(
                characterId = characterId,
                rank = rank,
                slotIndex = slotIndex,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun undoLastCast() {
        viewModelScope.launch {
            service.undoLastCast(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun useFocusPoint() {
        viewModelScope.launch {
            service.useFocusPoint(characterId)
        }
    }

    fun increaseFocusMax() {
        viewModelScope.launch {
            service.increaseFocusMax(characterId)
        }
    }

    fun decreaseFocusMax() {
        viewModelScope.launch {
            service.decreaseFocusMax(characterId)
        }
    }

    fun refocus() {
        viewModelScope.launch {
            service.refocus(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun castLayOnHands() {
        viewModelScope.launch {
            service.castLayOnHands(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun rest() {
        viewModelScope.launch {
            service.rest(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun newDayPreparation() {
        viewModelScope.launch {
            service.newDayPreparation(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun prepareRandom() {
        viewModelScope.launch {
            service.prepareRandom(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }
}

class PreparedSlotsViewModelFactory(
    private val characterId: Long,
    private val preparedSlotRepository: PreparedSlotRepository,
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val focusStateRepository: FocusStateRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val spellRepository: SpellRepository,
    private val characterCrudRepository: CharacterCrudRepository,
    private val characterBuildRepository: CharacterBuildRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(PreparedSlotsViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        val service = PreparedSlotsService(
            preparedSlotRepository = preparedSlotRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
            sessionEventRepository = sessionEventRepository,
            focusStateRepository = focusStateRepository,
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
        )
        return PreparedSlotsViewModel(
            characterId = characterId,
            service = service,
        ) as T
    }
}
