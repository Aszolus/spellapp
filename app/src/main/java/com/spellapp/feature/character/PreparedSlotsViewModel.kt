package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.CharacterRepository
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
    val slotsForRank: List<PreparedSlot> = emptyList(),
    val spellNameById: Map<String, String> = emptyMap(),
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
    ) { rank, slots, events, names ->
        PreparedSlotsUiState(
            selectedRank = rank,
            slotsForRank = slots
                .filter { it.rank == rank }
                .sortedBy { it.slotIndex },
            spellNameById = names,
            recentEventLines = service.formatRecentEventLines(
                sessionEvents = events,
                spellNameById = names,
            ),
            canUndoLastCast = events.firstOrNull()?.type == SessionEventType.CAST_SPELL,
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
}

class PreparedSlotsViewModelFactory(
    private val characterId: Long,
    private val characterRepository: CharacterRepository,
    private val spellRepository: SpellRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(PreparedSlotsViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        val service = PreparedSlotsService(
            characterRepository = characterRepository,
            spellRepository = spellRepository,
        )
        return PreparedSlotsViewModel(
            characterId = characterId,
            service = service,
        ) as T
    }
}
