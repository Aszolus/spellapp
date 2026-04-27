package com.spellapp.feature.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CastingStyle
import com.spellapp.core.model.CharacterBuildOption
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.ClassChoice
import com.spellapp.core.model.ClassChoiceGroup
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.EmptyClassSpellcastingCatalogSource
import com.spellapp.core.model.PrimaryTrackDefinition
import com.spellapp.core.model.SpellAllowanceKind
import com.spellapp.core.model.SpellAllowancePolicy
import com.spellapp.core.model.SpellAllowanceRule
import com.spellapp.core.model.SpellcastingTradition
import com.spellapp.core.model.countsAtLevel
import com.spellapp.core.model.managedOptionIds
import com.spellapp.core.model.normalizeClassId
import com.spellapp.core.model.optionTypeForOptionId
import com.spellapp.core.model.totalAtLevel
import com.spellapp.feature.character.spellcasting.RefreshSpellcastingProjectionUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class CharacterBuilderUiState(
    val characterId: Long = 0L,
    val isNewCharacter: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val loadError: String? = null,
    val saveError: String? = null,
    val name: String = "",
    val levelText: String = "1",
    val selectedClassId: String = "wizard",
    val selectedAncestryId: String? = null,
    val selectedHeritageId: String? = null,
    val selectedBackgroundId: String? = null,
    val selectedFeatSlotOptions: Map<String, String> = emptyMap(),
    val keyAbility: AbilityScore = AbilityScore.INTELLIGENCE,
    val spellDcText: String = "10",
    val spellAttackText: String = "0",
    val legacyTerminologyEnabled: Boolean = false,
    val saveAttempted: Boolean = false,
    val selectedBuildOptionIds: Set<String> = emptySet(),
    val acceptedSourceBooks: Set<String> = emptySet(),
    val availableSpellSources: List<String> = emptyList(),
    val classDefinitionsByClass: Map<String, CharacterClassDefinition> = emptyMap(),
    val availableClasses: List<CharacterClassDefinition> = emptyList(),
    val availableAncestries: List<BuilderAncestryRecord> = emptyList(),
    val availableHeritages: List<BuilderHeritageRecord> = emptyList(),
    val availableBackgrounds: List<BuilderBackgroundRecord> = emptyList(),
    val expectedFeatSlots: List<BuilderFeatSlot> = emptyList(),
    val featIndexById: Map<String, BuilderFeatIndexRecord> = emptyMap(),
    val featCandidatesBySlotId: Map<String, List<BuilderFeatIndexRecord>> = emptyMap(),
    val builderWarningLines: List<String> = emptyList(),
    val classChoiceGroups: List<ClassChoiceGroup> = emptyList(),
    val missingRequiredClassChoices: List<ClassChoiceGroup> = emptyList(),
    val selectedClassChoices: List<ClassChoice> = emptyList(),
    val classPreviewLines: List<String> = emptyList(),
    val archetypeSpellcastingPackages: List<ArchetypeSpellcastingPackage> = emptyList(),
    val selectedArchetypePackages: List<ArchetypeSpellcastingPackage> = emptyList(),
    val availableArchetypePackages: List<ArchetypeSpellcastingPackage> = emptyList(),
    val sections: List<CharacterBuilderSectionSummary> = emptyList(),
    val expandedSection: CharacterBuilderSectionId? = CharacterBuilderSectionId.IDENTITY,
    val isDirty: Boolean = false,
    val canSave: Boolean = false,
) {
    val level: Int? get() = levelText.toIntOrNull()?.takeIf { it in 1..20 }
    val spellDc: Int? get() = spellDcText.toIntOrNull()?.takeIf { it in 0..99 }
    val spellAttack: Int? get() = spellAttackText.toIntOrNull()?.takeIf { it in -99..99 }
    val nameInvalid: Boolean get() = name.isBlank()
    val levelInvalid: Boolean get() = level == null
    val spellDcInvalid: Boolean get() = spellDc == null
    val spellAttackInvalid: Boolean get() = spellAttack == null
}

data class CharacterBuilderSectionSummary(
    val id: CharacterBuilderSectionId,
    val title: String,
    val status: CharacterBuilderSectionStatus,
    val summary: String,
    val validationMessage: String? = null,
)

enum class CharacterBuilderSectionId {
    IDENTITY,
    ANCESTRY_HERITAGE,
    BACKGROUND,
    CLASS_SPELLCASTING,
    FEATS,
    CASTING_STATS,
    SPELL_SOURCES,
    ARCHETYPE_SPELLCASTING,
    PREFERENCES,
}

enum class CharacterBuilderSectionStatus {
    COMPLETE,
    NEEDS_REVIEW,
    OPTIONAL,
    BLOCKED,
}

class CharacterBuilderViewModel(
    private val characterId: Long,
    private val characterCrudRepository: CharacterCrudRepository,
    private val characterBuildRepository: CharacterBuildRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    private val spellRepository: SpellRepository,
    private val refreshSpellcastingProjectionUseCase: RefreshSpellcastingProjectionUseCase,
    private val classDefinitionSource: CharacterClassDefinitionSource,
    private val characterBuilderCatalogSource: CharacterBuilderCatalogSource,
    private val archetypeSpellcastingCatalogSource: ArchetypeSpellcastingCatalogSource,
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource,
) : ViewModel() {
    private val availableSpellSources = spellRepository.observeAvailableSources()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
    private val classDefinitionsByClass: Map<String, CharacterClassDefinition> =
        classDefinitionSource.allDefinitions().associateBy { normalizeClassId(it.classId) }
    private val availableClasses: List<CharacterClassDefinition> =
        classDefinitionSource.phaseOneDefinitions()
    private var builderCatalog: CharacterBuilderCatalog? = null
    private val archetypeSpellcastingPackages: List<ArchetypeSpellcastingPackage> =
        archetypeSpellcastingCatalogSource.phaseOnePackages()
    private val managedArchetypeOptionIds: Set<String> =
        archetypeSpellcastingCatalogSource.managedOptionIds()
    private val managedClassChoiceOptionIds: Set<String> =
        classSpellcastingCatalogSource.managedOptionIds()
    private val managedBuildOptionIds: Set<String> =
        managedArchetypeOptionIds + managedClassChoiceOptionIds

    private val _uiState = MutableStateFlow(CharacterBuilderUiState())
    val uiState: StateFlow<CharacterBuilderUiState> = _uiState.asStateFlow()

    private val _saveEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val saveEvents: SharedFlow<Long> = _saveEvents.asSharedFlow()

    private var initialSnapshot: CharacterBuilderSnapshot? = null

    init {
        viewModelScope.launch {
            loadDraft()
        }
        viewModelScope.launch {
            availableSpellSources.collect { sources ->
                updateState { current ->
                    current.copy(availableSpellSources = sources)
                }
            }
        }
    }

    fun updateName(value: String) {
        updateState { it.copy(name = value, saveError = null) }
    }

    fun updateLevel(value: String) {
        updateState { it.copy(levelText = value.filter(Char::isDigit).take(2), saveError = null) }
    }

    fun selectAncestry(ancestryId: String) {
        updateState { current ->
            val normalized = ancestryId.trim()
            val heritageStillValid = builderCatalog
                ?.heritagesForAncestry(normalized)
                .orEmpty()
                .any { heritage -> heritage.id == current.selectedHeritageId }
            current.copy(
                selectedAncestryId = normalized,
                selectedHeritageId = current.selectedHeritageId.takeIf { heritageStillValid },
                saveError = null,
            )
        }
    }

    fun selectHeritage(heritageId: String) {
        updateState { it.copy(selectedHeritageId = heritageId.trim(), saveError = null) }
    }

    fun selectBackground(backgroundId: String) {
        updateState { it.copy(selectedBackgroundId = backgroundId.trim(), saveError = null) }
    }

    fun selectFeatForSlot(slotId: String, featId: String?) {
        updateState { current ->
            current.copy(
                selectedFeatSlotOptions = current.selectedFeatSlotOptions.toMutableMap().apply {
                    if (featId.isNullOrBlank()) remove(slotId) else put(slotId, featId)
                },
                saveError = null,
            )
        }
    }

    fun selectClass(classId: String) {
        updateState { current ->
            current.copy(
                selectedClassId = normalizeClassId(classId),
                keyAbility = defaultKeyAbility(classId, classDefinitionsByClass),
                selectedBuildOptionIds = current.selectedBuildOptionIds - managedClassChoiceOptionIds,
                saveError = null,
            )
        }
    }

    fun selectClassChoice(
        group: ClassChoiceGroup,
        choice: ClassChoice,
    ) {
        updateState { current ->
            val groupOptionIds = group.choices.map { it.optionId }.toSet()
            current.copy(
                selectedBuildOptionIds = (current.selectedBuildOptionIds - groupOptionIds) + choice.optionId,
                keyAbility = choice.keyAbility ?: current.keyAbility,
                saveError = null,
            )
        }
    }

    fun selectKeyAbility(ability: AbilityScore) {
        updateState { it.copy(keyAbility = ability, saveError = null) }
    }

    fun updateSpellDc(value: String) {
        updateState { it.copy(spellDcText = sanitizeSignedNumber(value, maxLength = 2), saveError = null) }
    }

    fun updateSpellAttack(value: String) {
        updateState { it.copy(spellAttackText = sanitizeSignedNumber(value, maxLength = 3), saveError = null) }
    }

    fun setLegacyTerminologyEnabled(enabled: Boolean) {
        updateState { it.copy(legacyTerminologyEnabled = enabled, saveError = null) }
    }

    fun setAcceptedSourceBooks(sources: Set<String>) {
        updateState { it.copy(acceptedSourceBooks = sources, saveError = null) }
    }

    fun toggleSourceBook(source: String, checked: Boolean) {
        updateState { current ->
            current.copy(
                acceptedSourceBooks = current.acceptedSourceBooks.toMutableSet().apply {
                    if (checked) add(source) else remove(source)
                }.toSet(),
                saveError = null,
            )
        }
    }

    fun toggleArchetypeTier(
        packageDef: ArchetypeSpellcastingPackage,
        tier: ArchetypeTier,
        turnOn: Boolean,
    ) {
        updateState { current ->
            val nextSelection = current.selectedBuildOptionIds.toMutableSet()
            val tiers = ArchetypeTier.values()
            if (turnOn) {
                tiers.takeWhile { it != tier }.plus(tier).forEach { selectedTier ->
                    packageDef.optionIdFor(selectedTier)?.let { nextSelection += it }
                }
            } else {
                tiers.dropWhile { it != tier }.forEach { selectedTier ->
                    packageDef.optionIdFor(selectedTier)?.let { nextSelection -= it }
                }
            }
            current.copy(
                selectedBuildOptionIds = nextSelection,
                saveError = null,
            )
        }
    }

    fun toggleSection(sectionId: CharacterBuilderSectionId) {
        updateState { current ->
            current.copy(
                expandedSection = if (current.expandedSection == sectionId) null else sectionId,
            )
        }
    }

    fun save() {
        val attempted = derive(_uiState.value.copy(saveAttempted = true, saveError = null))
        _uiState.value = attempted
        if (!attempted.canSave) {
            return
        }
        viewModelScope.launch {
            updateState { it.copy(isSaving = true, saveError = null) }
            runCatching {
                val profile = CharacterProfile(
                    id = attempted.characterId,
                    name = attempted.name.trim(),
                    level = attempted.level ?: 1,
                    classId = attempted.selectedClassId,
                    keyAbility = attempted.keyAbility,
                    spellDc = attempted.spellDc ?: 10,
                    spellAttackModifier = attempted.spellAttack ?: 0,
                    legacyTerminologyEnabled = attempted.legacyTerminologyEnabled,
                )
                val isNew = profile.id == 0L
                val savedCharacterId = characterCrudRepository.upsertCharacter(profile)
                characterBuildRepository.upsertBuildIdentity(
                    com.spellapp.core.model.CharacterBuildIdentity(
                        characterId = savedCharacterId,
                        ancestryId = attempted.selectedAncestryId,
                        heritageId = attempted.selectedHeritageId,
                        backgroundId = attempted.selectedBackgroundId,
                    ),
                )
                acceptedSpellSourceRepository.replaceAcceptedSources(
                    characterId = savedCharacterId,
                    sources = attempted.acceptedSourceBooks,
                )
                val shouldReconcileArchetypes = persistManagedBuildOptions(
                    characterId = savedCharacterId,
                    selectedBuildOptionIds = attempted.selectedBuildOptionIds,
                    selectedFeatSlotOptions = attempted.selectedFeatSlotOptions,
                    selectedAncestryId = attempted.selectedAncestryId,
                    selectedHeritageId = attempted.selectedHeritageId,
                    selectedBackgroundId = attempted.selectedBackgroundId,
                )
                refreshSpellcastingProjectionUseCase.refreshCharacterSpellcasting(
                    character = profile.copy(id = savedCharacterId),
                    selectedBuildOptionIds = attempted.selectedBuildOptionIds,
                    acceptedSourceBooks = attempted.acceptedSourceBooks,
                    isNewCharacter = isNew,
                    reconcileArchetypeTracks = shouldReconcileArchetypes,
                )
                savedCharacterId
            }.onSuccess { savedCharacterId ->
                updateState {
                    val savedState = it.copy(
                        characterId = savedCharacterId,
                        isNewCharacter = false,
                        isSaving = false,
                    )
                    initialSnapshot = snapshot(savedState)
                    savedState
                }
                _saveEvents.emit(savedCharacterId)
            }.onFailure { error ->
                updateState {
                    it.copy(
                        isSaving = false,
                        saveError = error.message ?: "Character could not be saved.",
                    )
                }
            }
        }
    }

    private suspend fun loadDraft() {
        val sources = spellRepository.observeAvailableSources().first()
        val catalogResult = characterBuilderCatalogSource.loadCatalog()
        builderCatalog = catalogResult.catalog
        val isNew = characterId == 0L
        val existingCharacter = if (isNew) null else characterCrudRepository.getCharacter(characterId)
        if (!isNew && existingCharacter == null) {
            _uiState.value = derive(
                _uiState.value.copy(
                    isLoading = false,
                    loadError = "Character could not be found.",
                    availableSpellSources = sources,
                    classDefinitionsByClass = classDefinitionsByClass,
                    availableClasses = availableClasses,
                    archetypeSpellcastingPackages = archetypeSpellcastingPackages,
                ),
            )
            return
        }
        if (catalogResult.loadError != null || catalogResult.catalog == null) {
            _uiState.value = derive(
                _uiState.value.copy(
                    isLoading = false,
                    loadError = catalogResult.loadError ?: "Character builder catalog could not be loaded.",
                    availableSpellSources = sources,
                    classDefinitionsByClass = classDefinitionsByClass,
                    availableClasses = availableClasses,
                    archetypeSpellcastingPackages = archetypeSpellcastingPackages,
                ),
            )
            return
        }

        val initialClassId = existingCharacter?.classId
            ?: availableClasses.firstOrNull()?.classId
            ?: "wizard"
        val existingIdentity = if (isNew) null else characterBuildRepository.getBuildIdentity(characterId)
        val existingOptions = if (isNew) emptyList() else characterBuildRepository.getBuildOptions(characterId)
        val selectedOptionIds = if (isNew) {
            emptySet()
        } else {
            existingOptions
                .map { it.optionId }
                .filter { optionId -> optionId in managedBuildOptionIds }
                .toSet()
        }
        val selectedFeatSlotOptions = existingOptions
            .mapNotNull(::featSlotSelectionFromOption)
            .toMap()
        val acceptedSources = if (isNew) {
            sources.toSet()
        } else {
            acceptedSpellSourceRepository.getAcceptedSources(characterId)
                .ifEmpty { sources.toSet() }
        }
        val baseState = CharacterBuilderUiState(
            characterId = existingCharacter?.id ?: 0L,
            isNewCharacter = isNew,
            isLoading = false,
            name = existingCharacter?.name.orEmpty(),
            levelText = (existingCharacter?.level ?: 1).toString(),
            selectedClassId = initialClassId,
            selectedAncestryId = existingIdentity?.ancestryId,
            selectedHeritageId = existingIdentity?.heritageId,
            selectedBackgroundId = existingIdentity?.backgroundId,
            selectedFeatSlotOptions = selectedFeatSlotOptions,
            keyAbility = existingCharacter?.keyAbility
                ?: defaultKeyAbility(initialClassId, classDefinitionsByClass),
            spellDcText = (existingCharacter?.spellDc ?: 10).toString(),
            spellAttackText = (existingCharacter?.spellAttackModifier ?: 0).toString(),
            legacyTerminologyEnabled = existingCharacter?.legacyTerminologyEnabled ?: false,
            selectedBuildOptionIds = selectedOptionIds,
            acceptedSourceBooks = acceptedSources,
            availableSpellSources = sources,
            classDefinitionsByClass = classDefinitionsByClass,
            availableClasses = availableClasses,
            availableAncestries = catalogResult.catalog.ancestries,
            availableHeritages = catalogResult.catalog.heritagesForAncestry(existingIdentity?.ancestryId),
            availableBackgrounds = catalogResult.catalog.backgrounds,
            featIndexById = catalogResult.catalog.featIndexById,
            archetypeSpellcastingPackages = archetypeSpellcastingPackages,
            expandedSection = CharacterBuilderSectionId.IDENTITY,
        )
        initialSnapshot = snapshot(baseState)
        _uiState.value = derive(baseState)
    }

    private suspend fun persistManagedBuildOptions(
        characterId: Long,
        selectedBuildOptionIds: Set<String>,
        selectedFeatSlotOptions: Map<String, String>,
        selectedAncestryId: String?,
        selectedHeritageId: String?,
        selectedBackgroundId: String?,
    ): Boolean {
        val existingOptions = characterBuildRepository.getBuildOptions(characterId)
        val existingManagedOptions = existingOptions
            .filter { option -> option.optionId in managedBuildOptionIds }
        val selectedManagedOptionIds = selectedBuildOptionIds
            .filter { optionId -> optionId in managedBuildOptionIds }
            .toSet()
        val hasManagedState = existingManagedOptions.isNotEmpty() || selectedManagedOptionIds.isNotEmpty()

        val retainedOptions = existingOptions
            .filterNot { option -> option.optionId in managedBuildOptionIds || option.isBuilderManaged() }
        val managedOptions = selectedManagedOptionIds
            .sorted()
            .mapNotNull { optionId ->
                val optionType = classSpellcastingCatalogSource.optionTypeForOptionId(optionId)
                    ?: archetypeSpellcastingCatalogSource.optionTypeForOptionId(optionId)
                    ?: return@mapNotNull null
                CharacterBuildOption(
                    characterId = characterId,
                    optionType = optionType,
                    optionId = optionId,
                    metadataJson = "{\"managedBy\":\"spellcasting\"}",
                )
            }

        val builderOptions = buildBuilderManagedOptions(
            characterId = characterId,
            selectedFeatSlotOptions = selectedFeatSlotOptions,
            selectedAncestryId = selectedAncestryId,
            selectedHeritageId = selectedHeritageId,
            selectedBackgroundId = selectedBackgroundId,
        )

        characterBuildRepository.replaceBuildOptions(
            characterId = characterId,
            options = retainedOptions + managedOptions + builderOptions,
        )
        return hasManagedState
    }

    private fun CharacterBuildOption.isBuilderManaged(): Boolean {
        return metadataJson.contains("\"managedBy\":\"builder\"")
    }

    private fun featSlotSelectionFromOption(option: CharacterBuildOption): Pair<String, String>? {
        if (!option.isBuilderManaged() || option.optionType != com.spellapp.core.model.CharacterBuildOptionType.FEAT) {
            return null
        }
        val metadata = runCatching { JSONObject(option.metadataJson) }.getOrNull() ?: return null
        val slotId = metadata.optString("slotId").takeIf { it.isNotBlank() } ?: return null
        return slotId to option.optionId
    }

    private fun buildBuilderManagedOptions(
        characterId: Long,
        selectedFeatSlotOptions: Map<String, String>,
        selectedAncestryId: String?,
        selectedHeritageId: String?,
        selectedBackgroundId: String?,
    ): List<CharacterBuildOption> {
        val catalog = builderCatalog ?: return emptyList()
        val featOptions = selectedFeatSlotOptions.mapNotNull { (slotId, featId) ->
            val slot = catalog.classes
                .flatMap { it.featSlots }
                .firstOrNull { it.slotId == slotId }
                ?: return@mapNotNull null
            CharacterBuildOption(
                characterId = characterId,
                optionType = com.spellapp.core.model.CharacterBuildOptionType.FEAT,
                optionId = featId,
                levelAcquired = slot.level,
                metadataJson = builderMetadataJson(
                    "slotId" to slot.slotId,
                    "slotKind" to slot.kind,
                    "slotLevel" to slot.level.toString(),
                    "grantOrigin" to "player",
                ),
            )
        }
        val grantOptions = buildList {
            selectedAncestryId
                ?.let(catalog.ancestriesById::get)
                ?.grants
                .orEmpty()
                .mapNotNullTo(this) { grant ->
                    grant.toBuildOption(
                        characterId = characterId,
                        optionType = com.spellapp.core.model.CharacterBuildOptionType.ANCESTRY_FEATURE,
                        origin = "ancestry/$selectedAncestryId",
                    )
                }
            selectedHeritageId
                ?.let(catalog.heritagesById::get)
                ?.grants
                .orEmpty()
                .mapNotNullTo(this) { grant ->
                    grant.toBuildOption(
                        characterId = characterId,
                        optionType = com.spellapp.core.model.CharacterBuildOptionType.ANCESTRY_FEATURE,
                        origin = "heritage/$selectedHeritageId",
                    )
                }
            selectedBackgroundId
                ?.let(catalog.backgroundsById::get)
                ?.grants
                .orEmpty()
                .mapNotNullTo(this) { grant ->
                    grant.toBuildOption(
                        characterId = characterId,
                        optionType = com.spellapp.core.model.CharacterBuildOptionType.BACKGROUND_FEATURE,
                        origin = "background/$selectedBackgroundId",
                    )
                }
        }
        return (featOptions + grantOptions)
            .distinctBy { option -> option.optionType to option.optionId }
            .sortedWith(compareBy<CharacterBuildOption> { it.levelAcquired ?: 0 }.thenBy { it.optionId })
    }

    private fun BuilderGrantRecord.toBuildOption(
        characterId: Long,
        optionType: com.spellapp.core.model.CharacterBuildOptionType,
        origin: String,
    ): CharacterBuildOption? {
        val optionId = uuid ?: name?.let { normalizeClassId(it) } ?: return null
        return CharacterBuildOption(
            characterId = characterId,
            optionType = optionType,
            optionId = optionId,
            levelAcquired = level,
            metadataJson = builderMetadataJson(
                "grantOrigin" to origin,
                "grantName" to name.orEmpty(),
                "source" to source,
            ),
        )
    }

    private fun builderMetadataJson(vararg pairs: Pair<String, String>): String {
        val json = JSONObject()
        json.put("schemaVersion", 1)
        json.put("managedBy", "builder")
        pairs.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    private fun updateState(reducer: (CharacterBuilderUiState) -> CharacterBuilderUiState) {
        _uiState.update { current -> derive(reducer(current)) }
    }

    private fun derive(state: CharacterBuilderUiState): CharacterBuilderUiState {
        val definition = classSpellcastingCatalogSource.definitionFor(state.selectedClassId)
        val catalog = builderCatalog
        val choiceGroups = definition?.choiceGroups.orEmpty()
        val selectedChoices = choiceGroups
            .flatMap { it.choices }
            .filter { it.optionId in state.selectedBuildOptionIds }
        val missingRequiredChoices = choiceGroups.filter { group ->
            group.required && group.choices.none { choice -> choice.optionId in state.selectedBuildOptionIds }
        }
        val selectedArchetypes = state.archetypeSpellcastingPackages
            .filter { it.dedicationOptionId in state.selectedBuildOptionIds }
        val availableArchetypes = state.archetypeSpellcastingPackages
            .filter { it.dedicationOptionId !in state.selectedBuildOptionIds }
        val expectedFeatSlots = catalog?.featSlotsFor(state.selectedClassId, state.level ?: 1).orEmpty()
        val featCandidatesBySlotId = expectedFeatSlots.associate { slot ->
            slot.slotId to catalog!!.featCandidatesFor(slot)
        }
        val canSave = !state.isLoading &&
            !state.isSaving &&
            state.loadError == null &&
            !state.nameInvalid &&
            !state.levelInvalid &&
            state.selectedAncestryId != null &&
            state.selectedHeritageId != null &&
            state.selectedBackgroundId != null &&
            !state.spellDcInvalid &&
            !state.spellAttackInvalid &&
            missingRequiredChoices.isEmpty()

        val derived = state.copy(
            classChoiceGroups = choiceGroups,
            missingRequiredClassChoices = missingRequiredChoices,
            selectedClassChoices = selectedChoices,
            classPreviewLines = buildClassPreviewLines(
                selectedClassId = state.selectedClassId,
                level = state.level ?: 1,
                selectedChoices = selectedChoices,
            ),
            selectedArchetypePackages = selectedArchetypes,
            availableArchetypePackages = availableArchetypes,
            availableHeritages = catalog?.heritagesForAncestry(state.selectedAncestryId).orEmpty(),
            expectedFeatSlots = expectedFeatSlots,
            featCandidatesBySlotId = featCandidatesBySlotId,
            builderWarningLines = buildBuilderWarningLines(
                state.copy(expectedFeatSlots = expectedFeatSlots),
                catalog,
            ),
            canSave = canSave,
            isDirty = initialSnapshot?.let { baseline -> snapshot(state) != baseline } ?: false,
        )
        return derived.copy(sections = buildSections(derived))
    }

    private fun buildSections(state: CharacterBuilderUiState): List<CharacterBuilderSectionSummary> {
        val sections = mutableListOf<CharacterBuilderSectionSummary>()
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.IDENTITY,
            title = "Identity",
            status = if (state.nameInvalid || state.levelInvalid) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = if (state.name.isBlank()) {
                "Name and level"
            } else {
                "${state.name.trim()} · Level ${state.level ?: "?"}"
            },
            validationMessage = when {
                !state.saveAttempted -> null
                state.nameInvalid -> "Name is required."
                state.levelInvalid -> "Enter a level between 1 and 20."
                else -> null
            },
        )
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.ANCESTRY_HERITAGE,
            title = "Ancestry & Heritage",
            status = if (state.selectedAncestryId == null || state.selectedHeritageId == null) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = ancestryHeritageSummary(state),
            validationMessage = when {
                !state.saveAttempted -> null
                state.selectedAncestryId == null -> "Choose an ancestry."
                state.selectedHeritageId == null -> "Choose a heritage."
                else -> null
            },
        )
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.BACKGROUND,
            title = "Background",
            status = if (state.selectedBackgroundId == null) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = state.selectedBackgroundId
                ?.let { id -> builderCatalog?.backgroundsById?.get(id)?.name }
                ?: "Choose a background",
            validationMessage = if (state.saveAttempted && state.selectedBackgroundId == null) {
                "Choose a background."
            } else {
                null
            },
        )
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.CLASS_SPELLCASTING,
            title = "Class",
            status = when {
                state.availableClasses.isEmpty() -> CharacterBuilderSectionStatus.BLOCKED
                state.missingRequiredClassChoices.isNotEmpty() -> CharacterBuilderSectionStatus.NEEDS_REVIEW
                else -> CharacterBuilderSectionStatus.COMPLETE
            },
            summary = classSpellcastingSummary(state),
            validationMessage = when {
                state.availableClasses.isEmpty() -> "No classes are available."
                state.saveAttempted && state.missingRequiredClassChoices.isNotEmpty() -> {
                    "Choose ${state.missingRequiredClassChoices.joinToString { it.label.lowercase() }}."
                }
                else -> null
            },
        )
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.FEATS,
            title = "Feats",
            status = if (state.expectedFeatSlots.isEmpty()) {
                CharacterBuilderSectionStatus.OPTIONAL
            } else if (state.selectedFeatSlotOptions.size < state.expectedFeatSlots.size) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = if (state.expectedFeatSlots.isEmpty()) {
                "No feat slots at this level"
            } else {
                "${state.selectedFeatSlotOptions.size} of ${state.expectedFeatSlots.size} filled"
            },
        )
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.CASTING_STATS,
            title = "Casting Stats",
            status = if (state.spellDcInvalid || state.spellAttackInvalid) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = "DC ${state.spellDcText.ifBlank { "?" }} · Attack ${state.spellAttack?.withSign() ?: "?"}",
            validationMessage = when {
                !state.saveAttempted -> null
                state.spellDcInvalid -> "Enter a spell DC between 0 and 99."
                state.spellAttackInvalid -> "Enter an attack modifier between -99 and 99."
                else -> null
            },
        )
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.SPELL_SOURCES,
            title = "Spell Sources",
            status = CharacterBuilderSectionStatus.OPTIONAL,
            summary = if (state.availableSpellSources.isEmpty()) {
                "No spell sources available"
            } else {
                "${state.acceptedSourceBooks.size} of ${state.availableSpellSources.size} selected"
            },
        )
        if (state.archetypeSpellcastingPackages.isNotEmpty()) {
            sections += CharacterBuilderSectionSummary(
                id = CharacterBuilderSectionId.ARCHETYPE_SPELLCASTING,
                title = "Archetype Spellcasting",
                status = if (state.selectedArchetypePackages.isEmpty()) {
                    CharacterBuilderSectionStatus.OPTIONAL
                } else {
                    CharacterBuilderSectionStatus.COMPLETE
                },
                summary = if (state.selectedArchetypePackages.isEmpty()) {
                    "No spellcasting archetypes"
                } else {
                    state.selectedArchetypePackages.joinToString { it.label }
                },
            )
        }
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.PREFERENCES,
            title = "Preferences",
            status = CharacterBuilderSectionStatus.OPTIONAL,
            summary = if (state.legacyTerminologyEnabled) {
                "Pre-remaster spell names shown"
            } else {
                "Remaster spell names"
            },
        )
        return sections
    }

    private fun classSpellcastingSummary(state: CharacterBuilderUiState): String {
        val classLabel = state.selectedClassId.classLabel(state.classDefinitionsByClass)
        val keyAbilityText = state.keyAbility.label()
        return if (state.missingRequiredClassChoices.isEmpty()) {
            "$classLabel · $keyAbilityText"
        } else {
            "$classLabel · needs ${state.missingRequiredClassChoices.joinToString { it.label.lowercase() }}"
        }
    }

    private fun ancestryHeritageSummary(state: CharacterBuilderUiState): String {
        val ancestryName = state.selectedAncestryId
            ?.let { id -> builderCatalog?.ancestriesById?.get(id)?.name }
        val heritageName = state.selectedHeritageId
            ?.let { id -> builderCatalog?.heritagesById?.get(id)?.name }
        return listOfNotNull(ancestryName, heritageName)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?: "Choose ancestry and heritage"
    }

    private fun buildBuilderWarningLines(
        state: CharacterBuilderUiState,
        catalog: CharacterBuilderCatalog?,
    ): List<String> {
        if (catalog == null) return emptyList()
        val warnings = mutableListOf<String>()
        state.selectedAncestryId
            ?.let(catalog.ancestriesById::get)
            ?.warnings
            .orEmpty()
            .take(2)
            .forEach { warning -> warnings += "Ancestry: ${warning.message}" }
        state.selectedHeritageId
            ?.let(catalog.heritagesById::get)
            ?.warnings
            .orEmpty()
            .take(2)
            .forEach { warning -> warnings += "Heritage: ${warning.message}" }
        state.selectedBackgroundId
            ?.let(catalog.backgroundsById::get)
            ?.warnings
            .orEmpty()
            .take(2)
            .forEach { warning -> warnings += "Background: ${warning.message}" }
        val missingFeatSlots = state.expectedFeatSlots.count { slot ->
            slot.slotId !in state.selectedFeatSlotOptions
        }
        if (missingFeatSlots > 0) {
            warnings += "$missingFeatSlots expected feat slot${if (missingFeatSlots == 1) "" else "s"} unfilled."
        }
        return warnings.distinct().take(8)
    }

    private fun buildClassPreviewLines(
        selectedClassId: String,
        level: Int,
        selectedChoices: List<ClassChoice>,
    ): List<String> {
        val definition = classSpellcastingCatalogSource.definitionFor(selectedClassId) ?: return emptyList()
        val trackLines = definition.primaryTracks.map { track ->
            track.previewLine(level)
        }
        val choiceLines = selectedChoices.flatMap { choice ->
            listOfNotNull(
                choice.tradition?.let { tradition -> "Choice tradition: ${tradition.displayLabel()}" },
                choice.grantedSpellNames.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "Granted spells: "),
                choice.focusSpellNames.takeIf { it.isNotEmpty() }
                    ?.joinToString(prefix = "Focus spells: "),
            )
        }
        return trackLines + choiceLines
    }

    private fun PrimaryTrackDefinition.previewLine(level: Int): String {
        val style = castingStyle.displayLabel()
        val traditionText = tradition?.displayLabel() ?: "variable tradition"
        val slots = slotsByLevel[level]
            ?.toSortedMap()
            ?.entries
            ?.joinToString { (rank, count) -> "R$rank $count" }
            ?: "slots unavailable"
        val allowanceText = allowanceRules
            .mapNotNull { rule -> rule.previewText(level) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = "; ", prefix = " · ")
            .orEmpty()
        return "$displayName: $style, $traditionText · $slots$allowanceText"
    }

    private fun SpellAllowanceRule.previewText(level: Int): String? {
        return when (policy) {
            SpellAllowancePolicy.ALL_KNOWN -> "$label: all known"
            else -> {
                val rankedCounts = countsAtLevel(level)
                    .toSortedMap()
                    .entries
                    .joinToString { (rank, count) -> "R$rank $count" }
                val total = totalAtLevel(level)
                when {
                    rankedCounts.isNotBlank() -> "$label: $rankedCounts"
                    total != null -> "$label: $total"
                    kind == SpellAllowanceKind.SIGNATURE_SPELLS -> "$label"
                    else -> null
                }
            }
        }
    }

    private fun CastingStyle.displayLabel(): String {
        return name.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun SpellcastingTradition.displayLabel(): String {
        return name.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun snapshot(state: CharacterBuilderUiState): CharacterBuilderSnapshot {
        return CharacterBuilderSnapshot(
            characterId = state.characterId,
            name = state.name,
            levelText = state.levelText,
            selectedClassId = state.selectedClassId,
            keyAbility = state.keyAbility,
            spellDcText = state.spellDcText,
            spellAttackText = state.spellAttackText,
            legacyTerminologyEnabled = state.legacyTerminologyEnabled,
            selectedAncestryId = state.selectedAncestryId,
            selectedHeritageId = state.selectedHeritageId,
            selectedBackgroundId = state.selectedBackgroundId,
            selectedFeatSlotOptions = state.selectedFeatSlotOptions,
            selectedBuildOptionIds = state.selectedBuildOptionIds,
            acceptedSourceBooks = state.acceptedSourceBooks,
        )
    }

    private data class CharacterBuilderSnapshot(
        val characterId: Long,
        val name: String,
        val levelText: String,
        val selectedClassId: String,
        val keyAbility: AbilityScore,
        val spellDcText: String,
        val spellAttackText: String,
        val legacyTerminologyEnabled: Boolean,
        val selectedAncestryId: String?,
        val selectedHeritageId: String?,
        val selectedBackgroundId: String?,
        val selectedFeatSlotOptions: Map<String, String>,
        val selectedBuildOptionIds: Set<String>,
        val acceptedSourceBooks: Set<String>,
    )
}

private fun ArchetypeSpellcastingPackage.optionIdFor(tier: ArchetypeTier): String? = when (tier) {
    ArchetypeTier.DEDICATION -> dedicationOptionId
    ArchetypeTier.BASIC -> basicSpellcastingOptionId
    ArchetypeTier.EXPERT -> expertSpellcastingOptionId
    ArchetypeTier.MASTER -> masterSpellcastingOptionId
}

class CharacterBuilderViewModelFactory(
    private val characterId: Long,
    private val characterCrudRepository: CharacterCrudRepository,
    private val characterBuildRepository: CharacterBuildRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    private val spellRepository: SpellRepository,
    private val refreshSpellcastingProjectionUseCase: RefreshSpellcastingProjectionUseCase,
    private val classDefinitionSource: CharacterClassDefinitionSource = StaticCharacterClassDefinitionSource,
    private val characterBuilderCatalogSource: CharacterBuilderCatalogSource =
        EmptyCharacterBuilderCatalogSource,
    private val archetypeSpellcastingCatalogSource: ArchetypeSpellcastingCatalogSource =
        StaticArchetypeSpellcastingCatalogSource,
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        EmptyClassSpellcastingCatalogSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(CharacterBuilderViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        return CharacterBuilderViewModel(
            characterId = characterId,
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            spellRepository = spellRepository,
            refreshSpellcastingProjectionUseCase = refreshSpellcastingProjectionUseCase,
            classDefinitionSource = classDefinitionSource,
            characterBuilderCatalogSource = characterBuilderCatalogSource,
            archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
            classSpellcastingCatalogSource = classSpellcastingCatalogSource,
        ) as T
    }
}
