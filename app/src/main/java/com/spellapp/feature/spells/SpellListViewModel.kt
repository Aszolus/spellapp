package com.spellapp.feature.spells

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.SpellRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class SpellListUiState(
    val queryInput: String = "",
    val traitQueryInput: String = "",
    val selectedRank: Int? = null,
    val selectedTradition: String? = null,
    val selectedRarity: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SpellListViewModel(
    private val spellRepository: SpellRepository,
) : ViewModel() {
    private val queryInput = MutableStateFlow("")
    private val traitQueryInput = MutableStateFlow("")
    private val selectedRank = MutableStateFlow<Int?>(null)
    private val selectedTradition = MutableStateFlow<String?>(null)
    private val selectedRarity = MutableStateFlow<String?>(null)

    val uiState = combine(
        queryInput,
        traitQueryInput,
        selectedRank,
        selectedTradition,
        selectedRarity,
    ) { query, trait, rank, tradition, rarity ->
        SpellListUiState(
            queryInput = query,
            traitQueryInput = trait,
            selectedRank = rank,
            selectedTradition = tradition,
            selectedRarity = rarity,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SpellListUiState(),
    )

    val spells = combine(
        queryInput.debounce(250),
        traitQueryInput.debounce(250),
        selectedRank,
        selectedTradition,
        selectedRarity,
    ) { query, trait, rank, tradition, rarity ->
        SpellFilters(
            query = query,
            trait = trait,
            rank = rank,
            tradition = tradition,
            rarity = rarity,
        )
    }.flatMapLatest { filters ->
        spellRepository.observeSpells(
            query = filters.query,
            rank = filters.rank,
            tradition = filters.tradition,
            rarity = filters.rarity,
            trait = filters.trait,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun onQueryChange(query: String) {
        queryInput.update { query }
    }

    fun onTraitQueryChange(query: String) {
        traitQueryInput.update { query }
    }

    fun onRankToggle(rank: Int) {
        selectedRank.update { current ->
            if (current == rank) null else rank
        }
    }

    fun setRank(rank: Int) {
        selectedRank.update { rank }
    }

    fun onTraditionToggle(tradition: String) {
        selectedTradition.update { current ->
            if (current == tradition) null else tradition
        }
    }

    fun onRarityToggle(rarity: String) {
        selectedRarity.update { current ->
            if (current == rarity) null else rarity
        }
    }

    fun clearTextFilters() {
        queryInput.update { "" }
        traitQueryInput.update { "" }
    }

    fun clearAllFilters() {
        queryInput.update { "" }
        traitQueryInput.update { "" }
        selectedRank.update { null }
        selectedTradition.update { null }
        selectedRarity.update { null }
    }
}

class SpellListViewModelFactory(
    private val spellRepository: SpellRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(SpellListViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return SpellListViewModel(spellRepository) as T
    }
}

private data class SpellFilters(
    val query: String,
    val trait: String,
    val rank: Int?,
    val tradition: String?,
    val rarity: String?,
)
