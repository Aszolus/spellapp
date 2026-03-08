package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.CharacterBuildOption
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
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    spellRepository: SpellRepository,
    knownSpellRepository: KnownSpellRepository,
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
    private val knownSpellsSeeder = DefaultKnownSpellsSeeder(
        spellRepository = spellRepository,
        knownSpellRepository = knownSpellRepository,
    )
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
            val characterId = characterCrudRepository.upsertCharacter(character)
            acceptedSpellSourceRepository.replaceAcceptedSources(
                characterId = characterId,
                sources = acceptedSourceBooks,
            )
            val shouldReconcileArchetypes = persistManagedBuildOptions(
                characterId = characterId,
                selectedBuildOptionIds = selectedBuildOptionIds,
            )
            if (shouldReconcileArchetypes) {
                reconcileArchetypeTracks(characterId, selectedBuildOptionIds)
            }
            knownSpellsSeeder.seedForCharacter(
                character = character.copy(id = characterId),
                acceptedSourceBooks = acceptedSourceBooks,
            )
            preparedSlotSyncRepository.syncPreparedSlotsForCharacter(characterId)
            isEditorVisible.update { false }
        }
    }

    fun deleteCharacter(characterId: Long) {
        viewModelScope.launch {
            characterCrudRepository.deleteCharacter(characterId)
        }
    }

    private suspend fun reconcileArchetypeTracks(
        characterId: Long,
        selectedBuildOptionIds: Set<String>,
    ) {
        val existingArchetypeTracks = castingTrackRepository.getCastingTracks(characterId)
            .filter { it.sourceType == CastingTrackSourceType.ARCHETYPE }
        val selectedArchetypes = archetypeSpellcastingPackages
            .filter { packageDef ->
                packageDef.dedicationOptionId in selectedBuildOptionIds &&
                    packageDef.supportsPreparedSpellcastingTrack()
            }
        val desiredTracksByKey = selectedArchetypes.associateBy { packageDef ->
            trackKeyForArchetype(packageDef.archetypeId)
        }

        existingArchetypeTracks
            .filterNot { track -> track.trackKey in desiredTracksByKey.keys }
            .forEach { track ->
                castingTrackRepository.deleteCastingTrack(
                    characterId = characterId,
                    trackKey = track.trackKey,
                )
            }

        desiredTracksByKey.forEach { (trackKey, packageDef) ->
            castingTrackRepository.upsertCastingTrack(
                CastingTrack(
                    characterId = characterId,
                    trackKey = trackKey,
                    sourceType = CastingTrackSourceType.ARCHETYPE,
                    sourceId = packageDef.label,
                    progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
                ),
            )
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

    private fun trackKeyForArchetype(archetypeId: String): String {
        return "archetype-$archetypeId"
    }

    private fun ArchetypeSpellcastingPackage.supportsPreparedSpellcastingTrack(): Boolean {
        return basicSpellcastingOptionId != null ||
            expertSpellcastingOptionId != null ||
            masterSpellcastingOptionId != null
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
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val spellRepository: SpellRepository,
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
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            spellRepository = spellRepository,
            knownSpellRepository = knownSpellRepository,
            classDefinitionSource = classDefinitionSource,
            archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
        ) as T
    }
}
