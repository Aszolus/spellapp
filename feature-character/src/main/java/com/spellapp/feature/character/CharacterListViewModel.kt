package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.normalizeClassId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CharacterListUiState(
    val characters: List<CharacterProfile> = emptyList(),
    val classDefinitionsByClass: Map<String, CharacterClassDefinition> = emptyMap(),
)

class CharacterListViewModel(
    private val characterCrudRepository: CharacterCrudRepository,
    private val classDefinitionSource: CharacterClassDefinitionSource,
) : ViewModel() {
    private val classDefinitionsByClass = MutableStateFlow<Map<String, CharacterClassDefinition>>(emptyMap())

    val uiState = combine(
        characterCrudRepository.observeCharacters(),
        classDefinitionsByClass,
    ) { characters, definitionsByClass ->
            CharacterListUiState(
                characters = characters,
                classDefinitionsByClass = definitionsByClass,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CharacterListUiState(),
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            classDefinitionsByClass.value = classDefinitionSource.allDefinitions()
                .associateBy { normalizeClassId(it.classId) }
        }
    }

    fun deleteCharacter(characterId: Long) {
        viewModelScope.launch {
            characterCrudRepository.deleteCharacter(characterId)
        }
    }
}

class CharacterListViewModelFactory(
    private val characterCrudRepository: CharacterCrudRepository,
    private val classDefinitionSource: CharacterClassDefinitionSource = StaticCharacterClassDefinitionSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CharacterListViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return CharacterListViewModel(
            characterCrudRepository = characterCrudRepository,
            classDefinitionSource = classDefinitionSource,
        ) as T
    }
}
