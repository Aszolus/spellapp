package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterListUiState(
    val characters: List<CharacterProfile> = emptyList(),
    val editingCharacter: CharacterProfile? = null,
    val isEditorVisible: Boolean = false,
    val classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition> = emptyMap(),
    val availableClasses: List<CharacterClassDefinition> = emptyList(),
)

class CharacterListViewModel(
    private val characterRepository: CharacterCrudRepository,
    private val classDefinitionSource: CharacterClassDefinitionSource,
) : ViewModel() {
    private val editingCharacter = MutableStateFlow<CharacterProfile?>(null)
    private val isEditorVisible = MutableStateFlow(false)
    private val classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition> =
        classDefinitionSource.allDefinitions().associateBy { it.characterClass }
    private val availableClasses: List<CharacterClassDefinition> =
        classDefinitionSource.phaseOneDefinitions()

    val uiState = combine(
        characterRepository.observeCharacters(),
        editingCharacter,
        isEditorVisible,
    ) { characters, editing, visible ->
        CharacterListUiState(
            characters = characters,
            editingCharacter = editing,
            isEditorVisible = visible,
            classDefinitionsByClass = classDefinitionsByClass,
            availableClasses = availableClasses,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CharacterListUiState(),
    )

    fun onAddCharacterRequest() {
        editingCharacter.update { null }
        isEditorVisible.update { true }
    }

    fun onEditCharacterRequest(character: CharacterProfile) {
        editingCharacter.update { character }
        isEditorVisible.update { true }
    }

    fun dismissEditor() {
        isEditorVisible.update { false }
    }

    fun saveCharacter(character: CharacterProfile) {
        viewModelScope.launch {
            characterRepository.upsertCharacter(character)
            isEditorVisible.update { false }
        }
    }

    fun deleteCharacter(characterId: Long) {
        viewModelScope.launch {
            characterRepository.deleteCharacter(characterId)
        }
    }
}

class CharacterListViewModelFactory(
    private val characterRepository: CharacterCrudRepository,
    private val classDefinitionSource: CharacterClassDefinitionSource = StaticCharacterClassDefinitionSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CharacterListViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return CharacterListViewModel(
            characterRepository = characterRepository,
            classDefinitionSource = classDefinitionSource,
        ) as T
    }
}
