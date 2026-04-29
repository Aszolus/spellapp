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
import com.spellapp.core.model.CharacterBuildOptionType
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
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
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
    val selectedAbilityBoosts: Map<String, AbilityScore> = emptyMap(),
    val voluntaryFlawEnabled: Boolean = false,
    val abilityBoostSlots: List<BuilderAbilityBoostSlot> = emptyList(),
    val abilityIssues: List<BuilderIssue> = emptyList(),
    val selectedSkillChoices: Map<String, String> = emptyMap(),
    val skillChoiceSlots: List<BuilderSkillChoiceSlot> = emptyList(),
    val skillIssues: List<BuilderIssue> = emptyList(),
    val selectedPromptChoices: Map<String, String> = emptyMap(),
    val promptSlots: List<BuilderPromptSlot> = emptyList(),
    val promptIssues: List<BuilderIssue> = emptyList(),
    val selectedFeatSlotOptions: Map<String, String> = emptyMap(),
    val selectedFeatOverrideReasons: Map<String, String> = emptyMap(),
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
    val availableClassRecords: List<BuilderClassRecord> = emptyList(),
    val availableAncestries: List<BuilderAncestryRecord> = emptyList(),
    val availableHeritages: List<BuilderHeritageRecord> = emptyList(),
    val availableBackgrounds: List<BuilderBackgroundRecord> = emptyList(),
    val isLoadingFeatDetails: Boolean = false,
    val expectedFeatSlots: List<BuilderFeatSlot> = emptyList(),
    val featIndexById: Map<String, BuilderFeatIndexRecord> = emptyMap(),
    val featsById: Map<String, BuilderFeatRecord> = emptyMap(),
    val featCandidatesBySlotId: Map<String, List<BuilderFeatRecord>> = emptyMap(),
    val featLegalityBySlotId: Map<String, Map<String, BuilderFeatLegality>> = emptyMap(),
    val activeFeatPickerSlotId: String? = null,
    val isPreparingFeatPicker: Boolean = false,
    val activeFeatPickerCandidates: List<BuilderFeatRecord> = emptyList(),
    val activeFeatPickerLegalityByFeatId: Map<String, BuilderFeatLegality> = emptyMap(),
    val buildFacts: BuildFactSnapshot? = null,
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
    ABILITY_SCORES,
    SKILLS,
    SPELL_SOURCES,
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
    private var classDefinitionsByClass: Map<String, CharacterClassDefinition> = emptyMap()
    private var availableClasses: List<CharacterClassDefinition> = emptyList()
    private var builderCatalog: CharacterBuilderCatalog? = null
    private var builderSourceTitles: List<String> = emptyList()
    private var archetypeSpellcastingPackages: List<ArchetypeSpellcastingPackage> = emptyList()
    private var managedClassChoiceOptionIds: Set<String> = emptySet()
    private var managedBuildOptionIds: Set<String> = emptySet()

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
                    current.copy(availableSpellSources = combinedSourceBooks(sources, builderSourceTitles))
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
                ?.filteredBySources(current.acceptedSourceBooks)
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

    fun selectAbilityBoost(
        slotId: String,
        ability: AbilityScore?,
    ) {
        updateState { current ->
            current.copy(
                selectedAbilityBoosts = current.selectedAbilityBoosts.toMutableMap().apply {
                    if (ability == null) remove(slotId) else put(slotId, ability)
                },
                saveError = null,
            )
        }
    }

    fun setVoluntaryFlawEnabled(enabled: Boolean) {
        updateState { current ->
            current.copy(
                voluntaryFlawEnabled = false,
                saveError = null,
            )
        }
    }

    fun selectSkillChoice(
        slotId: String,
        skillId: String?,
    ) {
        updateState { current ->
            current.copy(
                selectedSkillChoices = current.selectedSkillChoices.toMutableMap().apply {
                    if (skillId.isNullOrBlank()) remove(slotId) else put(slotId, skillId)
                },
                saveError = null,
            )
        }
    }

    fun selectLoreSkillChoice(
        slotId: String,
        loreName: String,
    ) {
        val normalized = loreName.trim()
        if (normalized.isBlank()) return
        selectSkillChoice(slotId, BuilderRules.loreKey(normalized))
    }

    fun selectPromptChoice(
        slotId: String,
        choice: String?,
    ) {
        updateState { current ->
            current.copy(
                selectedPromptChoices = current.selectedPromptChoices.toMutableMap().apply {
                    if (choice.isNullOrBlank()) remove(slotId) else put(slotId, choice)
                },
                saveError = null,
            )
        }
    }

    fun selectFeatForSlot(
        slotId: String,
        featId: String?,
        overrideReason: String? = null,
    ) {
        updateState { current ->
            current.copy(
                selectedFeatSlotOptions = current.selectedFeatSlotOptions.toMutableMap().apply {
                    if (featId.isNullOrBlank()) remove(slotId) else put(slotId, featId)
                },
                selectedFeatOverrideReasons = current.selectedFeatOverrideReasons.toMutableMap().apply {
                    if (featId.isNullOrBlank()) {
                        remove(slotId)
                    } else if (overrideReason != null) {
                        if (overrideReason.isBlank()) remove(slotId) else put(slotId, overrideReason.trim())
                    } else {
                        remove(slotId)
                    }
                },
                activeFeatPickerSlotId = null,
                isPreparingFeatPicker = false,
                activeFeatPickerCandidates = emptyList(),
                activeFeatPickerLegalityByFeatId = emptyMap(),
                saveError = null,
            )
        }
    }

    fun openFeatPicker(slotId: String) {
        val current = _uiState.value
        val slot = current.expectedFeatSlots.firstOrNull { it.slotId == slotId } ?: return
        val catalog = builderCatalog ?: return
        if (catalog.feats.isEmpty()) {
            updateState {
                it.copy(
                    activeFeatPickerSlotId = slotId,
                    isPreparingFeatPicker = true,
                    activeFeatPickerCandidates = emptyList(),
                    activeFeatPickerLegalityByFeatId = emptyMap(),
                    saveError = null,
                )
            }
            ensureFeatDetailsLoaded()
            return
        }
        viewModelScope.launch {
            updateState {
                it.copy(
                    activeFeatPickerSlotId = slotId,
                    isPreparingFeatPicker = true,
                    activeFeatPickerCandidates = emptyList(),
                    activeFeatPickerLegalityByFeatId = emptyMap(),
                    saveError = null,
                )
            }
            val snapshot = _uiState.value
            val facts = snapshot.buildFacts ?: run {
                updateState { it.copy(isPreparingFeatPicker = false) }
                return@launch
            }
            val selectedClassId = snapshot.selectedClassId
            val selectedAncestryId = snapshot.selectedAncestryId
            val selectedHeritageId = snapshot.selectedHeritageId
            val sourceBooks = snapshot.acceptedSourceBooks
            val (candidates, legalityByFeatId) = withContext(Dispatchers.Default) {
                val sourceCatalog = catalog.filteredBySources(sourceBooks)
                val candidates = sourceCatalog.featCandidatesFor(slot)
                val prerequisiteLookup = BuilderRules.buildPrerequisiteLookup(sourceCatalog)
                val legalityByFeatId = candidates.associate { feat ->
                    feat.id to BuilderRules.legalityFor(
                        feat = feat,
                        slot = slot,
                        facts = facts,
                        selectedClassId = selectedClassId,
                        selectedAncestryId = selectedAncestryId,
                        selectedHeritageId = selectedHeritageId,
                        catalog = sourceCatalog,
                        prerequisiteLookup = prerequisiteLookup,
                    )
                }
                candidates to legalityByFeatId
            }
            updateState { latest ->
                if (latest.activeFeatPickerSlotId != slotId) {
                    latest
                } else {
                    latest.copy(
                        isPreparingFeatPicker = false,
                        activeFeatPickerCandidates = candidates,
                        activeFeatPickerLegalityByFeatId = legalityByFeatId,
                        featCandidatesBySlotId = mapOf(slotId to candidates),
                        featLegalityBySlotId = latest.featLegalityBySlotId + (slotId to legalityByFeatId),
                    )
                }
            }
        }
    }

    fun dismissFeatPicker() {
        updateState {
            it.copy(
                activeFeatPickerSlotId = null,
                isPreparingFeatPicker = false,
                activeFeatPickerCandidates = emptyList(),
                activeFeatPickerLegalityByFeatId = emptyMap(),
            )
        }
    }

    fun selectClass(classId: String) {
        updateState { current ->
            current.copy(
                selectedClassId = normalizeClassId(classId),
                keyAbility = defaultKeyAbility(classId, classDefinitionsByClass),
                selectedBuildOptionIds = current.selectedBuildOptionIds - managedClassChoiceOptionIds,
                activeFeatPickerSlotId = null,
                isPreparingFeatPicker = false,
                activeFeatPickerCandidates = emptyList(),
                activeFeatPickerLegalityByFeatId = emptyMap(),
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
        updateState { it.copy(legacyTerminologyEnabled = false, saveError = null) }
    }

    fun setAcceptedSourceBooks(sources: Set<String>) {
        updateState {
            it.copy(
                acceptedSourceBooks = sources,
                activeFeatPickerSlotId = null,
                isPreparingFeatPicker = false,
                activeFeatPickerCandidates = emptyList(),
                activeFeatPickerLegalityByFeatId = emptyMap(),
                featCandidatesBySlotId = emptyMap(),
                saveError = null,
            )
        }
    }

    fun toggleSourceBook(source: String, checked: Boolean) {
        updateState { current ->
            current.copy(
                acceptedSourceBooks = current.acceptedSourceBooks.toMutableSet().apply {
                    if (checked) add(source) else remove(source)
                }.toSet(),
                activeFeatPickerSlotId = null,
                isPreparingFeatPicker = false,
                activeFeatPickerCandidates = emptyList(),
                activeFeatPickerLegalityByFeatId = emptyMap(),
                featCandidatesBySlotId = emptyMap(),
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
        if (sectionId == CharacterBuilderSectionId.FEATS && _uiState.value.expandedSection == sectionId) {
            ensureFeatDetailsLoaded()
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
                    legacyTerminologyEnabled = false,
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
                    selectedAbilityBoosts = attempted.selectedAbilityBoosts,
                    selectedSkillChoices = attempted.selectedSkillChoices,
                    selectedPromptChoices = attempted.selectedPromptChoices,
                    selectedFeatSlotOptions = attempted.selectedFeatSlotOptions,
                    selectedFeatOverrideReasons = attempted.selectedFeatOverrideReasons,
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
                    val generatedAbilitySlotIds = it.abilityBoostSlots.map { slot -> slot.slotId }.toSet()
                    val generatedSkillSlotIds = it.skillChoiceSlots.map { slot -> slot.slotId }.toSet()
                    val generatedPromptSlotIds = it.promptSlots.map { slot -> slot.slotId }.toSet()
                    val generatedFeatSlotIds = it.expectedFeatSlots.map { slot -> slot.slotId }.toSet()
                    val savedState = it.copy(
                        characterId = savedCharacterId,
                        isNewCharacter = false,
                        isSaving = false,
                        voluntaryFlawEnabled = false,
                        selectedAbilityBoosts = it.selectedAbilityBoosts
                            .filterKeys { slotId -> slotId in generatedAbilitySlotIds },
                        selectedSkillChoices = it.selectedSkillChoices
                            .filterKeys { slotId -> slotId in generatedSkillSlotIds },
                        selectedPromptChoices = it.selectedPromptChoices
                            .filterKeys { slotId -> slotId in generatedPromptSlotIds },
                        selectedFeatSlotOptions = it.selectedFeatSlotOptions
                            .filterKeys { slotId -> slotId in generatedFeatSlotIds },
                        selectedFeatOverrideReasons = it.selectedFeatOverrideReasons
                            .filterKeys { slotId -> slotId in generatedFeatSlotIds },
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
        runCatching {
            loadDraftUnsafe()
        }.onFailure { error ->
            _uiState.value = derive(
                _uiState.value.copy(
                    isLoading = false,
                    loadError = error.message ?: "Character builder could not be loaded.",
                    availableSpellSources = availableSpellSources.value,
                    classDefinitionsByClass = classDefinitionsByClass,
                    availableClasses = availableClasses,
                    archetypeSpellcastingPackages = archetypeSpellcastingPackages,
                ),
            )
        }
    }

    private suspend fun loadDraftUnsafe() {
        loadStaticBuilderData()
        val sources = spellRepository.observeAvailableSources().first()
        builderSourceTitles = characterBuilderCatalogSource.loadAvailableSourceTitles()
        val availableSourceBooks = combinedSourceBooks(sources, builderSourceTitles)
        val isNew = characterId == 0L
        val existingCharacter = if (isNew) null else characterCrudRepository.getCharacter(characterId)
        if (!isNew && existingCharacter == null) {
            _uiState.value = derive(
                _uiState.value.copy(
                    isLoading = false,
                    loadError = "Character could not be found.",
                    availableSpellSources = availableSourceBooks,
                    classDefinitionsByClass = classDefinitionsByClass,
                    availableClasses = availableClasses,
                    archetypeSpellcastingPackages = archetypeSpellcastingPackages,
                ),
            )
            return
        }

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
        val selectedFeatOverrideReasons = existingOptions
            .mapNotNull(::featOverrideSelectionFromOption)
            .toMap()
        val selectedAbilityBoosts = existingOptions
            .mapNotNull(::abilityBoostSelectionFromOption)
            .toMap()
        val selectedSkillChoices = existingOptions
            .mapNotNull(::skillChoiceSelectionFromOption)
            .toMap()
        val selectedPromptChoices = existingOptions
            .mapNotNull(::promptChoiceSelectionFromOption)
            .toMap()
        val acceptedSources = if (isNew) {
            availableSourceBooks.toSet()
        } else {
            acceptedSpellSourceRepository.getAcceptedSources(characterId)
                .ifEmpty { availableSourceBooks.toSet() }
        }
        val initialClassId = existingCharacter?.classId
            ?: availableClasses.firstOrNull()?.classId
            ?: "wizard"
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
            selectedAbilityBoosts = selectedAbilityBoosts,
            voluntaryFlawEnabled = false,
            selectedSkillChoices = selectedSkillChoices,
            selectedPromptChoices = selectedPromptChoices,
            selectedFeatSlotOptions = selectedFeatSlotOptions,
            selectedFeatOverrideReasons = selectedFeatOverrideReasons,
            keyAbility = existingCharacter?.keyAbility
                ?: defaultKeyAbility(initialClassId, classDefinitionsByClass),
            spellDcText = (existingCharacter?.spellDc ?: 10).toString(),
            spellAttackText = (existingCharacter?.spellAttackModifier ?: 0).toString(),
            legacyTerminologyEnabled = false,
            selectedBuildOptionIds = selectedOptionIds,
            acceptedSourceBooks = acceptedSources,
            availableSpellSources = availableSourceBooks,
            classDefinitionsByClass = classDefinitionsByClass,
            availableClasses = availableClasses,
            archetypeSpellcastingPackages = archetypeSpellcastingPackages,
            expandedSection = CharacterBuilderSectionId.SPELL_SOURCES,
        )
        initialSnapshot = snapshot(baseState)
        _uiState.value = derive(baseState)

        val catalogResult = characterBuilderCatalogSource.loadCatalog()
        val catalog = catalogResult.catalog
        builderCatalog = catalog
        if (catalogResult.loadError != null || catalog == null) {
            _uiState.value = derive(
                _uiState.value.copy(
                    loadError = catalogResult.loadError ?: "Character builder catalog could not be loaded.",
                    availableSpellSources = availableSourceBooks,
                    classDefinitionsByClass = classDefinitionsByClass,
                    archetypeSpellcastingPackages = archetypeSpellcastingPackages,
                ),
            )
            return
        }

        builderSourceTitles = catalog.sourceTitles()
        val latestState = _uiState.value
        val sourceCatalog = catalog.filteredBySources(latestState.acceptedSourceBooks)
        val sourceAllowedClasses = availableClasses.filter { definition ->
            sourceCatalog.classesById.containsKey(normalizeClassId(definition.classId))
        }
        val selectedClassStillAvailable = sourceAllowedClasses.any { definition ->
            normalizeClassId(definition.classId) == normalizeClassId(latestState.selectedClassId)
        }
        val selectedClassId = if (latestState.isNewCharacter && !selectedClassStillAvailable) {
            sourceAllowedClasses.firstOrNull()?.classId ?: latestState.selectedClassId
        } else {
            latestState.selectedClassId
        }
        val loadedState = derive(
            latestState.copy(
                loadError = null,
                availableSpellSources = combinedSourceBooks(sources, builderSourceTitles),
                selectedClassId = selectedClassId,
                keyAbility = if (selectedClassId != latestState.selectedClassId) {
                    defaultKeyAbility(selectedClassId, classDefinitionsByClass)
                } else {
                    latestState.keyAbility
                },
                classDefinitionsByClass = classDefinitionsByClass,
                availableClasses = availableClasses,
                availableClassRecords = sourceCatalog.classes,
                availableAncestries = sourceCatalog.ancestries,
                availableHeritages = sourceCatalog.heritagesForAncestry(latestState.selectedAncestryId),
                availableBackgrounds = sourceCatalog.backgrounds,
                featIndexById = sourceCatalog.featIndexById,
                featsById = sourceCatalog.featsById,
                archetypeSpellcastingPackages = archetypeSpellcastingPackages,
            ),
        )
        initialSnapshot = snapshot(loadedState)
        _uiState.value = loadedState
    }

    private suspend fun loadStaticBuilderData() {
        if (classDefinitionsByClass.isNotEmpty() && availableClasses.isNotEmpty()) {
            return
        }
        val loaded = withContext(Dispatchers.IO) {
            StaticBuilderData(
                classDefinitionsByClass = classDefinitionSource.allDefinitions()
                    .associateBy { normalizeClassId(it.classId) },
                availableClasses = classDefinitionSource.phaseOneDefinitions(),
                archetypeSpellcastingPackages = archetypeSpellcastingCatalogSource.phaseOnePackages(),
                managedClassChoiceOptionIds = classSpellcastingCatalogSource.managedOptionIds(),
                managedBuildOptionIds = archetypeSpellcastingCatalogSource.managedOptionIds() +
                    classSpellcastingCatalogSource.managedOptionIds(),
            )
        }
        classDefinitionsByClass = loaded.classDefinitionsByClass
        availableClasses = loaded.availableClasses
        archetypeSpellcastingPackages = loaded.archetypeSpellcastingPackages
        managedClassChoiceOptionIds = loaded.managedClassChoiceOptionIds
        managedBuildOptionIds = loaded.managedBuildOptionIds
    }

    private fun ensureFeatDetailsLoaded() {
        val currentCatalog = builderCatalog ?: return
        if (currentCatalog.feats.isNotEmpty() || _uiState.value.isLoadingFeatDetails) {
            return
        }
        viewModelScope.launch {
            updateState { it.copy(isLoadingFeatDetails = true) }
            runCatching {
                characterBuilderCatalogSource.loadFeatRecords()
            }.onSuccess { feats ->
                val updatedCatalog = builderCatalog?.copy(feats = feats) ?: return@onSuccess
                builderCatalog = updatedCatalog
                updateState {
                    it.copy(
                        isLoadingFeatDetails = false,
                        availableSpellSources = combinedSourceBooks(availableSpellSources.value, builderSourceTitles),
                        featsById = updatedCatalog.filteredBySources(it.acceptedSourceBooks).featsById,
                    )
                }
                _uiState.value.activeFeatPickerSlotId?.let(::openFeatPicker)
            }.onFailure { error ->
                updateState {
                    it.copy(
                        isLoadingFeatDetails = false,
                        isPreparingFeatPicker = false,
                        saveError = error.message ?: "Feat details could not be loaded.",
                    )
                }
            }
        }
    }

    private suspend fun persistManagedBuildOptions(
        characterId: Long,
        selectedBuildOptionIds: Set<String>,
        selectedAbilityBoosts: Map<String, AbilityScore>,
        selectedSkillChoices: Map<String, String>,
        selectedPromptChoices: Map<String, String>,
        selectedFeatSlotOptions: Map<String, String>,
        selectedFeatOverrideReasons: Map<String, String>,
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
            selectedAbilityBoosts = selectedAbilityBoosts,
            selectedSkillChoices = selectedSkillChoices,
            selectedPromptChoices = selectedPromptChoices,
            selectedFeatSlotOptions = selectedFeatSlotOptions,
            selectedFeatOverrideReasons = selectedFeatOverrideReasons,
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
        if (!option.isBuilderManaged() || option.optionType != CharacterBuildOptionType.FEAT) {
            return null
        }
        val metadata = runCatching { JSONObject(option.metadataJson) }.getOrNull() ?: return null
        val slotId = metadata.optString("slotId").takeIf { it.isNotBlank() } ?: return null
        return slotId to option.optionId
    }

    private fun abilityBoostSelectionFromOption(option: CharacterBuildOption): Pair<String, AbilityScore>? {
        if (!option.isBuilderManaged() || option.optionType != CharacterBuildOptionType.ABILITY_BOOST) {
            return null
        }
        val metadata = runCatching { JSONObject(option.metadataJson) }.getOrNull() ?: return null
        val slotId = metadata.optString("slotId").takeIf { it.isNotBlank() } ?: return null
        val ability = parseStoredAbility(option.optionId) ?: return null
        return slotId to ability
    }

    private fun skillChoiceSelectionFromOption(option: CharacterBuildOption): Pair<String, String>? {
        if (!option.isBuilderManaged() || option.optionType != CharacterBuildOptionType.SKILL_PROFICIENCY) {
            return null
        }
        val metadata = runCatching { JSONObject(option.metadataJson) }.getOrNull() ?: return null
        val slotId = metadata.optString("slotId").takeIf { it.isNotBlank() } ?: return null
        return slotId to option.optionId
    }

    private fun promptChoiceSelectionFromOption(option: CharacterBuildOption): Pair<String, String>? {
        if (!option.isBuilderManaged()) {
            return null
        }
        val metadata = runCatching { JSONObject(option.metadataJson) }.getOrNull() ?: return null
        if (metadata.optString("slotKind") != "prompt") return null
        val slotId = metadata.optString("slotId").takeIf { it.isNotBlank() } ?: return null
        val selectedValue = metadata.optString("selectedValue").takeIf { it.isNotBlank() } ?: return null
        return slotId to selectedValue
    }

    private fun featOverrideSelectionFromOption(option: CharacterBuildOption): Pair<String, String>? {
        if (!option.isBuilderManaged() || option.optionType != CharacterBuildOptionType.OVERRIDE) {
            return null
        }
        val metadata = runCatching { JSONObject(option.metadataJson) }.getOrNull() ?: return null
        if (metadata.optString("overrideType") != "FEAT") return null
        val slotId = metadata.optString("slotId").takeIf { it.isNotBlank() } ?: return null
        val reason = metadata.optString("reason").takeIf { it.isNotBlank() } ?: return null
        return slotId to reason
    }

    private fun buildBuilderManagedOptions(
        characterId: Long,
        selectedAbilityBoosts: Map<String, AbilityScore>,
        selectedSkillChoices: Map<String, String>,
        selectedPromptChoices: Map<String, String>,
        selectedFeatSlotOptions: Map<String, String>,
        selectedFeatOverrideReasons: Map<String, String>,
        selectedAncestryId: String?,
        selectedHeritageId: String?,
        selectedBackgroundId: String?,
    ): List<CharacterBuildOption> {
        val catalog = builderCatalog ?: return emptyList()
        val currentState = _uiState.value
        val abilitySlotsById = BuilderRules.abilityBoostSlots(
            catalog = catalog,
            ancestryId = selectedAncestryId,
            backgroundId = selectedBackgroundId,
            classId = currentState.selectedClassId,
            keyAbility = currentState.keyAbility,
            voluntaryFlawEnabled = false,
        ).associateBy { it.slotId }
        val skillSlotsById = BuilderRules.skillChoiceSlots(
            catalog = catalog,
            classId = currentState.selectedClassId,
            ancestryId = selectedAncestryId,
            heritageId = selectedHeritageId,
            backgroundId = selectedBackgroundId,
        ).associateBy { it.slotId }
        val promptSlotsById = BuilderRules.promptSlots(
            catalog = catalog,
            ancestryId = selectedAncestryId,
            heritageId = selectedHeritageId,
            backgroundId = selectedBackgroundId,
            classId = currentState.selectedClassId,
        ).associateBy { it.slotId }
        val abilityOptions = selectedAbilityBoosts.mapNotNull { (slotId, ability) ->
            val slot = abilitySlotsById[slotId] ?: return@mapNotNull null
            CharacterBuildOption(
                characterId = characterId,
                optionType = CharacterBuildOptionType.ABILITY_BOOST,
                optionId = ability.storageId(),
                levelAcquired = slot.level,
                metadataJson = builderMetadataJson(
                    "slotId" to slot.slotId,
                    "slotKind" to if (slot.isFlaw) "flaw" else "boost",
                    "slotLevel" to slot.level.toString(),
                    "grantOrigin" to "player",
                ),
            )
        }
        val skillOptions = selectedSkillChoices.mapNotNull { (slotId, skillId) ->
            val slot = skillSlotsById[slotId] ?: return@mapNotNull null
            CharacterBuildOption(
                characterId = characterId,
                optionType = CharacterBuildOptionType.SKILL_PROFICIENCY,
                optionId = skillId,
                levelAcquired = slot.level,
                metadataJson = builderMetadataJson(
                    "slotId" to slot.slotId,
                    "slotKind" to slot.kind.name.lowercase(),
                    "slotLevel" to slot.level.toString(),
                    "grantOrigin" to "player",
                ),
            )
        }
        val promptOptions = selectedPromptChoices.mapNotNull { (slotId, selectedValue) ->
            val slot = promptSlotsById[slotId] ?: return@mapNotNull null
            val selectedLabel = slot.choices.firstOrNull { choice -> choice.value == selectedValue }?.label
                ?: selectedValue
            CharacterBuildOption(
                characterId = characterId,
                optionType = slot.source.optionType(),
                optionId = "prompt/${slot.slotId}/$selectedValue",
                levelAcquired = slot.level,
                metadataJson = builderMetadataJson(
                    "slotId" to slot.slotId,
                    "slotKind" to "prompt",
                    "slotLevel" to slot.level.toString(),
                    "promptSource" to slot.source.name.lowercase(),
                    "sourceLabel" to slot.sourceLabel,
                    "selectedValue" to selectedValue,
                    "selectedLabel" to selectedLabel,
                    "grantOrigin" to "player",
                ),
            )
        }
        val featOptions = selectedFeatSlotOptions.mapNotNull { (slotId, featId) ->
            val slot = catalog.classes
                .flatMap { it.featSlots }
                .firstOrNull { it.slotId == slotId }
                ?: return@mapNotNull null
            CharacterBuildOption(
                characterId = characterId,
                optionType = CharacterBuildOptionType.FEAT,
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
        val featOverrideOptions = selectedFeatOverrideReasons.mapNotNull { (slotId, reason) ->
            val featId = selectedFeatSlotOptions[slotId] ?: return@mapNotNull null
            val slot = catalog.classes
                .flatMap { it.featSlots }
                .firstOrNull { it.slotId == slotId }
                ?: return@mapNotNull null
            CharacterBuildOption(
                characterId = characterId,
                optionType = CharacterBuildOptionType.OVERRIDE,
                optionId = "override/feat/$slotId",
                levelAcquired = slot.level,
                metadataJson = builderMetadataJson(
                    "overrideType" to "FEAT",
                    "targetId" to featId,
                    "slotId" to slot.slotId,
                    "label" to (catalog.featsById[featId]?.name ?: featId),
                    "reason" to reason,
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
                        optionType = CharacterBuildOptionType.ANCESTRY_FEATURE,
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
                        optionType = CharacterBuildOptionType.ANCESTRY_FEATURE,
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
                        optionType = CharacterBuildOptionType.BACKGROUND_FEATURE,
                        origin = "background/$selectedBackgroundId",
                    )
                }
        }
        return (abilityOptions + skillOptions + promptOptions + featOptions + featOverrideOptions + grantOptions)
            .distinctBy { option -> option.optionType to option.optionId }
            .sortedWith(compareBy<CharacterBuildOption> { it.levelAcquired ?: 0 }.thenBy { it.optionId })
    }

    private fun BuilderGrantRecord.toBuildOption(
        characterId: Long,
        optionType: CharacterBuildOptionType,
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
        val catalog = builderCatalog
        val sourceCatalog = catalog?.filteredBySources(state.acceptedSourceBooks)
        val availableClassDefinitions = availableClasses.filter { definition ->
            sourceCatalog?.classesById?.containsKey(normalizeClassId(definition.classId)) == true
        }
        val selectedClassAvailable = availableClassDefinitions.any { definition ->
            normalizeClassId(definition.classId) == normalizeClassId(state.selectedClassId)
        }
        val selectedAncestryAvailable = state.selectedAncestryId
            ?.let { ancestryId -> sourceCatalog?.ancestriesById?.containsKey(ancestryId) }
            ?: false
        val selectedHeritageAvailable = state.selectedHeritageId
            ?.let { heritageId -> sourceCatalog?.heritagesById?.containsKey(heritageId) }
            ?: false
        val selectedBackgroundAvailable = state.selectedBackgroundId
            ?.let { backgroundId -> sourceCatalog?.backgroundsById?.containsKey(backgroundId) }
            ?: false
        val definition = if (selectedClassAvailable) {
            classSpellcastingCatalogSource.definitionFor(state.selectedClassId)
        } else {
            null
        }
        val choiceGroups = definition?.choiceGroups.orEmpty()
        val selectedChoices = choiceGroups
            .flatMap { it.choices }
            .filter { it.optionId in state.selectedBuildOptionIds }
        val missingRequiredChoices = choiceGroups.filter { group ->
            group.required && group.choices.none { choice -> choice.optionId in state.selectedBuildOptionIds }
        }
        val sourceAllowedArchetypePackages = state.archetypeSpellcastingPackages
            .filter { packageDef -> packageDef.dedicationOptionId.featIdFromOptionId() in sourceCatalog?.featIndexById.orEmpty() }
        val selectedArchetypes = sourceAllowedArchetypePackages
            .filter { it.dedicationOptionId in state.selectedBuildOptionIds }
        val availableArchetypes = sourceAllowedArchetypePackages
            .filter { it.dedicationOptionId !in state.selectedBuildOptionIds }
        val activeLevel = state.level ?: 1
        val abilityBoostSlots = BuilderRules.abilityBoostSlots(
            catalog = sourceCatalog,
            ancestryId = state.selectedAncestryId,
            backgroundId = state.selectedBackgroundId,
            classId = state.selectedClassId,
            keyAbility = state.keyAbility,
            voluntaryFlawEnabled = false,
        )
        val abilityIssues = BuilderRules.abilityIssues(
            slots = abilityBoostSlots,
            selectedAbilityBoosts = state.selectedAbilityBoosts,
            activeLevel = activeLevel,
        )
        val skillChoiceSlots = BuilderRules.skillChoiceSlots(
            catalog = sourceCatalog,
            classId = state.selectedClassId,
            ancestryId = state.selectedAncestryId,
            heritageId = state.selectedHeritageId,
            backgroundId = state.selectedBackgroundId,
        )
        val skillIssues = BuilderRules.skillIssues(
            slots = skillChoiceSlots,
            selectedSkillChoices = state.selectedSkillChoices,
            activeLevel = activeLevel,
            initialTrainedSkills = BuilderRules.initialTrainedSkillIds(
                catalog = sourceCatalog,
                classId = state.selectedClassId,
                backgroundId = state.selectedBackgroundId,
            ),
        )
        val promptSlots = BuilderRules.promptSlots(
            catalog = sourceCatalog,
            ancestryId = state.selectedAncestryId,
            heritageId = state.selectedHeritageId,
            backgroundId = state.selectedBackgroundId,
            classId = state.selectedClassId,
        )
        val promptIssues = BuilderRules.promptIssues(
            slots = promptSlots,
            selectedPromptChoices = state.selectedPromptChoices,
            activeLevel = activeLevel,
        )
        val buildFacts = BuilderRules.buildFacts(
            catalog = sourceCatalog,
            classId = state.selectedClassId,
            ancestryId = state.selectedAncestryId,
            backgroundId = state.selectedBackgroundId,
            level = activeLevel,
            abilitySlots = abilityBoostSlots,
            selectedAbilityBoosts = state.selectedAbilityBoosts,
            skillSlots = skillChoiceSlots,
            selectedSkillChoices = state.selectedSkillChoices,
            selectedFeatSlotOptions = state.selectedFeatSlotOptions,
        )
        val expectedFeatSlots = if (selectedClassAvailable) {
            sourceCatalog?.featSlotsFor(state.selectedClassId, 20).orEmpty()
        } else {
            emptyList()
        }
        val featLegalityBySlotId = selectedFeatLegalityBySlotId(
            expectedFeatSlots = expectedFeatSlots,
            state = state,
            catalog = sourceCatalog,
            buildFacts = buildFacts,
        )
        val activeFeatSlots = expectedFeatSlots.filter { it.level <= activeLevel }
        val activeMissingFeatSlots = activeFeatSlots.count { slot -> slot.slotId !in state.selectedFeatSlotOptions }
        val activeBlockedFeatSelections = activeFeatSlots.count { slot ->
            val selectedFeatId = state.selectedFeatSlotOptions[slot.slotId] ?: return@count false
            val legality = featLegalityBySlotId[slot.slotId]?.get(selectedFeatId) ?: return@count false
            legality.requiresOverride && state.selectedFeatOverrideReasons[slot.slotId].isNullOrBlank()
        }
        val canSave = !state.isLoading &&
            !state.isSaving &&
            state.loadError == null &&
            state.acceptedSourceBooks.isNotEmpty() &&
            !state.nameInvalid &&
            !state.levelInvalid &&
            selectedClassAvailable &&
            selectedAncestryAvailable &&
            selectedHeritageAvailable &&
            selectedBackgroundAvailable &&
            missingRequiredChoices.isEmpty() &&
            abilityIssues.none { it.active } &&
            skillIssues.none { it.active } &&
            promptIssues.none { it.active } &&
            activeMissingFeatSlots == 0 &&
            activeBlockedFeatSelections == 0

        val derived = state.copy(
            voluntaryFlawEnabled = false,
            classChoiceGroups = choiceGroups,
            missingRequiredClassChoices = missingRequiredChoices,
            selectedClassChoices = selectedChoices,
            classPreviewLines = if (selectedClassAvailable) {
                buildClassPreviewLines(
                    selectedClassId = state.selectedClassId,
                    level = state.level ?: 1,
                    selectedChoices = selectedChoices,
                )
            } else {
                emptyList()
            },
            selectedArchetypePackages = selectedArchetypes,
            availableArchetypePackages = availableArchetypes,
            availableClasses = availableClassDefinitions,
            availableClassRecords = sourceCatalog?.classes.orEmpty(),
            availableAncestries = sourceCatalog?.ancestries.orEmpty(),
            availableHeritages = sourceCatalog?.heritagesForAncestry(state.selectedAncestryId).orEmpty(),
            availableBackgrounds = sourceCatalog?.backgrounds.orEmpty(),
            featIndexById = sourceCatalog?.featIndexById.orEmpty(),
            featsById = sourceCatalog?.featsById.orEmpty(),
            abilityBoostSlots = abilityBoostSlots,
            abilityIssues = abilityIssues,
            skillChoiceSlots = skillChoiceSlots,
            skillIssues = skillIssues,
            promptSlots = promptSlots,
            promptIssues = promptIssues,
            expectedFeatSlots = expectedFeatSlots,
            featCandidatesBySlotId = state.featCandidatesBySlotId.filterKeys { slotId ->
                state.activeFeatPickerSlotId == slotId
            },
            featLegalityBySlotId = featLegalityBySlotId,
            buildFacts = buildFacts,
            builderWarningLines = buildBuilderWarningLines(
                state.copy(
                    expectedFeatSlots = expectedFeatSlots,
                    availableClasses = availableClassDefinitions,
                    availableAncestries = sourceCatalog?.ancestries.orEmpty(),
                    availableHeritages = sourceCatalog?.heritagesForAncestry(state.selectedAncestryId).orEmpty(),
                    availableBackgrounds = sourceCatalog?.backgrounds.orEmpty(),
                    abilityIssues = abilityIssues,
                    skillIssues = skillIssues,
                    promptSlots = promptSlots,
                    promptIssues = promptIssues,
                    featLegalityBySlotId = featLegalityBySlotId,
                ),
                sourceCatalog,
            ),
            canSave = canSave,
            isDirty = initialSnapshot?.let { baseline -> snapshot(state) != baseline } ?: false,
        )
        return derived.copy(sections = buildSections(derived))
    }

    private fun selectedFeatLegalityBySlotId(
        expectedFeatSlots: List<BuilderFeatSlot>,
        state: CharacterBuilderUiState,
        catalog: CharacterBuilderCatalog?,
        buildFacts: BuildFactSnapshot,
    ): Map<String, Map<String, BuilderFeatLegality>> {
        if (catalog == null || catalog.featsById.isEmpty()) return emptyMap()
        return expectedFeatSlots.mapNotNull { slot ->
            val selectedFeatId = state.selectedFeatSlotOptions[slot.slotId] ?: return@mapNotNull null
            val feat = catalog.featsById[selectedFeatId] ?: return@mapNotNull null
            slot.slotId to mapOf(
                selectedFeatId to BuilderRules.legalityFor(
                    feat = feat,
                    slot = slot,
                    facts = buildFacts,
                    selectedClassId = state.selectedClassId,
                    selectedAncestryId = state.selectedAncestryId,
                    selectedHeritageId = state.selectedHeritageId,
                    catalog = catalog,
                ),
            )
        }.toMap()
    }

    private fun buildSections(state: CharacterBuilderUiState): List<CharacterBuilderSectionSummary> {
        val sections = mutableListOf<CharacterBuilderSectionSummary>()
        val ancestryPromptIssues = state.activePromptIssueCount(BuilderPromptSource.ANCESTRY, BuilderPromptSource.HERITAGE)
        val backgroundPromptIssues = state.activePromptIssueCount(BuilderPromptSource.BACKGROUND)
        val classPromptIssues = state.activePromptIssueCount(BuilderPromptSource.CLASS)
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.SPELL_SOURCES,
            title = "Sources",
            status = if (state.acceptedSourceBooks.isEmpty()) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = if (state.availableSpellSources.isEmpty()) {
                "No sources available"
            } else {
                "${state.acceptedSourceBooks.size} of ${state.availableSpellSources.size} selected"
            },
            validationMessage = if (state.saveAttempted && state.acceptedSourceBooks.isEmpty()) {
                "Choose at least one source."
            } else {
                null
            },
        )
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
            status = if (state.selectedAncestryId == null ||
                state.selectedHeritageId == null ||
                state.availableAncestries.none { it.id == state.selectedAncestryId } ||
                state.availableHeritages.none { it.id == state.selectedHeritageId } ||
                ancestryPromptIssues > 0
            ) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = ancestryHeritageSummary(state),
            validationMessage = when {
                !state.saveAttempted -> null
                state.selectedAncestryId == null ||
                    state.availableAncestries.none { it.id == state.selectedAncestryId } -> "Choose an ancestry from the selected sources."
                state.selectedHeritageId == null ||
                    state.availableHeritages.none { it.id == state.selectedHeritageId } -> "Choose a heritage from the selected sources."
                ancestryPromptIssues > 0 -> state.firstPromptIssueMessage(BuilderPromptSource.ANCESTRY, BuilderPromptSource.HERITAGE)
                else -> null
            },
        )
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.BACKGROUND,
            title = "Background",
            status = if (state.selectedBackgroundId == null ||
                state.availableBackgrounds.none { it.id == state.selectedBackgroundId } ||
                backgroundPromptIssues > 0
            ) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = state.availableBackgrounds
                .firstOrNull { background -> background.id == state.selectedBackgroundId }
                ?.name
                ?: "Choose a background",
            validationMessage = when {
                !state.saveAttempted -> null
                state.selectedBackgroundId == null ||
                    state.availableBackgrounds.none { it.id == state.selectedBackgroundId } -> "Choose a background from the selected sources."
                backgroundPromptIssues > 0 -> state.firstPromptIssueMessage(BuilderPromptSource.BACKGROUND)
                else -> null
            },
        )
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.CLASS_SPELLCASTING,
            title = "Class",
            status = when {
                state.availableClasses.isEmpty() -> CharacterBuilderSectionStatus.BLOCKED
                state.availableClasses.none { normalizeClassId(it.classId) == normalizeClassId(state.selectedClassId) } -> CharacterBuilderSectionStatus.NEEDS_REVIEW
                state.missingRequiredClassChoices.isNotEmpty() -> CharacterBuilderSectionStatus.NEEDS_REVIEW
                classPromptIssues > 0 -> CharacterBuilderSectionStatus.NEEDS_REVIEW
                else -> CharacterBuilderSectionStatus.COMPLETE
            },
            summary = classSpellcastingSummary(state),
            validationMessage = when {
                state.availableClasses.isEmpty() -> "No classes are available."
                state.saveAttempted &&
                    state.availableClasses.none { normalizeClassId(it.classId) == normalizeClassId(state.selectedClassId) } -> {
                    "Choose a class from the selected sources."
                }
                state.saveAttempted && state.missingRequiredClassChoices.isNotEmpty() -> {
                    "Choose ${state.missingRequiredClassChoices.joinToString { it.label.lowercase() }}."
                }
                state.saveAttempted && classPromptIssues > 0 -> state.firstPromptIssueMessage(BuilderPromptSource.CLASS)
                else -> null
            },
        )
        val activeAbilityIssues = state.abilityIssues.count { it.active && it.isLevelOneIssue() }
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.ABILITY_SCORES,
            title = "Attribute Modifiers",
            status = if (activeAbilityIssues > 0) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = state.buildFacts?.abilityModifiers
                ?.entries
                ?.joinToString { (ability, modifier) -> "${ability.label()} ${modifier.withSign()}" }
                ?: "Boosts and flaws",
            validationMessage = if (state.saveAttempted && activeAbilityIssues > 0) {
                "$activeAbilityIssues attribute choice${if (activeAbilityIssues == 1) "" else "s"} need review."
            } else {
                null
            },
        )
        val activeSkillIssues = state.skillIssues.count { it.active && it.isLevelOneIssue() }
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.SKILLS,
            title = "Skills",
            status = if (activeSkillIssues > 0) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = state.buildFacts?.skillRanks
                ?.filterValues { rank -> rank.value > 0 }
                ?.size
                ?.let { trainedCount -> "$trainedCount trained or better" }
                ?: "Skill training and increases",
            validationMessage = if (state.saveAttempted && activeSkillIssues > 0) {
                "$activeSkillIssues active skill choice${if (activeSkillIssues == 1) "" else "s"} need review."
            } else {
                null
            },
        )
        val workbenchChoiceCount = state.expectedFeatSlots.size +
            state.abilityBoostSlots.count { it.level > 1 } +
            state.skillChoiceSlots.count { it.level > 1 }
        val workbenchPlannedCount = state.selectedFeatSlotOptions.count { (slotId, _) ->
            state.expectedFeatSlots.any { slot -> slot.slotId == slotId }
        } + state.selectedAbilityBoosts.count { (slotId, _) ->
            state.abilityBoostSlots.any { slot -> slot.slotId == slotId && slot.level > 1 }
        } + state.selectedSkillChoices.count { (slotId, _) ->
            state.skillChoiceSlots.any { slot -> slot.slotId == slotId && slot.level > 1 }
        }
        val activeWorkbenchIssueCount = state.abilityIssues.count { it.active && !it.isLevelOneIssue() } +
            state.skillIssues.count { it.active && !it.isLevelOneIssue() }
        sections += CharacterBuilderSectionSummary(
            id = CharacterBuilderSectionId.FEATS,
            title = "Level Workbench",
            status = if (workbenchChoiceCount == 0) {
                CharacterBuilderSectionStatus.OPTIONAL
            } else if (activeMissingFeatSlotCount(state) > 0 || activeBlockedFeatSelectionCount(state) > 0 || activeWorkbenchIssueCount > 0) {
                CharacterBuilderSectionStatus.NEEDS_REVIEW
            } else {
                CharacterBuilderSectionStatus.COMPLETE
            },
            summary = if (workbenchChoiceCount == 0) {
                "No tracked level choices"
            } else {
                "$workbenchPlannedCount of $workbenchChoiceCount planned"
            },
            validationMessage = when {
                !state.saveAttempted -> null
                activeMissingFeatSlotCount(state) > 0 -> "${activeMissingFeatSlotCount(state)} active feat slot${if (activeMissingFeatSlotCount(state) == 1) "" else "s"} unfilled."
                activeBlockedFeatSelectionCount(state) > 0 -> "${activeBlockedFeatSelectionCount(state)} active feat selection${if (activeBlockedFeatSelectionCount(state) == 1) "" else "s"} need an override or a different feat."
                activeWorkbenchIssueCount > 0 -> "$activeWorkbenchIssueCount active level choice${if (activeWorkbenchIssueCount == 1) "" else "s"} need review."
                else -> null
            },
        )
        return sections
    }

    private fun classSpellcastingSummary(state: CharacterBuilderUiState): String {
        val classDefinition = state.availableClasses.firstOrNull { definition ->
            normalizeClassId(definition.classId) == normalizeClassId(state.selectedClassId)
        }
        val classLabel = classDefinition?.label ?: "Choose a class"
        val keyAbilityText = state.keyAbility.label()
        return if (classDefinition == null) {
            classLabel
        } else if (state.missingRequiredClassChoices.isEmpty()) {
            "$classLabel · $keyAbilityText"
        } else {
            "$classLabel · needs ${state.missingRequiredClassChoices.joinToString { it.label.lowercase() }}"
        }
    }

    private fun ancestryHeritageSummary(state: CharacterBuilderUiState): String {
        val ancestryName = state.availableAncestries
            .firstOrNull { ancestry -> ancestry.id == state.selectedAncestryId }
            ?.name
        val heritageName = state.availableHeritages
            .firstOrNull { heritage -> heritage.id == state.selectedHeritageId }
            ?.name
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
        state.abilityIssues
            .filter { it.active }
            .take(2)
            .forEach { issue -> warnings += issue.message }
        state.skillIssues
            .filter { it.active }
            .take(2)
            .forEach { issue -> warnings += issue.message }
        state.promptIssues
            .filter { it.active }
            .take(2)
            .forEach { issue -> warnings += issue.message }
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
        val missingFeatSlots = activeMissingFeatSlotCount(state)
        if (missingFeatSlots > 0) {
            warnings += "$missingFeatSlots active feat slot${if (missingFeatSlots == 1) "" else "s"} unfilled."
        }
        val blockedFeatSelections = activeBlockedFeatSelectionCount(state)
        if (blockedFeatSelections > 0) {
            warnings += "$blockedFeatSelections active feat selection${if (blockedFeatSelections == 1) "" else "s"} need review."
        }
        return warnings.distinct().take(8)
    }

    private fun activeMissingFeatSlotCount(state: CharacterBuilderUiState): Int {
        val activeLevel = state.level ?: 1
        return state.expectedFeatSlots.count { slot ->
            slot.level <= activeLevel &&
            slot.slotId !in state.selectedFeatSlotOptions
        }
    }

    private fun activeBlockedFeatSelectionCount(state: CharacterBuilderUiState): Int {
        val activeLevel = state.level ?: 1
        return state.expectedFeatSlots.count { slot ->
            if (slot.level > activeLevel) return@count false
            val selectedFeatId = state.selectedFeatSlotOptions[slot.slotId] ?: return@count false
            val legality = state.featLegalityBySlotId[slot.slotId]?.get(selectedFeatId) ?: return@count false
            legality.requiresOverride && state.selectedFeatOverrideReasons[slot.slotId].isNullOrBlank()
        }
    }

    private fun CharacterBuilderUiState.activePromptIssueCount(vararg sources: BuilderPromptSource): Int {
        val sourceSet = sources.toSet()
        val slotIds = promptSlots
            .filter { slot -> slot.source in sourceSet }
            .map { slot -> slot.slotId }
            .toSet()
        return promptIssues.count { issue -> issue.active && issue.slotId in slotIds }
    }

    private fun CharacterBuilderUiState.firstPromptIssueMessage(vararg sources: BuilderPromptSource): String? {
        val sourceSet = sources.toSet()
        val slotIds = promptSlots
            .filter { slot -> slot.source in sourceSet }
            .map { slot -> slot.slotId }
            .toSet()
        return promptIssues.firstOrNull { issue -> issue.active && issue.slotId in slotIds }?.message
    }

    private fun BuilderIssue.isLevelOneIssue(): Boolean {
        return (level ?: 1) <= 1
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

    private fun combinedSourceBooks(
        spellSources: List<String>,
        catalog: CharacterBuilderCatalog?,
    ): List<String> {
        return combinedSourceBooks(spellSources, catalog?.sourceTitles().orEmpty())
    }

    private fun combinedSourceBooks(
        spellSources: List<String>,
        builderSources: List<String>,
    ): List<String> {
        return (spellSources + builderSources)
            .filter { sourceBook -> sourceBook.isNotBlank() }
            .distinctBy { sourceBook -> sourceBook.sourceBookKey() }
            .sorted()
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
            selectedAbilityBoosts = state.selectedAbilityBoosts,
            voluntaryFlawEnabled = state.voluntaryFlawEnabled,
            selectedSkillChoices = state.selectedSkillChoices,
            selectedPromptChoices = state.selectedPromptChoices,
            selectedFeatSlotOptions = state.selectedFeatSlotOptions,
            selectedFeatOverrideReasons = state.selectedFeatOverrideReasons,
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
        val selectedAbilityBoosts: Map<String, AbilityScore>,
        val voluntaryFlawEnabled: Boolean,
        val selectedSkillChoices: Map<String, String>,
        val selectedPromptChoices: Map<String, String>,
        val selectedFeatSlotOptions: Map<String, String>,
        val selectedFeatOverrideReasons: Map<String, String>,
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

private data class StaticBuilderData(
    val classDefinitionsByClass: Map<String, CharacterClassDefinition>,
    val availableClasses: List<CharacterClassDefinition>,
    val archetypeSpellcastingPackages: List<ArchetypeSpellcastingPackage>,
    val managedClassChoiceOptionIds: Set<String>,
    val managedBuildOptionIds: Set<String>,
)

private fun BuilderPromptSource.optionType(): CharacterBuildOptionType = when (this) {
    BuilderPromptSource.ANCESTRY -> CharacterBuildOptionType.ANCESTRY
    BuilderPromptSource.HERITAGE -> CharacterBuildOptionType.HERITAGE
    BuilderPromptSource.BACKGROUND -> CharacterBuildOptionType.BACKGROUND
    BuilderPromptSource.CLASS -> CharacterBuildOptionType.CLASS
}

private fun String.featIdFromOptionId(): String {
    return substringAfterLast('/').trim()
}

private fun AbilityScore.storageId(): String = when (this) {
    AbilityScore.STRENGTH -> "str"
    AbilityScore.DEXTERITY -> "dex"
    AbilityScore.CONSTITUTION -> "con"
    AbilityScore.INTELLIGENCE -> "int"
    AbilityScore.WISDOM -> "wis"
    AbilityScore.CHARISMA -> "cha"
}

private fun parseStoredAbility(raw: String): AbilityScore? = when (raw.trim().lowercase()) {
    "str", "strength" -> AbilityScore.STRENGTH
    "dex", "dexterity" -> AbilityScore.DEXTERITY
    "con", "constitution" -> AbilityScore.CONSTITUTION
    "int", "intelligence" -> AbilityScore.INTELLIGENCE
    "wis", "wisdom" -> AbilityScore.WISDOM
    "cha", "charisma" -> AbilityScore.CHARISMA
    else -> null
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
