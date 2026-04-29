package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.PerfTrace
import com.spellapp.core.model.CharacterProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CharacterListUiState(
    val characters: List<CharacterProfile> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
)

class CharacterListViewModel(
    private val characterCrudRepositoryProvider: () -> CharacterCrudRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CharacterListUiState())
    val uiState: StateFlow<CharacterListUiState> = _uiState.asStateFlow()

    @Volatile
    private var characterCrudRepository: CharacterCrudRepository? = null

    init {
        viewModelScope.launch {
            runCatching {
                PerfTrace.suspendSection("CharacterListViewModel.observeCharacters") {
                    val repository = withContext(Dispatchers.IO) {
                        characterCrudRepositoryProvider().also { loaded ->
                            characterCrudRepository = loaded
                        }
                    }
                    PerfTrace.firstEmission(
                        name = "CharacterListViewModel.characters",
                        source = repository.observeCharacters(),
                        sizeOf = List<CharacterProfile>::size,
                    )
                        .catch { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    loadError = error.message ?: "Characters could not be loaded.",
                                )
                            }
                        }
                        .collect { characters ->
                            _uiState.update {
                                it.copy(
                                    characters = characters,
                                    isLoading = false,
                                    loadError = null,
                                )
                            }
                        }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = error.message ?: "Characters could not be loaded.",
                    )
                }
            }
        }
    }

    fun deleteCharacter(characterId: Long) {
        viewModelScope.launch {
            val repository = withContext(Dispatchers.IO) {
                characterCrudRepository ?: characterCrudRepositoryProvider().also { loaded ->
                    characterCrudRepository = loaded
                }
            }
            repository.deleteCharacter(characterId)
        }
    }
}

class CharacterListViewModelFactory(
    private val characterCrudRepositoryProvider: () -> CharacterCrudRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CharacterListViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return CharacterListViewModel(
            characterCrudRepositoryProvider = characterCrudRepositoryProvider,
        ) as T
    }
}
