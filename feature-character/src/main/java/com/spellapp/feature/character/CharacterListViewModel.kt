package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
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
    val editingArchetypeTrackCount: Int = 0,
    val isEditorVisible: Boolean = false,
    val classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition> = emptyMap(),
    val availableClasses: List<CharacterClassDefinition> = emptyList(),
)

class CharacterListViewModel(
    private val characterRepository: CharacterCrudRepository,
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val classDefinitionSource: CharacterClassDefinitionSource,
) : ViewModel() {
    private val editingCharacter = MutableStateFlow<CharacterProfile?>(null)
    private val editingArchetypeTrackCount = MutableStateFlow(0)
    private val isEditorVisible = MutableStateFlow(false)
    private val classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition> =
        classDefinitionSource.allDefinitions().associateBy { it.characterClass }
    private val availableClasses: List<CharacterClassDefinition> =
        classDefinitionSource.phaseOneDefinitions()

    val uiState = combine(
        characterRepository.observeCharacters(),
        editingCharacter,
        editingArchetypeTrackCount,
        isEditorVisible,
    ) { characters, editing, archetypeCount, visible ->
        CharacterListUiState(
            characters = characters,
            editingCharacter = editing,
            editingArchetypeTrackCount = archetypeCount,
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
        editingArchetypeTrackCount.update { 0 }
        isEditorVisible.update { true }
    }

    fun onEditCharacterRequest(character: CharacterProfile) {
        editingCharacter.update { character }
        editingArchetypeTrackCount.update { 0 }
        isEditorVisible.update { true }
        viewModelScope.launch {
            val archetypeCount = castingTrackRepository.getCastingTracks(character.id)
                .count { it.sourceType == CastingTrackSourceType.ARCHETYPE }
            if (editingCharacter.value?.id == character.id) {
                editingArchetypeTrackCount.update { archetypeCount }
            }
        }
    }

    fun dismissEditor() {
        isEditorVisible.update { false }
    }

    fun saveCharacter(
        character: CharacterProfile,
        archetypeTrackCount: Int,
    ) {
        viewModelScope.launch {
            val characterId = characterRepository.upsertCharacter(character)
            reconcileArchetypeTracks(
                characterId = characterId,
                desiredCount = archetypeTrackCount.coerceAtLeast(0),
            )
            preparedSlotSyncRepository.syncPreparedSlotsForCharacter(characterId)
            isEditorVisible.update { false }
        }
    }

    fun deleteCharacter(characterId: Long) {
        viewModelScope.launch {
            characterRepository.deleteCharacter(characterId)
        }
    }

    private suspend fun reconcileArchetypeTracks(
        characterId: Long,
        desiredCount: Int,
    ) {
        val existingArchetypes = castingTrackRepository.getCastingTracks(characterId)
            .filter { it.sourceType == CastingTrackSourceType.ARCHETYPE }
        val currentCount = existingArchetypes.size

        if (currentCount < desiredCount) {
            repeat(desiredCount - currentCount) {
                val nextIndex = nextArchetypeIndex(
                    existingArchetypes = castingTrackRepository.getCastingTracks(characterId)
                        .filter { track -> track.sourceType == CastingTrackSourceType.ARCHETYPE },
                )
                castingTrackRepository.upsertCastingTrack(
                    CastingTrack(
                        characterId = characterId,
                        trackKey = "archetype-$nextIndex",
                        sourceType = CastingTrackSourceType.ARCHETYPE,
                        sourceId = "Archetype $nextIndex",
                        progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
                    ),
                )
            }
        } else if (currentCount > desiredCount) {
            val toRemove = existingArchetypes
                .sortedByDescending { archetypeTrackOrder(it.trackKey) }
                .take(currentCount - desiredCount)
            toRemove.forEach { track ->
                castingTrackRepository.deleteCastingTrack(
                    characterId = characterId,
                    trackKey = track.trackKey,
                )
            }
        }
    }

    private fun nextArchetypeIndex(existingArchetypes: List<CastingTrack>): Int {
        val used = existingArchetypes.map { archetypeTrackOrder(it.trackKey) }
            .filter { it > 0 }
            .toSet()
        var candidate = 1
        while (candidate in used) {
            candidate += 1
        }
        return candidate
    }

    private fun archetypeTrackOrder(trackKey: String): Int {
        return Regex("^archetype-(\\d+)$")
            .matchEntire(trackKey)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }
}

class CharacterListViewModelFactory(
    private val characterRepository: CharacterCrudRepository,
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val classDefinitionSource: CharacterClassDefinitionSource = StaticCharacterClassDefinitionSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CharacterListViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return CharacterListViewModel(
            characterRepository = characterRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
            classDefinitionSource = classDefinitionSource,
        ) as T
    }
}
