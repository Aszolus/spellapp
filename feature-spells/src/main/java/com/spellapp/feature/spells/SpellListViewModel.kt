package com.spellapp.feature.spells

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpellListUiState(
    val queryInput: String = "",
    val traitQueryInput: String = "",
    val selectedRank: Int? = null,
    val selectedTradition: String? = null,
    val selectedRarity: String? = null,
    val browserMode: SpellBrowserMode = SpellBrowserMode.BrowseCatalog(),
    val knownSpellIds: Set<String> = emptySet(),
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SpellListViewModel(
    private val spellRepository: SpellRepository,
    private val knownSpellRepository: KnownSpellRepository,
) : ViewModel() {
    private val queryInput = MutableStateFlow("")
    private val traitQueryInput = MutableStateFlow("")
    private val selectedRank = MutableStateFlow<Int?>(null)
    private val selectedTradition = MutableStateFlow<String?>(null)
    private val selectedRarity = MutableStateFlow<String?>(null)
    private val browserMode = MutableStateFlow<SpellBrowserMode>(SpellBrowserMode.BrowseCatalog())

    private val knownSpellIds = browserMode.flatMapLatest { mode ->
        when (mode) {
            is SpellBrowserMode.ManageKnownSpells -> {
                knownSpellRepository.observeKnownSpellIds(mode.characterId, mode.trackKey)
            }

            is SpellBrowserMode.AssignPreparedSlot -> {
                knownSpellRepository.observeKnownSpellIds(mode.characterId, mode.trackKey)
            }

            is SpellBrowserMode.BrowseCatalog -> flowOf(emptySet())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet(),
    )

    private val filterUiState = combine(
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
    }

    val uiState = combine(
        filterUiState,
        browserMode,
        knownSpellIds,
    ) { filters, mode, knownIds ->
        filters.copy(
            browserMode = mode,
            knownSpellIds = knownIds,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SpellListUiState(),
    )

    private val filterInputs: Flow<SpellFilterInputs> = combine(
        queryInput.debounce(250),
        traitQueryInput.debounce(250),
        selectedRank,
        selectedTradition,
        selectedRarity,
    ) { query, trait, rank, tradition, rarity ->
        SpellFilterInputs(
            query = query,
            trait = trait,
            rank = rank,
            tradition = tradition,
            rarity = rarity,
        )
    }

    val spells = combine(
        filterInputs,
        browserMode,
        knownSpellIds,
    ) { filters, mode, knownIds ->
        Triple(filters, mode, knownIds)
    }.flatMapLatest { (filters, mode, knownIds) ->
        spellRepository.observeSpells(
            query = filters.query,
            rank = filters.rank.takeUnless { mode is SpellBrowserMode.AssignPreparedSlot },
            tradition = filters.tradition,
            rarity = filters.rarity,
            trait = filters.trait,
        ).map { sourceSpells ->
            var filtered = sourceSpells
            if (filters.rank != null) {
                filtered = filtered.filter { spell -> spell.rank == filters.rank }
            }
            when (mode) {
                is SpellBrowserMode.AssignPreparedSlot -> {
                    filtered = filtered.filter { spell -> spell.id in knownIds }
                    filtered = filtered.filter { spell ->
                        if (mode.slotRank == 0) {
                            spell.rank == 0
                        } else {
                            spell.rank in 1..mode.slotRank
                        }
                    }
                }

                is SpellBrowserMode.ManageKnownSpells -> Unit
                is SpellBrowserMode.BrowseCatalog -> Unit
            }
            filtered
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun setBrowserMode(mode: SpellBrowserMode) {
        browserMode.update { mode }
    }

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

    fun clearRankSelection() {
        selectedRank.update { null }
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

    fun toggleKnownSpell(spellId: String) {
        val mode = browserMode.value as? SpellBrowserMode.ManageKnownSpells ?: return
        viewModelScope.launch {
            val isKnown = knownSpellRepository.isKnownSpell(
                characterId = mode.characterId,
                trackKey = mode.trackKey,
                spellId = spellId,
            )
            if (isKnown) {
                knownSpellRepository.removeKnownSpell(
                    characterId = mode.characterId,
                    trackKey = mode.trackKey,
                    spellId = spellId,
                )
            } else {
                knownSpellRepository.addKnownSpell(
                    characterId = mode.characterId,
                    trackKey = mode.trackKey,
                    spellId = spellId,
                )
            }
        }
    }
}

class SpellListViewModelFactory(
    private val spellRepository: SpellRepository,
    private val knownSpellRepository: KnownSpellRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(SpellListViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return SpellListViewModel(
            spellRepository = spellRepository,
            knownSpellRepository = knownSpellRepository,
        ) as T
    }
}

private data class SpellFilterInputs(
    val query: String,
    val trait: String,
    val rank: Int?,
    val tradition: String?,
    val rarity: String?,
)
