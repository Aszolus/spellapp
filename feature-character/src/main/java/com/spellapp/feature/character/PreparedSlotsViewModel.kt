package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreparedSlotsUiState(
    val selectedRank: Int = 1,
    val allSlots: List<PreparedSlot> = emptyList(),
    val slotsForRank: List<PreparedSlot> = emptyList(),
    val spellNameById: Map<String, String> = emptyMap(),
    val focusCurrentPoints: Int = 0,
    val focusMaxPoints: Int = 1,
    val recentEventLines: List<String> = emptyList(),
    val canUndoLastCast: Boolean = false,
)

class PreparedSlotsViewModel(
    private val characterId: Long,
    private val service: PreparedSlotsService,
) : ViewModel() {
    private val selectedRank = MutableStateFlow(1)
    private val preparedSlots = service.observePreparedSlots(characterId)
    private val sessionEvents = service.observeSessionEvents(characterId)
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

    val uiState = combine(
        selectedRank,
        preparedSlots,
        sessionEvents,
        spellNameById,
        focusState,
    ) { rank, slots, events, names, focus ->
        val sortedSlots = slots.sortedWith(
            compareBy<PreparedSlot> { it.rank }.thenBy { it.slotIndex },
        )
        PreparedSlotsUiState(
            selectedRank = rank,
            allSlots = sortedSlots,
            slotsForRank = sortedSlots
                .filter { it.rank == rank },
            spellNameById = names,
            focusCurrentPoints = focus.currentPoints,
            focusMaxPoints = focus.maxPoints,
            recentEventLines = service.formatRecentEventLines(
                sessionEvents = events,
                spellNameById = names,
            ),
            canUndoLastCast = events.any { event -> event.type == SessionEventType.CAST_SPELL },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreparedSlotsUiState(),
    )

    fun onRankChange(rank: Int) {
        selectedRank.update { rank }
    }

    fun addSlot(rank: Int) {
        viewModelScope.launch {
            service.addSlot(
                characterId = characterId,
                rank = rank,
            )
        }
    }

    fun removeSlot(rank: Int, slotIndex: Int) {
        viewModelScope.launch {
            service.removeSlot(
                characterId = characterId,
                rank = rank,
                slotIndex = slotIndex,
            )
        }
    }

    fun clearSpell(rank: Int, slotIndex: Int) {
        viewModelScope.launch {
            service.clearSpell(
                characterId = characterId,
                rank = rank,
                slotIndex = slotIndex,
            )
        }
    }

    fun castSlot(rank: Int, slotIndex: Int) {
        viewModelScope.launch {
            service.castSlot(
                characterId = characterId,
                rank = rank,
                slotIndex = slotIndex,
            )
        }
    }

    fun undoLastCast() {
        viewModelScope.launch {
            service.undoLastCast(characterId)
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
            service.refocus(characterId)
        }
    }

    fun rest() {
        viewModelScope.launch {
            service.rest(characterId)
        }
    }

    fun newDayPreparation() {
        viewModelScope.launch {
            service.newDayPreparation(characterId)
        }
    }
}

class PreparedSlotsViewModelFactory(
    private val characterId: Long,
    private val preparedSlotRepository: PreparedSlotRepository,
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
