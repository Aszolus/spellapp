package com.spellapp.feature.spells

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.RulesReferenceRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.model.CompendiumReferenceKey
import com.spellapp.core.model.RulesReferenceCategory
import com.spellapp.core.model.RulesReferenceKey
import com.spellapp.core.model.RulesTextDocument
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.TraitReferenceKey
import com.spellapp.core.model.referenceKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpellDetailUiState(
    val spell: SpellDetail? = null,
    val isLoading: Boolean = true,
    val heightenedAt: Int? = null,
    val traitLookups: List<SpellTraitLookupUiState> = emptyList(),
    val rulesDocument: RulesTextDocument = RulesTextDocument(),
    val referenceLookups: Map<RulesReferenceKey, SpellReferenceLookupUiState> = emptyMap(),
)

data class SpellTraitLookupUiState(
    val label: String,
    val document: RulesTextDocument? = null,
)

data class SpellReferenceLookupUiState(
    val key: RulesReferenceKey,
    val category: RulesReferenceCategory,
    val label: String,
    val document: RulesTextDocument? = null,
)

class SpellDetailViewModel(
    private val spellId: String,
    private val spellRepository: SpellRepository,
    private val rulesReferenceRepository: RulesReferenceRepository,
    private val spellRulesTextRepository: SpellRulesTextRepository,
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
                    traitLookups = emptyList(),
                    rulesDocument = RulesTextDocument(),
                    referenceLookups = emptyMap(),
                )
            }
            val spell = spellRepository.getSpellDetail(spellId)
            val spellRank = spell?.let { detail ->
                _uiState.value.heightenedAt ?: detail.rank.takeIf { it > 0 } ?: 1
            }
            val parsedRulesText = if (spell != null) {
                spellRulesTextRepository.getSpellRulesText(
                    spellId = spellId,
                    spellRank = spellRank,
                )
                    ?: RulesTextDocument.fromPlainText(spell.description)
            } else {
                RulesTextDocument()
            }
            val lookupEntries = if (spell != null) {
                val keys = buildSet {
                    spell.traits.forEach { trait ->
                        add(TraitReferenceKey.fromSlug(trait))
                    }
                    addAll(parsedRulesText.referenceKeys())
                }
                rulesReferenceRepository.getEntries(keys)
            } else {
                emptyMap()
            }
            _uiState.update {
                it.copy(
                    spell = spell,
                    isLoading = false,
                    traitLookups = spell?.traits.orEmpty().map { trait ->
                        val key = TraitReferenceKey.fromSlug(trait)
                        val entry = lookupEntries[key]
                        SpellTraitLookupUiState(
                            label = entry?.label ?: trait.toDisplayLabel(),
                            document = entry?.document,
                        )
                    },
                    rulesDocument = parsedRulesText,
                    referenceLookups = lookupEntries
                        .filterKeys { it is CompendiumReferenceKey }
                        .mapValues { (key, entry) ->
                            SpellReferenceLookupUiState(
                                key = key,
                                category = entry.key.category,
                                label = entry.label,
                                document = entry.document,
                            )
                        },
                )
            }
        }
    }
}

private fun String.toDisplayLabel(): String {
    return split('-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { firstChar ->
                if (firstChar.isLowerCase()) firstChar.titlecase() else firstChar.toString()
            }
        }
}

class SpellDetailViewModelFactory(
    private val spellId: String,
    private val spellRepository: SpellRepository,
    private val rulesReferenceRepository: RulesReferenceRepository,
    private val spellRulesTextRepository: SpellRulesTextRepository,
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
            rulesReferenceRepository = rulesReferenceRepository,
            spellRulesTextRepository = spellRulesTextRepository,
            initialHeightenedAt = initialHeightenedAt,
        ) as T
    }
}
