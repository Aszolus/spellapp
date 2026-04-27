package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CharacterListUiState(
    val characters: List<CharacterProfile> = emptyList(),
    val classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition> = emptyMap(),
)

class CharacterListViewModel(
    private val characterCrudRepository: CharacterCrudRepository,
    classDefinitionSource: CharacterClassDefinitionSource,
) : ViewModel() {
    private val classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition> =
        classDefinitionSource.allDefinitions().associateBy { it.characterClass }

    val uiState = characterCrudRepository.observeCharacters()
        .map { characters ->
            CharacterListUiState(
                characters = characters,
                classDefinitionsByClass = classDefinitionsByClass,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CharacterListUiState(
                classDefinitionsByClass = classDefinitionsByClass,
            ),
        )

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
