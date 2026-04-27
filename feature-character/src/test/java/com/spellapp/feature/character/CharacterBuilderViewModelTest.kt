package com.spellapp.feature.character

import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.local.ClassSpellcastingCatalogJsonParser
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.CharacterBuildIdentity
import com.spellapp.core.model.CharacterBuildOption
import com.spellapp.core.model.CharacterBuildOptionType
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.ClassSpellcastingCatalog
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.KnownSpellOrigin
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import com.spellapp.feature.character.spellcasting.DefaultKnownSpellsSeeder
import com.spellapp.feature.character.spellcasting.RefreshSpellcastingProjectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CharacterBuilderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        ClassSpellcastingCatalogJsonParser.parse(readClassSpellcastingAsset()).also { source ->
            ClassSpellcastingCatalog.install(source)
        }

    @Test
    fun newCharacter_defaultsToFirstSupportedClass_andAllSources() = runTest {
        val viewModel = createViewModel(
            characterId = 0L,
            spellRepository = FakeSpellRepository(
                availableSources = listOf("Player Core", "GM Core"),
                spells = emptyList(),
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isNewCharacter)
        assertEquals(setOf("Player Core", "GM Core"), state.acceptedSourceBooks)
        assertEquals(CharacterBuilderSectionId.IDENTITY, state.expandedSection)
        assertTrue(state.classPreviewLines.isNotEmpty())
        assertEquals(
            CharacterBuilderSectionStatus.NEEDS_REVIEW,
            state.sections.first { it.id == CharacterBuilderSectionId.IDENTITY }.status,
        )
    }

    @Test
    fun saveInvalidDraft_marksValidationAndDoesNotPersist() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val viewModel = createViewModel(
            characterId = 0L,
            characterCrudRepository = characterCrudRepository,
        )
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.saveAttempted)
        assertFalse(state.canSave)
        assertTrue(characterCrudRepository.characters().isEmpty())
        assertEquals(
            CharacterBuilderSectionStatus.NEEDS_REVIEW,
            state.sections.first { it.id == CharacterBuilderSectionId.IDENTITY }.status,
        )
    }

    @Test
    fun editExisting_loadsOnlyManagedOptions_andAcceptedSources() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val characterBuildRepository = FakeCharacterBuildRepository()
        val acceptedSpellSourceRepository = FakeAcceptedSpellSourceRepository()
        val existingCharacterId = characterCrudRepository.upsertCharacter(sampleCharacter(id = 10L))
        characterBuildRepository.replaceBuildOptions(
            characterId = existingCharacterId,
            options = listOf(
                CharacterBuildOption(
                    characterId = existingCharacterId,
                    optionType = CharacterBuildOptionType.ARCHETYPE,
                    optionId = "archetype/wizard/wizard-dedication",
                ),
                CharacterBuildOption(
                    characterId = existingCharacterId,
                    optionType = CharacterBuildOptionType.OTHER,
                    optionId = "custom/non-managed",
                ),
            ),
        )
        acceptedSpellSourceRepository.replaceAcceptedSources(existingCharacterId, setOf("Player Core"))

        val viewModel = createViewModel(
            characterId = existingCharacterId,
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf("archetype/wizard/wizard-dedication"), state.selectedBuildOptionIds)
        assertEquals(setOf("Player Core"), state.acceptedSourceBooks)
        assertFalse(state.isDirty)
    }

    @Test
    fun saveExisting_replacesManagedOptions_preservesCustomOptions_andReconcilesArchetypes() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val characterBuildRepository = FakeCharacterBuildRepository()
        val castingTrackRepository = FakeCastingTrackRepository()
        val preparedSlotSyncRepository = FakePreparedSlotSyncRepository()
        val existingCharacterId = characterCrudRepository.upsertCharacter(sampleCharacter(id = 33L))
        characterBuildRepository.replaceBuildOptions(
            characterId = existingCharacterId,
            options = listOf(
                CharacterBuildOption(
                    characterId = existingCharacterId,
                    optionType = CharacterBuildOptionType.OTHER,
                    optionId = "custom/non-managed",
                ),
                CharacterBuildOption(
                    characterId = existingCharacterId,
                    optionType = CharacterBuildOptionType.ARCHETYPE,
                    optionId = "archetype/druid/druid-dedication",
                ),
            ),
        )
        castingTrackRepository.upsertCastingTrack(
            CastingTrack(
                characterId = existingCharacterId,
                trackKey = "archetype-druid",
                sourceType = CastingTrackSourceType.ARCHETYPE,
                sourceId = "Druid",
                progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
            ),
        )
        val viewModel = createViewModel(
            characterId = existingCharacterId,
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
        )
        advanceUntilIdle()
        selectRequiredClassChoices(viewModel)
        selectRequiredBuilderBasics(viewModel)
        val wizard = viewModel.uiState.value.archetypeSpellcastingPackages.first { it.archetypeId == "wizard" }
        val cleric = viewModel.uiState.value.archetypeSpellcastingPackages.first { it.archetypeId == "cleric" }
        val druid = viewModel.uiState.value.archetypeSpellcastingPackages.first { it.archetypeId == "druid" }
        viewModel.toggleArchetypeTier(druid, ArchetypeTier.DEDICATION, false)
        viewModel.toggleArchetypeTier(wizard, ArchetypeTier.DEDICATION, true)
        viewModel.toggleArchetypeTier(wizard, ArchetypeTier.BASIC, true)
        viewModel.toggleArchetypeTier(cleric, ArchetypeTier.DEDICATION, true)

        viewModel.save()
        advanceUntilIdle()

        val savedOptions = characterBuildRepository.getBuildOptions(existingCharacterId)
        val savedOptionIds = savedOptions.map { it.optionId }.toSet()
        assertTrue("custom/non-managed" in savedOptionIds)
        assertTrue("archetype/wizard/wizard-dedication" in savedOptionIds)
        assertTrue("archetype/wizard/basic-wizard-spellcasting" in savedOptionIds)
        assertTrue("archetype/cleric/cleric-dedication" in savedOptionIds)
        assertFalse("archetype/druid/druid-dedication" in savedOptionIds)

        val archetypeTracks = castingTrackRepository.getCastingTracks(existingCharacterId)
            .filter { it.sourceType == CastingTrackSourceType.ARCHETYPE }
        assertEquals(setOf("Wizard", "Cleric"), archetypeTracks.map { it.displayName }.toSet())
        assertEquals(listOf(existingCharacterId), preparedSlotSyncRepository.syncedCharacterIds)
    }

    @Test
    fun saveNewCleric_persistsAcceptedSources_andSeedsCommonKnownSpells() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val acceptedSpellSourceRepository = FakeAcceptedSpellSourceRepository()
        val knownSpellRepository = FakeKnownSpellRepository()
        val spellRepository = FakeSpellRepository(
            availableSources = listOf("Player Core", "Gods & Magic"),
            spells = listOf(
                spell(id = "heal", tradition = "divine", rarity = "common", sourceBook = "Player Core"),
                spell(id = "blessing", tradition = "divine", rarity = "common", sourceBook = "Gods & Magic"),
                spell(id = "rare-divine", tradition = "divine", rarity = "rare", sourceBook = "Player Core"),
            ),
        )
        val viewModel = createViewModel(
            characterId = 0L,
            characterCrudRepository = characterCrudRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
        )
        advanceUntilIdle()
        viewModel.updateName("Mira")
        viewModel.selectClass("cleric")
        selectRequiredClassChoices(viewModel)
        selectRequiredBuilderBasics(viewModel)
        viewModel.setAcceptedSourceBooks(setOf("Player Core"))

        viewModel.save()
        advanceUntilIdle()

        val savedCharacterId = characterCrudRepository.characters().single().id
        assertEquals(setOf("Player Core"), acceptedSpellSourceRepository.getAcceptedSources(savedCharacterId))
        assertEquals(setOf("heal"), knownSpellRepository.getKnownSpellIds(savedCharacterId))
    }

    @Test
    fun sectionStatuses_updateAsDraftBecomesComplete() = runTest {
        val viewModel = createViewModel(characterId = 0L)
        advanceUntilIdle()

        viewModel.updateName("Sera")
        selectRequiredClassChoices(viewModel)
        advanceUntilIdle()

        val sectionsById = viewModel.uiState.value.sections.associateBy { it.id }
        assertEquals(CharacterBuilderSectionStatus.COMPLETE, sectionsById[CharacterBuilderSectionId.IDENTITY]?.status)
        assertEquals(
            CharacterBuilderSectionStatus.COMPLETE,
            sectionsById[CharacterBuilderSectionId.CLASS_SPELLCASTING]?.status,
        )
        assertEquals(
            CharacterBuilderSectionStatus.OPTIONAL,
            sectionsById[CharacterBuilderSectionId.SPELL_SOURCES]?.status,
        )
        assertEquals(
            CharacterBuilderSectionStatus.OPTIONAL,
            sectionsById[CharacterBuilderSectionId.PREFERENCES]?.status,
        )
    }

    private fun selectRequiredClassChoices(viewModel: CharacterBuilderViewModel) {
        viewModel.uiState.value.classChoiceGroups
            .filter { it.required }
            .forEach { group ->
                viewModel.selectClassChoice(group, group.choices.first())
            }
    }

    private fun selectRequiredBuilderBasics(viewModel: CharacterBuilderViewModel) {
        viewModel.selectAncestry("human")
        viewModel.selectHeritage("skilled-heritage")
        viewModel.selectBackground("acolyte")
    }

    private fun createViewModel(
        characterId: Long,
        characterCrudRepository: CharacterCrudRepository = FakeCharacterCrudRepository(),
        characterBuildRepository: CharacterBuildRepository = FakeCharacterBuildRepository(),
        acceptedSpellSourceRepository: AcceptedSpellSourceRepository = FakeAcceptedSpellSourceRepository(),
        castingTrackRepository: CastingTrackRepository = FakeCastingTrackRepository(),
        preparedSlotSyncRepository: PreparedSlotSyncRepository = FakePreparedSlotSyncRepository(),
        knownSpellRepository: KnownSpellRepository = FakeKnownSpellRepository(),
        spellRepository: SpellRepository = FakeSpellRepository(
            availableSources = DEFAULT_ACCEPTED_SOURCES.toList(),
            spells = emptyList(),
        ),
    ): CharacterBuilderViewModel {
        return CharacterBuilderViewModel(
            characterId = characterId,
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            spellRepository = spellRepository,
            refreshSpellcastingProjectionUseCase = RefreshSpellcastingProjectionUseCase(
                castingTrackRepository = castingTrackRepository,
                preparedSlotSyncRepository = preparedSlotSyncRepository,
                knownSpellsSeeder = DefaultKnownSpellsSeeder(
                    spellRepository = spellRepository,
                    knownSpellRepository = knownSpellRepository,
                    classSpellcastingCatalogSource = classSpellcastingCatalogSource,
                ),
                archetypeSpellcastingCatalogSource = StaticArchetypeSpellcastingCatalogSource,
            ),
            classDefinitionSource = StaticCharacterClassDefinitionSource,
            characterBuilderCatalogSource = FakeCharacterBuilderCatalogSource(),
            archetypeSpellcastingCatalogSource = StaticArchetypeSpellcastingCatalogSource,
            classSpellcastingCatalogSource = classSpellcastingCatalogSource,
        )
    }

    private fun readClassSpellcastingAsset(): String {
        val candidates = listOf(
            File("app/src/main/assets/class-spellcasting.normalized.json"),
            File("../app/src/main/assets/class-spellcasting.normalized.json"),
            File("../../app/src/main/assets/class-spellcasting.normalized.json"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("class-spellcasting.normalized.json not found from ${File(".").absolutePath}")
    }

    private fun sampleCharacter(
        id: Long,
        classId: String = "wizard",
    ): CharacterProfile {
        return CharacterProfile(
            id = id,
            name = "Test",
            level = 5,
            classId = classId,
            keyAbility = AbilityScore.INTELLIGENCE,
            spellDc = 22,
            spellAttackModifier = 12,
            legacyTerminologyEnabled = false,
        )
    }

    private fun spell(
        id: String,
        tradition: String,
        rarity: String,
        sourceBook: String,
    ): SpellListItem {
        return SpellListItem(
            id = id,
            name = id,
            rank = 1,
            tradition = tradition,
            rarity = rarity,
            sourceBook = sourceBook,
        )
    }

    private companion object {
        val DEFAULT_ACCEPTED_SOURCES: Set<String> = setOf("Player Core")
    }
}

private class FakeCharacterBuilderCatalogSource : CharacterBuilderCatalogSource {
    override suspend fun loadCatalog(): CharacterBuilderCatalogResult {
        val source = BuilderSourceRecord(
            title = "Test",
            license = "Test",
            remaster = true,
        )
        val traits = BuilderTraitsRecord(
            rarity = "common",
            values = emptyList(),
        )
        return CharacterBuilderCatalogResult(
            catalog = CharacterBuilderCatalog(
                classes = listOf(
                    BuilderClassRecord(
                        id = "wizard",
                        name = "Wizard",
                        hp = 6,
                        keyAbilityOptions = listOf(AbilityScore.INTELLIGENCE),
                        featSlots = listOf(
                            BuilderFeatSlot(
                                slotId = "wizard-class-2",
                                kind = "class",
                                level = 2,
                            ),
                        ),
                        source = source,
                        traits = traits,
                        description = "",
                        warnings = emptyList(),
                    ),
                    BuilderClassRecord(
                        id = "cleric",
                        name = "Cleric",
                        hp = 8,
                        keyAbilityOptions = listOf(AbilityScore.WISDOM),
                        featSlots = emptyList(),
                        source = source,
                        traits = traits,
                        description = "",
                        warnings = emptyList(),
                    ),
                ),
                ancestries = listOf(
                    BuilderAncestryRecord(
                        id = "human",
                        name = "Human",
                        hp = 8,
                        speed = "25 feet",
                        size = "medium",
                        source = source,
                        traits = traits,
                        description = "",
                        grants = emptyList(),
                        choicePrompts = emptyList(),
                        warnings = emptyList(),
                    ),
                ),
                heritages = listOf(
                    BuilderHeritageRecord(
                        id = "skilled-heritage",
                        name = "Skilled Heritage",
                        ancestryId = "human",
                        source = source,
                        traits = traits,
                        description = "",
                        grants = emptyList(),
                        choicePrompts = emptyList(),
                        warnings = emptyList(),
                    ),
                ),
                backgrounds = listOf(
                    BuilderBackgroundRecord(
                        id = "acolyte",
                        name = "Acolyte",
                        source = source,
                        traits = traits,
                        description = "",
                        grants = emptyList(),
                        choicePrompts = emptyList(),
                        warnings = emptyList(),
                    ),
                ),
                featIndex = listOf(
                    BuilderFeatIndexRecord(
                        id = "counterspell",
                        name = "Counterspell",
                        category = "class",
                        level = 1,
                        rarity = "common",
                        traits = emptyList(),
                        shard = "feats.class.normalized.json.gz",
                    ),
                ),
                featShards = emptyList(),
                classFeatures = emptyList(),
                ancestryFeatures = emptyList(),
            ),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeCharacterCrudRepository : CharacterCrudRepository {
    private val charactersFlow = MutableStateFlow<List<CharacterProfile>>(emptyList())
    private var nextId = 1L

    override fun observeCharacters(): Flow<List<CharacterProfile>> = charactersFlow

    override suspend fun getCharacter(characterId: Long): CharacterProfile? {
        return charactersFlow.value.firstOrNull { it.id == characterId }
    }

    override suspend fun upsertCharacter(character: CharacterProfile): Long {
        val assignedId = if (character.id == 0L) nextId++ else character.id
        val updated = character.copy(id = assignedId)
        val next = charactersFlow.value.toMutableList()
        val existingIndex = next.indexOfFirst { it.id == assignedId }
        if (existingIndex >= 0) {
            next[existingIndex] = updated
        } else {
            next += updated
        }
        charactersFlow.value = next
        return assignedId
    }

    override suspend fun deleteCharacter(characterId: Long) {
        charactersFlow.value = charactersFlow.value.filterNot { it.id == characterId }
    }

    fun characters(): List<CharacterProfile> = charactersFlow.value
}

private class FakeCharacterBuildRepository : CharacterBuildRepository {
    private val identityByCharacter = mutableMapOf<Long, CharacterBuildIdentity?>()
    private val optionsByCharacter = mutableMapOf<Long, MutableList<CharacterBuildOption>>()
    private val optionFlowByCharacter = mutableMapOf<Long, MutableStateFlow<List<CharacterBuildOption>>>()
    private var nextOptionId = 1L

    override fun observeBuildIdentity(characterId: Long): Flow<CharacterBuildIdentity?> {
        return MutableStateFlow(identityByCharacter[characterId])
    }

    override suspend fun getBuildIdentity(characterId: Long): CharacterBuildIdentity? {
        return identityByCharacter[characterId]
    }

    override suspend fun upsertBuildIdentity(identity: CharacterBuildIdentity) {
        identityByCharacter[identity.characterId] = identity
    }

    override fun observeBuildOptions(characterId: Long): Flow<List<CharacterBuildOption>> {
        return optionFlowByCharacter.getOrPut(characterId) {
            MutableStateFlow(optionsByCharacter[characterId].orEmpty().toList())
        }
    }

    override suspend fun getBuildOptions(characterId: Long): List<CharacterBuildOption> {
        return optionsByCharacter[characterId].orEmpty().toList()
    }

    override suspend fun upsertBuildOption(option: CharacterBuildOption): Long {
        val assignedId = if (option.id == 0L) nextOptionId++ else option.id
        val nextList = optionsByCharacter.getOrPut(option.characterId) { mutableListOf() }
        val updated = option.copy(id = assignedId)
        val existingIndex = nextList.indexOfFirst { it.id == assignedId }
        if (existingIndex >= 0) {
            nextList[existingIndex] = updated
        } else {
            nextList += updated
        }
        emitOptions(option.characterId)
        return assignedId
    }

    override suspend fun deleteBuildOption(
        characterId: Long,
        optionType: CharacterBuildOptionType,
        optionId: String,
    ): Boolean {
        val current = optionsByCharacter[characterId] ?: return false
        val removed = current.removeIf { it.optionType == optionType && it.optionId == optionId }
        if (removed) {
            emitOptions(characterId)
        }
        return removed
    }

    override suspend fun replaceBuildOptions(characterId: Long, options: List<CharacterBuildOption>) {
        optionsByCharacter[characterId] = options
            .map { option ->
                val assignedId = if (option.id == 0L) nextOptionId++ else option.id
                option.copy(id = assignedId, characterId = characterId)
            }
            .toMutableList()
        emitOptions(characterId)
    }

    private fun emitOptions(characterId: Long) {
        optionFlowByCharacter.getOrPut(characterId) { MutableStateFlow(emptyList()) }.value =
            optionsByCharacter[characterId].orEmpty().toList()
    }
}

private class FakeCastingTrackRepository : CastingTrackRepository {
    private val trackByCharacter = mutableMapOf<Long, MutableList<CastingTrack>>()
    private val trackFlowByCharacter = mutableMapOf<Long, MutableStateFlow<List<CastingTrack>>>()
    private var nextTrackId = 1L

    override fun observeCastingTracks(characterId: Long): Flow<List<CastingTrack>> {
        return trackFlowByCharacter.getOrPut(characterId) {
            MutableStateFlow(trackByCharacter[characterId].orEmpty().toList())
        }
    }

    override suspend fun getCastingTracks(characterId: Long): List<CastingTrack> {
        return trackByCharacter[characterId].orEmpty().toList()
    }

    override suspend fun upsertCastingTrack(track: CastingTrack): Long {
        val nextList = trackByCharacter.getOrPut(track.characterId) { mutableListOf() }
        val existingIndex = nextList.indexOfFirst { it.trackKey == track.trackKey }
        val assignedId = if (existingIndex >= 0) nextList[existingIndex].id else nextTrackId++
        val updated = track.copy(id = assignedId)
        if (existingIndex >= 0) {
            nextList[existingIndex] = updated
        } else {
            nextList += updated
        }
        emitTracks(track.characterId)
        return assignedId
    }

    override suspend fun deleteCastingTrack(characterId: Long, trackKey: String): Boolean {
        val current = trackByCharacter[characterId] ?: return false
        val removed = current.removeIf { it.trackKey == trackKey }
        if (removed) {
            emitTracks(characterId)
        }
        return removed
    }

    private fun emitTracks(characterId: Long) {
        trackFlowByCharacter.getOrPut(characterId) { MutableStateFlow(emptyList()) }.value =
            trackByCharacter[characterId].orEmpty().toList()
    }
}

private class FakePreparedSlotSyncRepository : PreparedSlotSyncRepository {
    val syncedCharacterIds = mutableListOf<Long>()

    override suspend fun syncPreparedSlotsForCharacter(characterId: Long) {
        syncedCharacterIds += characterId
    }
}

private class FakeAcceptedSpellSourceRepository : AcceptedSpellSourceRepository {
    private val sourcesByCharacter = mutableMapOf<Long, MutableStateFlow<Set<String>>>()

    override fun observeAcceptedSources(characterId: Long): Flow<Set<String>> {
        return sourcesByCharacter.getOrPut(characterId) { MutableStateFlow(emptySet()) }
    }

    override suspend fun getAcceptedSources(characterId: Long): Set<String> {
        return sourcesByCharacter[characterId]?.value.orEmpty()
    }

    override suspend fun replaceAcceptedSources(characterId: Long, sources: Set<String>) {
        sourcesByCharacter.getOrPut(characterId) { MutableStateFlow(emptySet()) }.value = sources
    }
}

private class FakeKnownSpellRepository : KnownSpellRepository {
    private val knownSpellsByCharacter = mutableMapOf<Long, MutableStateFlow<List<KnownSpell>>>()
    private var nextId = 1L

    override fun observeKnownSpells(
        characterId: Long,
        trackKey: String,
    ): Flow<List<KnownSpell>> {
        return knownSpellsByCharacter.getOrPut(characterId) { MutableStateFlow(emptyList()) }
            .map { spells -> spells.filter { it.trackKey == trackKey } }
    }

    override fun observeKnownSpellIds(characterId: Long, trackKey: String): Flow<Set<String>> {
        return observeKnownSpells(characterId, trackKey).map { spells ->
            spells.map { it.spellId }.toSet()
        }
    }

    override suspend fun addKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int?,
        origin: KnownSpellOrigin,
        isLocked: Boolean,
        isSignature: Boolean,
    ): Long {
        val flow = knownSpellsByCharacter.getOrPut(characterId) { MutableStateFlow(emptyList()) }
        if (flow.value.any { it.trackKey == trackKey && it.spellId == spellId && it.knownRank == knownRank }) {
            return -1L
        }
        val id = nextId++
        flow.value = flow.value + KnownSpell(
            id = id,
            characterId = characterId,
            trackKey = trackKey,
            spellId = spellId,
            knownRank = knownRank,
            origin = origin,
            isLocked = isLocked,
            isSignature = isSignature,
        )
        return id
    }

    override suspend fun removeKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int?,
    ): Boolean {
        val flow = knownSpellsByCharacter[characterId] ?: return false
        val updated = flow.value.filterNot { it.trackKey == trackKey && it.spellId == spellId }
        val removed = updated.size != flow.value.size
        flow.value = updated
        return removed
    }

    override suspend fun isKnownSpell(
        characterId: Long,
        trackKey: String,
        spellId: String,
        knownRank: Int?,
    ): Boolean {
        return knownSpellsByCharacter[characterId]?.value?.any {
            it.trackKey == trackKey && it.spellId == spellId && (knownRank == null || it.knownRank == knownRank)
        } == true
    }

    fun getKnownSpellIds(characterId: Long): Set<String> {
        return knownSpellsByCharacter[characterId]?.value?.map { it.spellId }?.toSet().orEmpty()
    }
}

private class FakeSpellRepository(
    private val availableSources: List<String>,
    private val spells: List<SpellListItem>,
) : SpellRepository {
    override fun observeAvailableSources(): Flow<List<String>> = MutableStateFlow(availableSources)

    override fun observeAvailableTraits(): Flow<List<String>> = MutableStateFlow(emptyList())

    override fun observeSpells(
        query: String,
        rank: Int?,
        tradition: String?,
        rarity: String?,
        trait: String?,
    ): Flow<List<SpellListItem>> {
        return MutableStateFlow(
            spells.filter { spell ->
                (query.isBlank() || spell.name.contains(query, ignoreCase = true)) &&
                    (rank == null || spell.rank == rank) &&
                    (tradition.isNullOrBlank() || spell.tradition.contains(tradition, ignoreCase = true)) &&
                    (rarity.isNullOrBlank() || spell.rarity.equals(rarity, ignoreCase = true))
            },
        )
    }

    override suspend fun getSpellDetail(spellId: String): SpellDetail? = null

    override suspend fun seedFromDatasetIfEmpty(datasetJson: String) = Unit
}
