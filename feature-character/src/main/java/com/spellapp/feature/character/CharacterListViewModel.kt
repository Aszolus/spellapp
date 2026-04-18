package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CharacterBuildOption
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import com.spellapp.feature.character.spellcasting.RefreshSpellcastingProjectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CharacterListUiState(
    val characters: List<CharacterProfile> = emptyList(),
    val editingCharacter: CharacterProfile? = null,
    val editingSelectedBuildOptionIds: Set<String> = emptySet(),
    val editingAcceptedSourceBooks: Set<String> = emptySet(),
    val isEditorVisible: Boolean = false,
    val classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition> = emptyMap(),
    val availableClasses: List<CharacterClassDefinition> = emptyList(),
    val archetypeSpellcastingPackages: List<ArchetypeSpellcastingPackage> = emptyList(),
    val availableSpellSources: List<String> = emptyList(),
)

class CharacterListViewModel(
    private val characterCrudRepository: CharacterCrudRepository,
    private val characterBuildRepository: CharacterBuildRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    spellRepository: SpellRepository,
    private val refreshSpellcastingProjectionUseCase: RefreshSpellcastingProjectionUseCase,
    private val classDefinitionSource: CharacterClassDefinitionSource,
    private val archetypeSpellcastingCatalogSource: ArchetypeSpellcastingCatalogSource,
) : ViewModel() {
    private val editingCharacter = MutableStateFlow<CharacterProfile?>(null)
    private val editingSelectedBuildOptionIds = MutableStateFlow<Set<String>>(emptySet())
    private val editingAcceptedSourceBooks = MutableStateFlow<Set<String>>(emptySet())
    private val isEditorVisible = MutableStateFlow(false)
    private val classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition> =
        classDefinitionSource.allDefinitions().associateBy { it.characterClass }
    private val availableClasses: List<CharacterClassDefinition> =
        classDefinitionSource.phaseOneDefinitions()
    private val archetypeSpellcastingPackages: List<ArchetypeSpellcastingPackage> =
        archetypeSpellcastingCatalogSource.phaseOnePackages()
    private val managedArchetypeOptionIds: Set<String> =
        archetypeSpellcastingCatalogSource.managedOptionIds()
    private val availableSpellSources = spellRepository.observeAvailableSources()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
    private val editorState = combine(
        editingCharacter,
        editingSelectedBuildOptionIds,
        editingAcceptedSourceBooks,
        isEditorVisible,
    ) { editing, selectedOptionIds, acceptedSources, visible ->
        EditorState(
            editingCharacter = editing,
            selectedBuildOptionIds = selectedOptionIds,
            acceptedSourceBooks = acceptedSources,
            isVisible = visible,
        )
    }

    val uiState = combine(
        characterCrudRepository.observeCharacters(),
        editorState,
        availableSpellSources,
    ) { characters, editor, sources ->
        CharacterListUiState(
            characters = characters,
            editingCharacter = editor.editingCharacter,
            editingSelectedBuildOptionIds = editor.selectedBuildOptionIds,
            editingAcceptedSourceBooks = editor.acceptedSourceBooks,
            isEditorVisible = editor.isVisible,
            classDefinitionsByClass = classDefinitionsByClass,
            availableClasses = availableClasses,
            archetypeSpellcastingPackages = archetypeSpellcastingPackages,
            availableSpellSources = sources,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CharacterListUiState(),
    )

    fun onAddCharacterRequest() {
        editingCharacter.update { null }
        editingSelectedBuildOptionIds.update { emptySet() }
        editingAcceptedSourceBooks.update { availableSpellSources.value.toSet() }
        isEditorVisible.update { true }
    }

    fun onEditCharacterRequest(character: CharacterProfile) {
        editingCharacter.update { character }
        editingSelectedBuildOptionIds.update { emptySet() }
        editingAcceptedSourceBooks.update { emptySet() }
        isEditorVisible.update { true }
        viewModelScope.launch {
            val selectedOptionIds = characterBuildRepository.getBuildOptions(character.id)
                .map { it.optionId }
                .filter { optionId -> optionId in managedArchetypeOptionIds }
                .toSet()
            val acceptedSources = acceptedSpellSourceRepository.getAcceptedSources(character.id)
                .ifEmpty { availableSpellSources.value.toSet() }
            if (editingCharacter.value?.id == character.id) {
                editingSelectedBuildOptionIds.update { selectedOptionIds }
                editingAcceptedSourceBooks.update { acceptedSources }
            }
        }
    }

    fun dismissEditor() {
        isEditorVisible.update { false }
    }

    fun saveCharacter(
        character: CharacterProfile,
        selectedBuildOptionIds: Set<String>,
        acceptedSourceBooks: Set<String>,
    ) {
        viewModelScope.launch {
            val isNew = character.id == 0L
            val characterId = characterCrudRepository.upsertCharacter(character)
            acceptedSpellSourceRepository.replaceAcceptedSources(
                characterId = characterId,
                sources = acceptedSourceBooks,
            )
            val shouldReconcileArchetypes = persistManagedBuildOptions(
                characterId = characterId,
                selectedBuildOptionIds = selectedBuildOptionIds,
            )
            refreshSpellcastingProjectionUseCase.refreshCharacterSpellcasting(
                character = character.copy(id = characterId),
                selectedBuildOptionIds = selectedBuildOptionIds,
                acceptedSourceBooks = acceptedSourceBooks,
                isNewCharacter = isNew,
                reconcileArchetypeTracks = shouldReconcileArchetypes,
            )
            isEditorVisible.update { false }
        }
    }

    fun deleteCharacter(characterId: Long) {
        viewModelScope.launch {
            characterCrudRepository.deleteCharacter(characterId)
        }
    }

    private suspend fun persistManagedBuildOptions(
        characterId: Long,
        selectedBuildOptionIds: Set<String>,
    ): Boolean {
        val existingOptions = characterBuildRepository.getBuildOptions(characterId)
        val existingManagedOptions = existingOptions
            .filter { option -> option.optionId in managedArchetypeOptionIds }
        val selectedManagedOptionIds = selectedBuildOptionIds
            .filter { optionId -> optionId in managedArchetypeOptionIds }
            .toSet()
        val hasManagedState = existingManagedOptions.isNotEmpty() || selectedManagedOptionIds.isNotEmpty()
        if (!hasManagedState) {
            return false
        }

        val retainedOptions = existingOptions
            .filterNot { option -> option.optionId in managedArchetypeOptionIds }
        val managedOptions = selectedManagedOptionIds
            .sorted()
            .mapNotNull { optionId ->
                val optionType = archetypeSpellcastingCatalogSource.optionTypeForOptionId(optionId)
                    ?: return@mapNotNull null
                CharacterBuildOption(
                    characterId = characterId,
                    optionType = optionType,
                    optionId = optionId,
                )
            }

        characterBuildRepository.replaceBuildOptions(
            characterId = characterId,
            options = retainedOptions + managedOptions,
        )
        return true
    }

    private data class EditorState(
        val editingCharacter: CharacterProfile?,
        val selectedBuildOptionIds: Set<String>,
        val acceptedSourceBooks: Set<String>,
        val isVisible: Boolean,
    )
}

class CharacterListViewModelFactory(
    private val characterCrudRepository: CharacterCrudRepository,
    private val characterBuildRepository: CharacterBuildRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    private val spellRepository: SpellRepository,
    private val refreshSpellcastingProjectionUseCase: RefreshSpellcastingProjectionUseCase,
    private val classDefinitionSource: CharacterClassDefinitionSource = StaticCharacterClassDefinitionSource,
    private val archetypeSpellcastingCatalogSource: ArchetypeSpellcastingCatalogSource =
        StaticArchetypeSpellcastingCatalogSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CharacterListViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return CharacterListViewModel(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            spellRepository = spellRepository,
            refreshSpellcastingProjectionUseCase = refreshSpellcastingProjectionUseCase,
            classDefinitionSource = classDefinitionSource,
            archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
        ) as T
    }
}
