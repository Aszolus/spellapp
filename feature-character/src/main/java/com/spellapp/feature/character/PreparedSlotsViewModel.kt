package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEventType
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
    val selectedRank: Int = 1,
    val selectedTrackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    val castingTracks: List<CastingTrack> = emptyList(),
    val allSlots: List<PreparedSlot> = emptyList(),
    val slotsForRank: List<PreparedSlot> = emptyList(),
    val spellNameById: Map<String, String> = emptyMap(),
    val focusCurrentPoints: Int = 0,
    val focusMaxPoints: Int = 1,
    val recentEventLines: List<String> = emptyList(),
    val canUndoLastCast: Boolean = false,
)

private data class SlotContext(
    val selectedRank: Int,
    val selectedTrackKey: String,
    val castingTracks: List<CastingTrack>,
    val allSlots: List<PreparedSlot>,
    val slotsForRank: List<PreparedSlot>,
)

private data class EventContext(
    val spellNameById: Map<String, String>,
    val recentEventLines: List<String>,
    val canUndoLastCast: Boolean,
)

class PreparedSlotsViewModel(
    private val characterId: Long,
    private val service: PreparedSlotsService,
) : ViewModel() {
    private val selectedRank = MutableStateFlow(1)
    private val selectedTrackKey = MutableStateFlow(PreparedSlot.PRIMARY_TRACK_KEY)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private val spellNameById = combine(preparedSlots, sessionEvents) { slots, events ->
        slots to events
    }.mapLatest { (slots, events) ->
        service.resolveSpellNames(
            preparedSlots = slots,
            sessionEvents = events,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
    )

    private val slotContext = combine(
        selectedRank,
        activeTrackKey,
        castingTracks,
        preparedSlots,
    ) { rank, trackKey, tracks, slots ->
        val sortedSlots = slots.sortedWith(
            compareBy<PreparedSlot> { it.rank }.thenBy { it.slotIndex },
        )
        SlotContext(
            selectedRank = rank,
            selectedTrackKey = trackKey,
            castingTracks = tracks,
            allSlots = sortedSlots,
            slotsForRank = sortedSlots
                .filter { it.rank == rank },
        )
    }

    private val eventContext = combine(
        sessionEvents,
        spellNameById,
    ) { events, names ->
        EventContext(
            spellNameById = names,
            recentEventLines = service.formatRecentEventLines(
                sessionEvents = events,
                spellNameById = names,
            ),
            canUndoLastCast = events.any { event -> event.type == SessionEventType.CAST_SPELL },
        )
    }

    val uiState = combine(
        slotContext,
        eventContext,
        focusState,
    ) { slots, events, focus ->
        PreparedSlotsUiState(
            selectedRank = slots.selectedRank,
            selectedTrackKey = slots.selectedTrackKey,
            castingTracks = slots.castingTracks,
            allSlots = slots.allSlots,
            slotsForRank = slots.slotsForRank,
            spellNameById = events.spellNameById,
            focusCurrentPoints = focus.currentPoints,
            focusMaxPoints = focus.maxPoints,
            recentEventLines = events.recentEventLines,
            canUndoLastCast = events.canUndoLastCast,
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
    }

    fun onRankChange(rank: Int) {
        selectedRank.update { rank }
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
}

class PreparedSlotsViewModelFactory(
    private val characterId: Long,
    private val preparedSlotRepository: PreparedSlotRepository,
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val focusStateRepository: FocusStateRepository,
    private val spellRepository: SpellRepository,
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
            spellRepository = spellRepository,
        )
        return PreparedSlotsViewModel(
            characterId = characterId,
            service = service,
        ) as T
    }
}
