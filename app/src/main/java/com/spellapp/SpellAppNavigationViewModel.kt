package com.spellapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.feature.spells.AssignPreparedSpellUseCase
import com.spellapp.feature.spells.SpellBrowserMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpellAppNavigationUiState(
    val activeCharacterId: Long? = null,
    val spellBrowserMode: SpellBrowserMode? = null,
    val spellBrowserSessionId: Long = 0L,
)

class SpellAppNavigationViewModel(
    private val assignPreparedSpellUseCase: AssignPreparedSpellUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpellAppNavigationUiState())
    val uiState: StateFlow<SpellAppNavigationUiState> = _uiState.asStateFlow()
    private val _slotAssignmentResult = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val slotAssignmentResult: SharedFlow<Boolean> = _slotAssignmentResult.asSharedFlow()

    fun openPreparedSlots(characterId: Long) {
        _uiState.update { current ->
            current.copy(
                activeCharacterId = characterId,
                spellBrowserMode = null,
            )
        }
    }

    fun openSpellList(characterId: Long) {
        _uiState.update { current ->
            current.copy(
                activeCharacterId = characterId,
                spellBrowserMode = SpellBrowserMode.BrowseCatalog(characterId = characterId),
                spellBrowserSessionId = current.spellBrowserSessionId + 1L,
            )
        }
    }

    fun manageKnownSpells(
        characterId: Long,
        trackKey: String,
        preferredTradition: String?,
        trackSourceId: String?,
    ) {
        _uiState.update { current ->
            current.copy(
                activeCharacterId = characterId,
                spellBrowserMode = SpellBrowserMode.ManageKnownSpells(
                    characterId = characterId,
                    trackKey = trackKey,
                    preferredTradition = preferredTradition,
                    trackSourceId = trackSourceId,
                ),
                spellBrowserSessionId = current.spellBrowserSessionId + 1L,
            )
        }
    }

    fun startPreparedSlotAssignment(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String,
        preferredTradition: String?,
    ) {
        _uiState.update { current ->
            current.copy(
                activeCharacterId = characterId,
                spellBrowserMode = SpellBrowserMode.AssignPreparedSlot(
                    characterId = characterId,
                    trackKey = trackKey,
                    slotRank = rank,
                    slotIndex = slotIndex,
                    preferredTradition = preferredTradition,
                ),
                spellBrowserSessionId = current.spellBrowserSessionId + 1L,
            )
        }
    }

    fun clearSpellBrowserMode() {
        _uiState.update { current ->
            current.copy(spellBrowserMode = null)
        }
    }

    fun completeSlotAssignment(spellId: String) {
        val mode = _uiState.value.spellBrowserMode as? SpellBrowserMode.AssignPreparedSlot ?: return
        viewModelScope.launch {
            val success = runCatching {
                assignPreparedSpellUseCase.assign(
                    mode = mode,
                    spellId = spellId,
                )
            }.getOrDefault(false)
            if (success) {
                clearSpellBrowserMode()
            }
            _slotAssignmentResult.emit(success)
        }
    }
}

class SpellAppNavigationViewModelFactory(
    private val assignPreparedSpellUseCase: AssignPreparedSpellUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(SpellAppNavigationViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return SpellAppNavigationViewModel(
            assignPreparedSpellUseCase = assignPreparedSpellUseCase,
        ) as T
    }
}
