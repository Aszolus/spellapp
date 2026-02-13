package com.spellapp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PreparedSlotTarget(
    val characterId: Long,
    val rank: Int,
    val slotIndex: Int,
)

data class SpellAppNavigationUiState(
    val activeCharacterId: Long? = null,
    val preparedSlotTarget: PreparedSlotTarget? = null,
)

class SpellAppNavigationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SpellAppNavigationUiState())
    val uiState: StateFlow<SpellAppNavigationUiState> = _uiState.asStateFlow()

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
    ) {
        _uiState.update { current ->
            current.copy(
                activeCharacterId = characterId,
                preparedSlotTarget = PreparedSlotTarget(
                    characterId = characterId,
                    rank = rank,
                    slotIndex = slotIndex,
                ),
            )
        }
    }

    fun clearPreparedSlotTarget() {
        _uiState.update { current ->
            current.copy(preparedSlotTarget = null)
        }
    }
}
