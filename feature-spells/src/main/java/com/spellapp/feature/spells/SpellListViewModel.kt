package com.spellapp.feature.spells

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.EmptyClassSpellcastingCatalogSource
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.KnownSpellOrigin
import com.spellapp.core.model.SpellAllowanceKind
import com.spellapp.core.model.SpellAllowancePolicy
import com.spellapp.core.model.SpellAllowanceSummary
import com.spellapp.core.model.allowanceRulesForTrack
import com.spellapp.core.model.buildSpellAllowanceSummaries
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpellListUiState(
    val queryInput: String = "",
    val traitQueryInput: String = "",
    val selectedRank: Int? = null,
    val selectedTradition: String? = null,
    val selectedRarities: Set<String> = emptySet(),
    val browserMode: SpellBrowserMode = SpellBrowserMode.BrowseCatalog(),
    val knownSpellIds: Set<String> = emptySet(),
    val knownSpellStatuses: Map<String, KnownSpellStatus> = emptyMap(),
    val signatureSpellIds: Set<String> = emptySet(),
    val canManageSignatureSpells: Boolean = false,
    val allKnownSpellsAreSignature: Boolean = false,
    val allowanceSummaries: List<SpellAllowanceSummary> = emptyList(),
    val availableTraits: List<String> = emptyList(),
    val pendingKnownSpellWarning: PendingKnownSpellWarning? = null,
)

data class KnownSpellStatus(
    val isLocked: Boolean = false,
    val label: String? = null,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SpellListViewModel(
    private val spellRepository: SpellRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val toggleKnownSpellUseCase: ToggleKnownSpellUseCase,
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        EmptyClassSpellcastingCatalogSource,
) : ViewModel() {
    @Volatile
    private var activeBrowserSessionId: Long? = null
    private val queryInput = MutableStateFlow("")
    private val traitQueryInput = MutableStateFlow("")
    private val selectedRank = MutableStateFlow<Int?>(null)
    private val selectedTradition = MutableStateFlow<String?>(null)
    private val selectedRarities = MutableStateFlow<Set<String>>(emptySet())
    private val browserMode = MutableStateFlow<SpellBrowserMode>(SpellBrowserMode.BrowseCatalog())
    private val pendingKnownSpellWarning = MutableStateFlow<PendingKnownSpellWarning?>(null)

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

    private val knownSpells = browserMode.flatMapLatest { mode ->
        when (mode) {
            is SpellBrowserMode.ManageKnownSpells -> {
                knownSpellRepository.observeKnownSpells(mode.characterId, mode.trackKey)
            }

            is SpellBrowserMode.AssignPreparedSlot,
            is SpellBrowserMode.BrowseCatalog,
            -> flowOf(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val knownSpellBaseRanksById = knownSpells.mapLatest { known ->
        val storedRanks = known.mapNotNull { knownSpell ->
            knownSpell.knownRank?.let { rank -> knownSpell.spellId to rank }
        }.toMap()
        val unresolvedIds = known
            .filter { knownSpell -> knownSpell.knownRank == null }
            .map { knownSpell -> knownSpell.spellId }
        storedRanks + spellRepository.getSpellRanks(unresolvedIds)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
    )

    private val allowanceSummaries = combine(
        browserMode,
        knownSpells,
        knownSpellBaseRanksById,
    ) { mode, known, ranksById ->
        val manageMode = mode as? SpellBrowserMode.ManageKnownSpells ?: return@combine emptyList()
        val sourceId = manageMode.trackSourceId ?: return@combine emptyList()
        val rules = classSpellcastingCatalogSource.allowanceRulesForTrack(
            trackKey = manageMode.trackKey,
            sourceId = sourceId,
        )
        buildSpellAllowanceSummaries(
            rules = rules,
            characterLevel = manageMode.characterLevel,
            knownSpells = known,
            knownSpellBaseRanksById = ranksById,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val acceptedSourceBooks = browserMode.flatMapLatest { mode ->
        when (mode) {
            is SpellBrowserMode.BrowseCatalog -> {
                mode.characterId?.let { characterId ->
                    acceptedSpellSourceRepository.observeAcceptedSources(characterId)
                } ?: flowOf(emptySet())
            }

            is SpellBrowserMode.ManageKnownSpells -> {
                acceptedSpellSourceRepository.observeAcceptedSources(mode.characterId)
            }

            is SpellBrowserMode.AssignPreparedSlot -> {
                acceptedSpellSourceRepository.observeAcceptedSources(mode.characterId)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet(),
    )

    private val availableTraits = spellRepository.observeAvailableTraits().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val filterUiState = combine(
        queryInput,
        traitQueryInput,
        selectedRank,
        selectedTradition,
        selectedRarities,
    ) { query, trait, rank, tradition, rarities ->
        SpellListUiState(
            queryInput = query,
            traitQueryInput = trait,
            selectedRank = rank,
            selectedTradition = tradition,
            selectedRarities = rarities,
        )
    }

    private val spellKnowledgeContext = combine(
        knownSpellIds,
        knownSpells,
        allowanceSummaries,
    ) { knownIds, known, allowance ->
        val allKnownSpellsAreSignature = allowance.any { summary ->
            summary.kind == SpellAllowanceKind.SIGNATURE_SPELLS &&
                summary.policy == SpellAllowancePolicy.ALL_KNOWN
        }
        val canManageSignatureSpells = allowance.any { summary ->
            summary.kind == SpellAllowanceKind.SIGNATURE_SPELLS
        } && !allKnownSpellsAreSignature
        SpellKnowledgeContext(
            knownSpellIds = knownIds,
            knownSpellStatuses = known.statusesBySpellId(),
            signatureSpellIds = if (allKnownSpellsAreSignature) {
                known.map { knownSpell -> knownSpell.spellId }.toSet()
            } else {
                known.filter { knownSpell -> knownSpell.isSignature }
                    .map { knownSpell -> knownSpell.spellId }
                    .toSet()
            },
            canManageSignatureSpells = canManageSignatureSpells,
            allKnownSpellsAreSignature = allKnownSpellsAreSignature,
            allowanceSummaries = allowance,
        )
    }

    val uiState = combine(
        filterUiState,
        browserMode,
        spellKnowledgeContext,
        availableTraits,
        pendingKnownSpellWarning,
    ) { filters, mode, knowledge, traits, pendingWarning ->
        filters.copy(
            browserMode = mode,
            knownSpellIds = knowledge.knownSpellIds,
            knownSpellStatuses = knowledge.knownSpellStatuses,
            signatureSpellIds = knowledge.signatureSpellIds,
            canManageSignatureSpells = knowledge.canManageSignatureSpells,
            allKnownSpellsAreSignature = knowledge.allKnownSpellsAreSignature,
            allowanceSummaries = knowledge.allowanceSummaries,
            availableTraits = traits,
            pendingKnownSpellWarning = pendingWarning,
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
        selectedRarities,
    ) { query, trait, rank, tradition, rarities ->
        SpellFilterInputs(
            query = query,
            trait = trait,
            rank = rank,
            tradition = tradition,
            rarities = rarities,
        )
    }

    val spells = combine(
        filterInputs,
        browserMode,
        knownSpellIds,
        acceptedSourceBooks,
    ) { filters, mode, knownIds, acceptedSources ->
        SpellQueryContext(
            filters = filters,
            browserMode = mode,
            knownSpellIds = knownIds,
            acceptedSourceBooks = acceptedSources,
        )
    }.flatMapLatest { query ->
        spellRepository.observeSpells(
            query = query.filters.query,
            rank = query.filters.rank.takeUnless { query.browserMode is SpellBrowserMode.AssignPreparedSlot },
            tradition = query.filters.tradition,
            rarity = null,
            trait = query.filters.trait,
        ).map { sourceSpells ->
            var filtered = sourceSpells
            if (query.acceptedSourceBooks.isNotEmpty()) {
                filtered = filtered.filter { spell -> spell.sourceBook in query.acceptedSourceBooks }
            }
            if (query.filters.rarities.isNotEmpty()) {
                filtered = filtered.filter { spell ->
                    spell.rarity.lowercase() in query.filters.rarities
                }
            }
            if (query.filters.rank != null) {
                filtered = filtered.filter { spell -> spell.rank == query.filters.rank }
            }
            when (query.browserMode) {
                is SpellBrowserMode.AssignPreparedSlot -> {
                    filtered = filtered.filter { spell -> spell.id in query.knownSpellIds }
                    filtered = filtered.filter { spell ->
                        if (query.browserMode.slotRank == 0) {
                            spell.rank == 0
                        } else {
                            spell.rank in 1..query.browserMode.slotRank
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

    fun openBrowserMode(
        mode: SpellBrowserMode,
        sessionId: Long,
    ) {
        if (activeBrowserSessionId != sessionId) {
            activeBrowserSessionId = sessionId
            queryInput.update { "" }
            traitQueryInput.update { "" }
            selectedRarities.update { emptySet() }
            pendingKnownSpellWarning.update { null }
            selectedRank.update {
                when (mode) {
                    is SpellBrowserMode.ManageKnownSpells -> mode.initialRank
                    is SpellBrowserMode.AssignPreparedSlot,
                    is SpellBrowserMode.BrowseCatalog,
                    -> null
                }
            }
            selectedTradition.update {
                when (mode) {
                    is SpellBrowserMode.ManageKnownSpells -> mode.preferredTradition
                    is SpellBrowserMode.AssignPreparedSlot -> mode.preferredTradition
                    is SpellBrowserMode.BrowseCatalog -> null
                }
            }
        }
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
        selectedRarities.update { current ->
            current.toMutableSet().apply {
                if (!add(rarity)) {
                    remove(rarity)
                }
            }
        }
    }

    fun clearTraitFilter() {
        traitQueryInput.update { "" }
    }

    fun clearRankFilter() {
        selectedRank.update { null }
    }

    fun clearTraditionFilter() {
        selectedTradition.update { null }
    }

    fun clearRarityFilter(rarity: String) {
        selectedRarities.update { current -> current - rarity }
    }

    fun clearTextFilters() {
        queryInput.update { "" }
    }

    fun clearStructuredFilters() {
        traitQueryInput.update { "" }
        selectedRank.update { null }
        selectedTradition.update { null }
        selectedRarities.update { emptySet() }
    }

    fun clearAllFilters() {
        queryInput.update { "" }
        clearStructuredFilters()
    }

    fun toggleKnownSpell(spellId: String) {
        val mode = browserMode.value as? SpellBrowserMode.ManageKnownSpells ?: return
        if (knownSpells.value.any { knownSpell -> knownSpell.spellId == spellId && knownSpell.isLocked }) {
            return
        }
        viewModelScope.launch {
            val warning = toggleKnownSpellUseCase.toggle(mode, spellId)
            if (warning != null) {
                pendingKnownSpellWarning.update { warning }
            }
        }
    }

    fun toggleSignatureSpell(spellId: String) {
        val mode = browserMode.value as? SpellBrowserMode.ManageKnownSpells ?: return
        if (spellId !in knownSpellIds.value) return
        val summaries = allowanceSummaries.value
        val allKnownSpellsAreSignature = summaries.any { summary ->
            summary.kind == SpellAllowanceKind.SIGNATURE_SPELLS &&
                summary.policy == SpellAllowancePolicy.ALL_KNOWN
        }
        if (allKnownSpellsAreSignature) return
        val hasSignatureAllowance = summaries.any { summary ->
            summary.kind == SpellAllowanceKind.SIGNATURE_SPELLS
        }
        if (!hasSignatureAllowance) return

        val currentlySignature = knownSpells.value.any { knownSpell ->
            knownSpell.spellId == spellId && knownSpell.isSignature
        }
        viewModelScope.launch {
            knownSpellRepository.setSignatureSpell(
                characterId = mode.characterId,
                trackKey = mode.trackKey,
                spellId = spellId,
                isSignature = !currentlySignature,
            )
        }
    }

    fun learnAllVisibleSpells() {
        val mode = browserMode.value as? SpellBrowserMode.ManageKnownSpells ?: return
        val spellIds = spells.value
            .asSequence()
            .map { it.id }
            .filterNot { it in knownSpellIds.value }
            .toList()
        if (spellIds.isEmpty()) return
        viewModelScope.launch {
            val warning = toggleKnownSpellUseCase.addAll(mode, spellIds)
            if (warning != null) {
                pendingKnownSpellWarning.update { warning }
            }
        }
    }

    fun unlearnAllVisibleSpells() {
        val mode = browserMode.value as? SpellBrowserMode.ManageKnownSpells ?: return
        val spellIds = spells.value
            .asSequence()
            .map { it.id }
            .filter { it in knownSpellIds.value && it !in lockedKnownSpellIds(knownSpells.value) }
            .toList()
        if (spellIds.isEmpty()) return
        viewModelScope.launch {
            toggleKnownSpellUseCase.removeAll(mode, spellIds)
            pendingKnownSpellWarning.update { null }
        }
    }

    fun confirmKnownSpellWarning() {
        val mode = browserMode.value as? SpellBrowserMode.ManageKnownSpells ?: return
        val warning = pendingKnownSpellWarning.value ?: return
        viewModelScope.launch {
            toggleKnownSpellUseCase.confirmAdd(mode, warning.spellIds)
            pendingKnownSpellWarning.update { null }
        }
    }

    fun dismissKnownSpellWarning() {
        pendingKnownSpellWarning.update { null }
    }
}

class SpellListViewModelFactory(
    private val spellRepository: SpellRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val toggleKnownSpellUseCase: ToggleKnownSpellUseCase,
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        EmptyClassSpellcastingCatalogSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(SpellListViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return SpellListViewModel(
            spellRepository = spellRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            knownSpellRepository = knownSpellRepository,
            toggleKnownSpellUseCase = toggleKnownSpellUseCase,
            classSpellcastingCatalogSource = classSpellcastingCatalogSource,
        ) as T
    }
}

private data class SpellFilterInputs(
    val query: String,
    val trait: String,
    val rank: Int?,
    val tradition: String?,
    val rarities: Set<String>,
)

private data class SpellQueryContext(
    val filters: SpellFilterInputs,
    val browserMode: SpellBrowserMode,
    val knownSpellIds: Set<String>,
    val acceptedSourceBooks: Set<String>,
)

private data class SpellKnowledgeContext(
    val knownSpellIds: Set<String>,
    val knownSpellStatuses: Map<String, KnownSpellStatus>,
    val signatureSpellIds: Set<String>,
    val canManageSignatureSpells: Boolean,
    val allKnownSpellsAreSignature: Boolean,
    val allowanceSummaries: List<SpellAllowanceSummary>,
)

private fun List<KnownSpell>.statusesBySpellId(): Map<String, KnownSpellStatus> {
    return groupBy { knownSpell -> knownSpell.spellId }.mapValues { (_, entries) ->
        val isLocked = entries.any { knownSpell -> knownSpell.isLocked }
        val origin = entries
            .map { knownSpell -> knownSpell.origin }
            .minByOrNull(::originPriority)
            ?: KnownSpellOrigin.MANUAL
        KnownSpellStatus(
            isLocked = isLocked,
            label = origin.statusLabel(isLocked),
        )
    }
}

private fun lockedKnownSpellIds(knownSpells: List<KnownSpell>): Set<String> {
    return knownSpells
        .filter { knownSpell -> knownSpell.isLocked }
        .map { knownSpell -> knownSpell.spellId }
        .toSet()
}

private fun originPriority(origin: KnownSpellOrigin): Int {
    return when (origin) {
        KnownSpellOrigin.SUBCLASS -> 0
        KnownSpellOrigin.CLASS -> 1
        KnownSpellOrigin.ARCHETYPE -> 2
        KnownSpellOrigin.GM_GRANT -> 3
        KnownSpellOrigin.CUSTOM -> 4
        KnownSpellOrigin.MANUAL -> 5
    }
}

private fun KnownSpellOrigin.statusLabel(isLocked: Boolean): String? {
    return when (this) {
        KnownSpellOrigin.CLASS -> if (isLocked) "Always known (class)" else "Class access"
        KnownSpellOrigin.SUBCLASS -> if (isLocked) "Always known (subclass)" else "Subclass granted"
        KnownSpellOrigin.ARCHETYPE -> if (isLocked) "Always known (archetype)" else "Archetype access"
        KnownSpellOrigin.GM_GRANT -> if (isLocked) "Locked GM grant" else "GM grant"
        KnownSpellOrigin.CUSTOM -> "Custom"
        KnownSpellOrigin.MANUAL -> if (isLocked) "Locked" else null
    }
}
