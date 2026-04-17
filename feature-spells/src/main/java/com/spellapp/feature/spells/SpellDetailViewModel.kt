package com.spellapp.feature.spells

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.SpellDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpellDetailUiState(
    val spell: SpellDetail? = null,
    val isLoading: Boolean = true,
    val heightenedAt: Int? = null,
)

class SpellDetailViewModel(
    private val spellId: String,
    private val spellRepository: SpellRepository,
    initialHeightenedAt: Int? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpellDetailUiState(heightenedAt = initialHeightenedAt))
    val uiState: StateFlow<SpellDetailUiState> = _uiState.asStateFlow()

    init {
        loadSpellDetail()
    }

    fun reload() {
        loadSpellDetail()
    }

    private fun loadSpellDetail() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    spell = null,
                    isLoading = true,
                )
            }
            val spell = spellRepository.getSpellDetail(spellId)
            _uiState.update {
                it.copy(
                    spell = spell,
                    isLoading = false,
                )
            }
        }
    }
}

class SpellDetailViewModelFactory(
    private val spellId: String,
    private val spellRepository: SpellRepository,
    private val initialHeightenedAt: Int? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(SpellDetailViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return SpellDetailViewModel(
            spellId = spellId,
            spellRepository = spellRepository,
            initialHeightenedAt = initialHeightenedAt,
        ) as T
    }
}
