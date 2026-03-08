package com.spellapp.feature.character

import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.CharacterBuildIdentity
import com.spellapp.core.model.CharacterBuildOption
import com.spellapp.core.model.CharacterBuildOptionType
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveCharacter_createsArchetypeTracksFromSelectedDedications_and_preservesNonManagedOptions() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val characterBuildRepository = FakeCharacterBuildRepository()
        val castingTrackRepository = FakeCastingTrackRepository()
        val preparedSlotSyncRepository = FakePreparedSlotSyncRepository()
        val viewModel = createViewModel(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
        )
        val existingCharacterId = characterCrudRepository.upsertCharacter(sampleCharacter(id = 10L))
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

        viewModel.saveCharacter(
            character = sampleCharacter(id = existingCharacterId),
            selectedBuildOptionIds = setOf(
                "archetype/wizard/wizard-dedication",
                "archetype/wizard/basic-wizard-spellcasting",
                "archetype/cleric/cleric-dedication",
                "not/managed-by/catalog",
            ),
            acceptedSourceBooks = DEFAULT_ACCEPTED_SOURCES,
        )
        advanceUntilIdle()

        val savedOptions = characterBuildRepository.getBuildOptions(existingCharacterId)
        val savedOptionIds = savedOptions.map { it.optionId }.toSet()
        assertTrue("custom/non-managed" in savedOptionIds)
        assertTrue("archetype/wizard/wizard-dedication" in savedOptionIds)
        assertTrue("archetype/wizard/basic-wizard-spellcasting" in savedOptionIds)
        assertTrue("archetype/cleric/cleric-dedication" in savedOptionIds)
        assertFalse("archetype/druid/druid-dedication" in savedOptionIds)
        assertFalse("not/managed-by/catalog" in savedOptionIds)

        val byOptionId = savedOptions.associateBy { it.optionId }
        assertEquals(
            CharacterBuildOptionType.ARCHETYPE,
            byOptionId["archetype/wizard/wizard-dedication"]?.optionType,
        )
        assertEquals(
            CharacterBuildOptionType.FEAT,
            byOptionId["archetype/wizard/basic-wizard-spellcasting"]?.optionType,
        )
        assertEquals(
            CharacterBuildOptionType.ARCHETYPE,
            byOptionId["archetype/cleric/cleric-dedication"]?.optionType,
        )

        val archetypeTracks = castingTrackRepository.getCastingTracks(existingCharacterId)
            .filter { it.sourceType == CastingTrackSourceType.ARCHETYPE }
        val archetypeLabels = archetypeTracks
            .map { it.sourceId }
            .toSet()
        assertEquals(setOf("Wizard", "Cleric"), archetypeLabels)
        assertTrue(archetypeTracks.all { it.progressionType == CastingProgressionType.ARCHETYPE_PREPARED })
        assertEquals(2, archetypeTracks.size)
        assertEquals(listOf(existingCharacterId), preparedSlotSyncRepository.syncedCharacterIds)
    }

    @Test
    fun saveCharacter_withManagedStateAndNoSelections_clearsManagedOptions_and_removesArchetypeTracks() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val characterBuildRepository = FakeCharacterBuildRepository()
        val castingTrackRepository = FakeCastingTrackRepository()
        val preparedSlotSyncRepository = FakePreparedSlotSyncRepository()
        val viewModel = createViewModel(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
        )
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
                    optionId = "archetype/wizard/wizard-dedication",
                ),
            ),
        )
        castingTrackRepository.upsertCastingTrack(
            CastingTrack(
                characterId = existingCharacterId,
                trackKey = "primary",
                sourceType = CastingTrackSourceType.PRIMARY_CLASS,
                sourceId = "Wizard",
                progressionType = CastingProgressionType.FULL_PREPARED,
            ),
        )
        castingTrackRepository.upsertCastingTrack(
            CastingTrack(
                characterId = existingCharacterId,
                trackKey = "archetype-wizard",
                sourceType = CastingTrackSourceType.ARCHETYPE,
                sourceId = "Wizard",
                progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
            ),
        )

        viewModel.saveCharacter(
            character = sampleCharacter(id = existingCharacterId),
            selectedBuildOptionIds = emptySet(),
            acceptedSourceBooks = DEFAULT_ACCEPTED_SOURCES,
        )
        advanceUntilIdle()

        val remainingOptions = characterBuildRepository.getBuildOptions(existingCharacterId)
        assertEquals(setOf("custom/non-managed"), remainingOptions.map { it.optionId }.toSet())

        val remainingTracks = castingTrackRepository.getCastingTracks(existingCharacterId)
        val remainingTrackTypes = remainingTracks
            .map { it.sourceType }
            .toSet()
        assertEquals(setOf(CastingTrackSourceType.PRIMARY_CLASS), remainingTrackTypes)
        assertEquals(listOf(existingCharacterId), preparedSlotSyncRepository.syncedCharacterIds)
    }

    @Test
    fun saveCharacter_withNoManagedState_doesNotMutateLegacyArchetypeTracks() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val characterBuildRepository = FakeCharacterBuildRepository()
        val castingTrackRepository = FakeCastingTrackRepository()
        val preparedSlotSyncRepository = FakePreparedSlotSyncRepository()
        val viewModel = createViewModel(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
        )
        val existingCharacterId = characterCrudRepository.upsertCharacter(sampleCharacter(id = 42L))
        val originalTrack = CastingTrack(
            characterId = existingCharacterId,
            trackKey = "archetype-legacy",
            sourceType = CastingTrackSourceType.ARCHETYPE,
            sourceId = "Legacy Archetype",
            progressionType = CastingProgressionType.ARCHETYPE_PREPARED,
        )
        castingTrackRepository.upsertCastingTrack(originalTrack)

        viewModel.saveCharacter(
            character = sampleCharacter(id = existingCharacterId),
            selectedBuildOptionIds = emptySet(),
            acceptedSourceBooks = DEFAULT_ACCEPTED_SOURCES,
        )
        advanceUntilIdle()

        val tracksAfterSave = castingTrackRepository.getCastingTracks(existingCharacterId)
        assertEquals(1, tracksAfterSave.size)
        assertEquals("Legacy Archetype", tracksAfterSave.first().sourceId)
        assertEquals(CastingTrackSourceType.ARCHETYPE, tracksAfterSave.first().sourceType)
        assertTrue(characterBuildRepository.getBuildOptions(existingCharacterId).isEmpty())
        assertEquals(listOf(existingCharacterId), preparedSlotSyncRepository.syncedCharacterIds)
    }

    @Test
    fun saveCharacter_sameSelection_isIdempotent() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val characterBuildRepository = FakeCharacterBuildRepository()
        val castingTrackRepository = FakeCastingTrackRepository()
        val preparedSlotSyncRepository = FakePreparedSlotSyncRepository()
        val viewModel = createViewModel(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
        )
        val existingCharacterId = characterCrudRepository.upsertCharacter(sampleCharacter(id = 43L))
        val selection = setOf(
            "archetype/wizard/wizard-dedication",
            "archetype/wizard/basic-wizard-spellcasting",
        )

        viewModel.saveCharacter(sampleCharacter(id = existingCharacterId), selection, DEFAULT_ACCEPTED_SOURCES)
        advanceUntilIdle()
        viewModel.saveCharacter(sampleCharacter(id = existingCharacterId), selection, DEFAULT_ACCEPTED_SOURCES)
        advanceUntilIdle()

        val savedOptionIds = characterBuildRepository.getBuildOptions(existingCharacterId)
            .map { it.optionId }
        assertEquals(2, savedOptionIds.size)
        assertEquals(selection, savedOptionIds.toSet())

        val tracks = castingTrackRepository.getCastingTracks(existingCharacterId)
            .filter { it.sourceType == CastingTrackSourceType.ARCHETYPE }
        assertEquals(1, tracks.size)
        assertEquals("Wizard", tracks.first().sourceId)
        assertEquals(
            listOf(existingCharacterId, existingCharacterId),
            preparedSlotSyncRepository.syncedCharacterIds,
        )
    }

    @Test
    fun onEditCharacterRequest_exposesOnlyManagedBuildOptionIds() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val characterBuildRepository = FakeCharacterBuildRepository()
        val castingTrackRepository = FakeCastingTrackRepository()
        val preparedSlotSyncRepository = FakePreparedSlotSyncRepository()
        val viewModel = createViewModel(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
        )
        val existingCharacterId = characterCrudRepository.upsertCharacter(sampleCharacter(id = 44L))
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

        viewModel.onEditCharacterRequest(sampleCharacter(id = existingCharacterId))
        advanceUntilIdle()

        val selectedIds = viewModel.uiState
            .map { it.editingSelectedBuildOptionIds }
            .first { it == setOf("archetype/wizard/wizard-dedication") }
        assertEquals(setOf("archetype/wizard/wizard-dedication"), selectedIds)
    }

    @Test
    fun saveCharacter_withBlessedOneDedication_persistsOption_withoutCreatingPreparedArchetypeTrack() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val characterBuildRepository = FakeCharacterBuildRepository()
        val castingTrackRepository = FakeCastingTrackRepository()
        val preparedSlotSyncRepository = FakePreparedSlotSyncRepository()
        val viewModel = createViewModel(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
        )
        val existingCharacterId = characterCrudRepository.upsertCharacter(sampleCharacter(id = 45L))

        viewModel.saveCharacter(
            character = sampleCharacter(id = existingCharacterId),
            selectedBuildOptionIds = setOf("archetype/blessed-one/blessed-one-dedication"),
            acceptedSourceBooks = DEFAULT_ACCEPTED_SOURCES,
        )
        advanceUntilIdle()

        val savedOptions = characterBuildRepository.getBuildOptions(existingCharacterId)
        assertEquals(setOf("archetype/blessed-one/blessed-one-dedication"), savedOptions.map { it.optionId }.toSet())
        assertEquals(CharacterBuildOptionType.ARCHETYPE, savedOptions.single().optionType)

        val archetypeTracks = castingTrackRepository.getCastingTracks(existingCharacterId)
            .filter { it.sourceType == CastingTrackSourceType.ARCHETYPE }
        assertTrue(archetypeTracks.isEmpty())
        assertEquals(listOf(existingCharacterId), preparedSlotSyncRepository.syncedCharacterIds)
    }

    @Test
    fun saveCharacter_persistsAcceptedSources_and_seedsCommonKnownSpells_forCleric() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val characterBuildRepository = FakeCharacterBuildRepository()
        val castingTrackRepository = FakeCastingTrackRepository()
        val preparedSlotSyncRepository = FakePreparedSlotSyncRepository()
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
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
        )

        viewModel.saveCharacter(
            character = sampleCharacter(id = 0L, characterClass = CharacterClass.CLERIC),
            selectedBuildOptionIds = emptySet(),
            acceptedSourceBooks = setOf("Player Core"),
        )
        advanceUntilIdle()

        val savedCharacterId = characterCrudRepository.observeCharacters().first().single().id
        assertEquals(setOf("Player Core"), acceptedSpellSourceRepository.getAcceptedSources(savedCharacterId))
        assertEquals(setOf("heal"), knownSpellRepository.getKnownSpellIds(savedCharacterId))
    }

    @Test
    fun saveCharacter_doesNotReseedKnownSpells_whenCharacterIsExisting() = runTest {
        val characterCrudRepository = FakeCharacterCrudRepository()
        val knownSpellRepository = FakeKnownSpellRepository()
        val spellRepository = FakeSpellRepository(
            availableSources = listOf("Player Core"),
            spells = listOf(
                spell(id = "heal", tradition = "divine", rarity = "common", sourceBook = "Player Core"),
            ),
        )
        val viewModel = createViewModel(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = FakeCharacterBuildRepository(),
            castingTrackRepository = FakeCastingTrackRepository(),
            preparedSlotSyncRepository = FakePreparedSlotSyncRepository(),
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
        )
        val existingId = characterCrudRepository.upsertCharacter(
            sampleCharacter(id = 50L, characterClass = CharacterClass.CLERIC),
        )

        viewModel.saveCharacter(
            character = sampleCharacter(id = existingId, characterClass = CharacterClass.CLERIC),
            selectedBuildOptionIds = emptySet(),
            acceptedSourceBooks = setOf("Player Core"),
        )
        advanceUntilIdle()

        assertEquals(emptySet<String>(), knownSpellRepository.getKnownSpellIds(existingId))
    }

    private fun createViewModel(
        characterCrudRepository: CharacterCrudRepository,
        characterBuildRepository: CharacterBuildRepository,
        castingTrackRepository: CastingTrackRepository,
        preparedSlotSyncRepository: PreparedSlotSyncRepository,
        acceptedSpellSourceRepository: AcceptedSpellSourceRepository = FakeAcceptedSpellSourceRepository(),
        knownSpellRepository: KnownSpellRepository = FakeKnownSpellRepository(),
        spellRepository: SpellRepository = FakeSpellRepository(
            availableSources = DEFAULT_ACCEPTED_SOURCES.toList(),
            spells = emptyList(),
        ),
    ): CharacterListViewModel {
        return CharacterListViewModel(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            spellRepository = spellRepository,
            knownSpellRepository = knownSpellRepository,
            classDefinitionSource = StaticCharacterClassDefinitionSource,
            archetypeSpellcastingCatalogSource = StaticArchetypeSpellcastingCatalogSource,
        )
    }

    private fun sampleCharacter(
        id: Long,
        characterClass: CharacterClass = CharacterClass.WIZARD,
    ): CharacterProfile {
        return CharacterProfile(
            id = id,
            name = "Test",
            level = 5,
            characterClass = characterClass,
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

    override suspend fun addKnownSpell(characterId: Long, trackKey: String, spellId: String): Long {
        val flow = knownSpellsByCharacter.getOrPut(characterId) { MutableStateFlow(emptyList()) }
        if (flow.value.any { it.trackKey == trackKey && it.spellId == spellId }) {
            return -1L
        }
        val id = nextId++
        flow.value = flow.value + KnownSpell(
            id = id,
            characterId = characterId,
            trackKey = trackKey,
            spellId = spellId,
        )
        return id
    }

    override suspend fun removeKnownSpell(characterId: Long, trackKey: String, spellId: String): Boolean {
        val flow = knownSpellsByCharacter[characterId] ?: return false
        val updated = flow.value.filterNot { it.trackKey == trackKey && it.spellId == spellId }
        val removed = updated.size != flow.value.size
        flow.value = updated
        return removed
    }

    override suspend fun isKnownSpell(characterId: Long, trackKey: String, spellId: String): Boolean {
        return knownSpellsByCharacter[characterId]?.value?.any {
            it.trackKey == trackKey && it.spellId == spellId
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
