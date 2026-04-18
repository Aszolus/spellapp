package com.spellapp.feature.spells

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.RulesReferenceRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.model.RulesReferenceCategory
import com.spellapp.core.model.RulesReferenceKey
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellRulesText
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
    val rulesText: String = "",
    val rulesTextReferences: List<SpellRulesReferenceUiState> = emptyList(),
)

data class SpellTraitLookupUiState(
    val label: String,
    val description: String? = null,
)

data class SpellRulesReferenceUiState(
    val label: String,
    val description: String? = null,
    val start: Int,
    val end: Int,
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
                    rulesText = "",
                    rulesTextReferences = emptyList(),
                )
            }
            val spell = spellRepository.getSpellDetail(spellId)
            val parsedRulesText = if (spell != null) {
                spellRulesTextRepository.getSpellRulesText(spellId)
                    ?: SpellRulesText(text = spell.description)
            } else {
                SpellRulesText(text = "")
            }
            val lookupEntries = if (spell != null) {
                val keys = buildSet {
                    spell.traits.forEach { trait ->
                        add(
                            RulesReferenceKey(
                                category = RulesReferenceCategory.TRAIT,
                                slug = trait,
                            ),
                        )
                    }
                    parsedRulesText.references.forEach { reference ->
                        add(reference.key)
                    }
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
                        val key = RulesReferenceKey(
                            category = RulesReferenceCategory.TRAIT,
                            slug = trait,
                        )
                        val entry = lookupEntries[key]
                        SpellTraitLookupUiState(
                            label = entry?.label ?: trait.toDisplayLabel(),
                            description = entry?.description,
                        )
                    },
                    rulesText = parsedRulesText.text,
                    rulesTextReferences = parsedRulesText.references.map { reference ->
                        SpellRulesReferenceUiState(
                            label = reference.label,
                            description = lookupEntries[reference.key]?.description,
                            start = reference.start,
                            end = reference.end,
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
