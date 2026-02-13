package com.spellapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.model.PreparedSlot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreparedSlotTarget(
    val characterId: Long,
    val rank: Int,
    val slotIndex: Int,
    val trackKey: String,
)

data class SpellAppNavigationUiState(
    val activeCharacterId: Long? = null,
    val preparedSlotTarget: PreparedSlotTarget? = null,
)

class SpellAppNavigationViewModel(
    private val preparedSlotRepository: PreparedSlotRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpellAppNavigationUiState())
    val uiState: StateFlow<SpellAppNavigationUiState> = _uiState.asStateFlow()
    private val _slotAssignmentResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val slotAssignmentResult: SharedFlow<Boolean> = _slotAssignmentResult.asSharedFlow()

    fun openPreparedSlots(characterId: Long) {
        _uiState.update { current ->
            current.copy(
                activeCharacterId = characterId,
                preparedSlotTarget = null,
            )
        }
    }

    fun openSpellList(characterId: Long) {
        _uiState.update { current ->
            current.copy(
                activeCharacterId = characterId,
                preparedSlotTarget = null,
            )
        }
    }

    fun startPreparedSlotAssignment(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    ) {
        _uiState.update { current ->
            current.copy(
                activeCharacterId = characterId,
                preparedSlotTarget = PreparedSlotTarget(
                    characterId = characterId,
                    rank = rank,
                    slotIndex = slotIndex,
                    trackKey = trackKey,
                ),
            )
        }
    }

    fun clearPreparedSlotTarget() {
        _uiState.update { current ->
            current.copy(preparedSlotTarget = null)
        }
    }

    fun completeSlotAssignment(spellId: String) {
        val target = _uiState.value.preparedSlotTarget ?: return
        viewModelScope.launch {
            val success = runCatching {
                preparedSlotRepository.assignSpellToPreparedSlot(
                    characterId = target.characterId,
                    rank = target.rank,
                    slotIndex = target.slotIndex,
                    spellId = spellId,
                    trackKey = target.trackKey,
                )
            }.isSuccess
            if (success) {
                clearPreparedSlotTarget()
            }
            _slotAssignmentResult.emit(success)
        }
    }
}

class SpellAppNavigationViewModelFactory(
    private val preparedSlotRepository: PreparedSlotRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(SpellAppNavigationViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return SpellAppNavigationViewModel(
            preparedSlotRepository = preparedSlotRepository,
        ) as T
    }
}
