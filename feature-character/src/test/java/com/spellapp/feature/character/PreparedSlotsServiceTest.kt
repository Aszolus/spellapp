package com.spellapp.feature.character

import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CharacterBuildIdentity
import com.spellapp.core.model.CharacterBuildOption
import com.spellapp.core.model.CharacterBuildOptionType
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.FocusState
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEvent
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreparedSlotsServiceTest {
    @Test
    fun prepareRandom_allowsSameRank_withoutHeightenedText() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 3)),
            spells = listOf(spell(id = "spell-3", rank = 3)),
            details = mapOf("spell-3" to detail(id = "spell-3", rank = 3, description = "No heightened block")),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertEquals("spell-3", fixture.preparedSlotRepository.preparedSpellIdFor(rank = 3, slotIndex = 0))
    }

    @Test
    fun prepareRandom_blocksLowerRank_whenNoHeightenedBlock() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 3)),
            spells = listOf(spell(id = "spell-2", rank = 2)),
            details = mapOf("spell-2" to detail(id = "spell-2", rank = 2, description = "No heightened block")),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertNull(fixture.preparedSlotRepository.preparedSpellIdFor(rank = 3, slotIndex = 0))
    }

    @Test
    fun prepareRandom_blocksLowerRank_whenHeightenedPlusDoesNotTriggerAtTargetRank() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 3)),
            spells = listOf(spell(id = "spell-plus-2", rank = 2)),
            details = mapOf(
                "spell-plus-2" to detail(
                    id = "spell-plus-2",
                    rank = 2,
                    description = "Some text\n\n---\nHeightened (+2) Gains stronger effects.",
                ),
            ),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertNull(fixture.preparedSlotRepository.preparedSpellIdFor(rank = 3, slotIndex = 0))
    }

    @Test
    fun prepareRandom_allowsLowerRank_whenHeightenedPlusTriggersAtTargetRank() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 4)),
            spells = listOf(spell(id = "spell-plus-2", rank = 2)),
            details = mapOf(
                "spell-plus-2" to detail(
                    id = "spell-plus-2",
                    rank = 2,
                    description = "Some text\n\n---\nHeightened (+2) Gains stronger effects.",
                ),
            ),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertEquals("spell-plus-2", fixture.preparedSlotRepository.preparedSpellIdFor(rank = 4, slotIndex = 0))
    }

    @Test
    fun prepareRandom_blocksLowerRank_whenAbsoluteHeightenedNotYetReached() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 3)),
            spells = listOf(spell(id = "spell-absolute-4", rank = 2)),
            details = mapOf(
                "spell-absolute-4" to detail(
                    id = "spell-absolute-4",
                    rank = 2,
                    description = "Some text\n\n---\nHeightened (4th) Gains stronger effects.",
                ),
            ),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertNull(fixture.preparedSlotRepository.preparedSpellIdFor(rank = 3, slotIndex = 0))
    }

    @Test
    fun prepareRandom_allowsLowerRank_whenAbsoluteHeightenedReachedAtTargetRank() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 4)),
            spells = listOf(spell(id = "spell-absolute-4", rank = 2)),
            details = mapOf(
                "spell-absolute-4" to detail(
                    id = "spell-absolute-4",
                    rank = 2,
                    description = "Some text\n\n---\nHeightened (4th) Gains stronger effects.",
                ),
            ),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertEquals("spell-absolute-4", fixture.preparedSlotRepository.preparedSpellIdFor(rank = 4, slotIndex = 0))
    }

    @Test
    fun prepareRandom_blocksLowerRank_whenAbsoluteHeightenedAlreadyAtOrBelowBaseRank() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 5)),
            spells = listOf(spell(id = "spell-absolute-4", rank = 4)),
            details = mapOf(
                "spell-absolute-4" to detail(
                    id = "spell-absolute-4",
                    rank = 4,
                    description = "Some text\n\n---\nHeightened (4th) Gains stronger effects.",
                ),
            ),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertNull(fixture.preparedSlotRepository.preparedSpellIdFor(rank = 5, slotIndex = 0))
    }

    @Test
    fun prepareRandom_cantripSlot_onlyAcceptsRankZeroSpells() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 0)),
            spells = listOf(
                spell(id = "non-cantrip", rank = 1),
                spell(id = "cantrip", rank = 0, isCantrip = true),
            ),
            details = emptyMap(),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertEquals("cantrip", fixture.preparedSlotRepository.preparedSpellIdFor(rank = 0, slotIndex = 0))
    }

    @Test
    fun prepareRandom_usesKnownSpellsOnly() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 1)),
            spells = listOf(
                spell(id = "known-spell", rank = 1),
                spell(id = "unknown-spell", rank = 1),
            ),
            details = mapOf(
                "known-spell" to detail(id = "known-spell", rank = 1, description = ""),
                "unknown-spell" to detail(id = "unknown-spell", rank = 1, description = ""),
            ),
            knownSpellIds = setOf("known-spell"),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertEquals("known-spell", fixture.preparedSlotRepository.preparedSpellIdFor(rank = 1, slotIndex = 0))
    }

    @Test
    fun prepareRandom_prefers_track_tradition_when_available() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 1)),
            spells = listOf(
                spell(id = "arcane-spell", rank = 1, tradition = "arcane"),
                spell(id = "primal-spell", rank = 1, tradition = "primal"),
            ),
            details = mapOf(
                "arcane-spell" to detail(id = "arcane-spell", rank = 1, description = "", tradition = "arcane"),
                "primal-spell" to detail(id = "primal-spell", rank = 1, description = "", tradition = "primal"),
            ),
            tracks = listOf(
                CastingTrack(
                    characterId = CHARACTER_ID,
                    trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                    sourceType = com.spellapp.core.model.CastingTrackSourceType.PRIMARY_CLASS,
                    sourceId = "DRUID",
                    progressionType = com.spellapp.core.model.CastingProgressionType.FULL_PREPARED,
                ),
            ),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertEquals("primal-spell", fixture.preparedSlotRepository.preparedSpellIdFor(rank = 1, slotIndex = 0))
    }

    @Test
    fun prepareRandom_leaves_slot_empty_when_only_off_tradition_known_spells_exist() = runTest {
        val fixture = fixture(
            slots = listOf(emptySlot(rank = 1)),
            spells = listOf(
                spell(id = "arcane-spell", rank = 1, tradition = "arcane"),
            ),
            details = mapOf(
                "arcane-spell" to detail(id = "arcane-spell", rank = 1, description = "", tradition = "arcane"),
            ),
            tracks = listOf(
                CastingTrack(
                    characterId = CHARACTER_ID,
                    trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                    sourceType = com.spellapp.core.model.CastingTrackSourceType.PRIMARY_CLASS,
                    sourceId = "DRUID",
                    progressionType = com.spellapp.core.model.CastingProgressionType.FULL_PREPARED,
                ),
            ),
        )

        fixture.service.prepareRandom(characterId = CHARACTER_ID, trackKey = PreparedSlot.PRIMARY_TRACK_KEY)

        assertNull(fixture.preparedSlotRepository.preparedSpellIdFor(rank = 1, slotIndex = 0))
    }

    private fun fixture(
        slots: List<PreparedSlot>,
        spells: List<SpellListItem>,
        details: Map<String, SpellDetail>,
        knownSpellIds: Set<String> = spells.map { it.id }.toSet(),
        tracks: List<CastingTrack> = emptyList(),
    ): TestFixture {
        val preparedSlotRepository = FakePreparedSlotRepository(
            slotsByTrack = mapOf(
                PreparedSlot.PRIMARY_TRACK_KEY to slots.map { slot ->
                    slot.copy(trackKey = PreparedSlot.PRIMARY_TRACK_KEY, characterId = CHARACTER_ID)
                },
            ),
        )
        val knownSpellRepository = FakeKnownSpellRepository(knownSpellIds = knownSpellIds)
        val spellRepository = FakeSpellRepository(
            spells = spells,
            detailsById = details,
        )
        val characterCrudRepository = FakeCharacterCrudRepository(
            character = characterProfile(characterClass = CharacterClass.WIZARD),
        )
        val service = PreparedSlotsService(
            preparedSlotRepository = preparedSlotRepository,
            castingTrackRepository = FakeCastingTrackRepository(tracks),
            preparedSlotSyncRepository = FakePreparedSlotSyncRepository(),
            sessionEventRepository = FakeSessionEventRepository(),
            focusStateRepository = FakeFocusStateRepository(),
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = FakeCharacterBuildRepository(),
        )
        return TestFixture(
            service = service,
            preparedSlotRepository = preparedSlotRepository,
        )
    }

    private data class TestFixture(
        val service: PreparedSlotsService,
        val preparedSlotRepository: FakePreparedSlotRepository,
    )

    private class FakePreparedSlotRepository(
        slotsByTrack: Map<String, List<PreparedSlot>>,
    ) : PreparedSlotRepository {
        private val slotsByTrack = slotsByTrack.mapValues { (_, value) ->
            MutableStateFlow(value.toMutableList())
        }.toMutableMap()

        override fun observePreparedSlots(
            characterId: Long,
            trackKey: String?,
        ): Flow<List<PreparedSlot>> {
            if (trackKey == null) {
                val all = slotsByTrack.values.flatMap { flow -> flow.value }
                return flowOf(all)
            }
            return slotsByTrack.getOrPut(trackKey) { MutableStateFlow(mutableListOf()) }
        }

        override suspend fun addPreparedSlot(characterId: Long, rank: Int, trackKey: String): Long = 0L

        override suspend fun removePreparedSlot(
            characterId: Long,
            rank: Int,
            slotIndex: Int,
            trackKey: String,
        ): Boolean = false

        override suspend fun clearPreparedSlotSpell(
            characterId: Long,
            rank: Int,
            slotIndex: Int,
            trackKey: String,
        ): Boolean = false

        override suspend fun assignSpellToPreparedSlot(
            characterId: Long,
            rank: Int,
            slotIndex: Int,
            spellId: String,
            trackKey: String,
        ) {
            val flow = slotsByTrack.getOrPut(trackKey) { MutableStateFlow(mutableListOf()) }
            val current = flow.value.toMutableList()
            val index = current.indexOfFirst { slot ->
                slot.characterId == characterId &&
                    slot.trackKey == trackKey &&
                    slot.rank == rank &&
                    slot.slotIndex == slotIndex
            }
            if (index >= 0) {
                current[index] = current[index].copy(preparedSpellId = spellId)
            } else {
                current += PreparedSlot(
                    characterId = characterId,
                    trackKey = trackKey,
                    rank = rank,
                    slotIndex = slotIndex,
                    preparedSpellId = spellId,
                )
            }
            flow.value = current
        }

        override suspend fun castPreparedSlot(
            characterId: Long,
            rank: Int,
            slotIndex: Int,
            trackKey: String,
        ): Boolean = false

        override suspend fun uncastSlot(
            characterId: Long,
            rank: Int,
            slotIndex: Int,
            trackKey: String,
        ): Boolean = false

        override suspend fun restoreAllSlotsForTrack(characterId: Long, trackKey: String): Int = 0

        override suspend fun clearPreparedSlotsForTrack(characterId: Long, trackKey: String): Int = 0

        override suspend fun undoLastCast(characterId: Long, trackKey: String?): Boolean = false

        fun preparedSpellIdFor(rank: Int, slotIndex: Int): String? {
            return slotsByTrack[PreparedSlot.PRIMARY_TRACK_KEY]
                ?.value
                ?.firstOrNull { slot -> slot.rank == rank && slot.slotIndex == slotIndex }
                ?.preparedSpellId
        }
    }

    private class FakeSpellRepository(
        private val spells: List<SpellListItem>,
        private val detailsById: Map<String, SpellDetail>,
    ) : SpellRepository {
        override fun observeAvailableSources(): Flow<List<String>> {
            return flowOf(spells.map { it.sourceBook }.filter { it.isNotBlank() }.distinct().sorted())
        }

        override fun observeAvailableTraits(): Flow<List<String>> {
            return flowOf(emptyList())
        }

        override fun observeSpells(
            query: String,
            rank: Int?,
            tradition: String?,
            rarity: String?,
            trait: String?,
        ): Flow<List<SpellListItem>> {
            val filtered = spells
                .filter { spell ->
                    query.isBlank() || spell.name.contains(query, ignoreCase = true)
                }
                .filter { spell ->
                    rank == null || spell.rank == rank
                }
                .filter { spell ->
                    tradition.isNullOrBlank() || spell.tradition.contains(tradition, ignoreCase = true)
                }
                .filter { spell ->
                    rarity.isNullOrBlank() || spell.rarity.equals(rarity, ignoreCase = true)
                }
            return flowOf(filtered)
        }

        override suspend fun getSpellDetail(spellId: String): SpellDetail? {
            return detailsById[spellId] ?: spells.firstOrNull { it.id == spellId }?.let { spell ->
                SpellDetail(
                    id = spell.id,
                    name = spell.name,
                    rank = spell.rank,
                    tradition = spell.tradition,
                    rarity = spell.rarity,
                    traits = emptyList(),
                    castTime = "2 actions",
                    range = "",
                    target = "",
                    duration = "",
                    description = "",
                    license = "",
                    sourceBook = spell.sourceBook,
                    sourcePage = null,
                )
            }
        }

        override suspend fun seedFromDatasetIfEmpty(datasetJson: String) = Unit
    }

    private class FakeKnownSpellRepository(
        knownSpellIds: Set<String>,
    ) : KnownSpellRepository {
        private val knownSpells = MutableStateFlow(
            knownSpellIds.mapIndexed { index, spellId ->
                KnownSpell(
                    id = index + 1L,
                    characterId = CHARACTER_ID,
                    trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                    spellId = spellId,
                )
            }
        )

        override fun observeKnownSpells(characterId: Long, trackKey: String): Flow<List<KnownSpell>> {
            return knownSpells
        }

        override fun observeKnownSpellIds(characterId: Long, trackKey: String): Flow<Set<String>> {
            return knownSpells.map { spells -> spells.map { it.spellId }.toSet() }
        }

        override suspend fun addKnownSpell(characterId: Long, trackKey: String, spellId: String): Long {
            val nextId = (knownSpells.value.maxOfOrNull { it.id } ?: 0L) + 1L
            knownSpells.value = knownSpells.value + KnownSpell(
                id = nextId,
                characterId = characterId,
                trackKey = trackKey,
                spellId = spellId,
            )
            return nextId
        }

        override suspend fun removeKnownSpell(characterId: Long, trackKey: String, spellId: String): Boolean {
            val updated = knownSpells.value.filterNot { it.spellId == spellId }
            val removed = updated.size != knownSpells.value.size
            knownSpells.value = updated
            return removed
        }

        override suspend fun isKnownSpell(characterId: Long, trackKey: String, spellId: String): Boolean {
            return knownSpells.value.any { it.spellId == spellId }
        }
    }

    private class FakeCharacterCrudRepository(
        private val character: CharacterProfile,
    ) : CharacterCrudRepository {
        override fun observeCharacters(): Flow<List<CharacterProfile>> = flowOf(listOf(character))

        override suspend fun getCharacter(characterId: Long): CharacterProfile? {
            return character.takeIf { it.id == characterId }
        }

        override suspend fun upsertCharacter(character: CharacterProfile): Long = character.id

        override suspend fun deleteCharacter(characterId: Long) = Unit
    }

    private class FakeCharacterBuildRepository : CharacterBuildRepository {
        override fun observeBuildIdentity(characterId: Long): Flow<CharacterBuildIdentity?> = flowOf(null)

        override suspend fun getBuildIdentity(characterId: Long): CharacterBuildIdentity? = null

        override suspend fun upsertBuildIdentity(identity: CharacterBuildIdentity) = Unit

        override fun observeBuildOptions(characterId: Long): Flow<List<CharacterBuildOption>> = flowOf(emptyList())

        override suspend fun getBuildOptions(characterId: Long): List<CharacterBuildOption> = emptyList()

        override suspend fun upsertBuildOption(option: CharacterBuildOption): Long = 0L

        override suspend fun deleteBuildOption(
            characterId: Long,
            optionType: CharacterBuildOptionType,
            optionId: String,
        ): Boolean = false

        override suspend fun replaceBuildOptions(
            characterId: Long,
            options: List<CharacterBuildOption>,
        ) = Unit
    }

    private class FakeCastingTrackRepository(
        private val tracks: List<CastingTrack>,
    ) : CastingTrackRepository {
        override fun observeCastingTracks(characterId: Long): Flow<List<CastingTrack>> = flowOf(tracks)

        override suspend fun getCastingTracks(characterId: Long): List<CastingTrack> = tracks

        override suspend fun upsertCastingTrack(track: CastingTrack): Long = 0L

        override suspend fun deleteCastingTrack(characterId: Long, trackKey: String): Boolean = false
    }

    private class FakePreparedSlotSyncRepository : PreparedSlotSyncRepository {
        override suspend fun syncPreparedSlotsForCharacter(characterId: Long) = Unit
    }

    private class FakeSessionEventRepository : SessionEventRepository {
        override fun observeSessionEvents(characterId: Long, trackKey: String?): Flow<List<SessionEvent>> =
            flowOf(emptyList())

        override suspend fun appendSessionEvent(event: SessionEvent): Long = 0L
    }

    private class FakeFocusStateRepository : FocusStateRepository {
        override fun observeFocusState(characterId: Long): Flow<FocusState?> = flowOf(null)

        override suspend fun upsertFocusState(state: FocusState) = Unit
    }

    private fun emptySlot(rank: Int, slotIndex: Int = 0): PreparedSlot {
        return PreparedSlot(
            characterId = CHARACTER_ID,
            rank = rank,
            slotIndex = slotIndex,
            preparedSpellId = null,
        )
    }

    private fun spell(
        id: String,
        rank: Int,
        tradition: String = "arcane",
        rarity: String = "common",
        sourceBook: String = "",
        isCantrip: Boolean = rank == 0,
    ): SpellListItem {
        return SpellListItem(
            id = id,
            name = id.replace('-', ' '),
            rank = rank,
            tradition = tradition,
            rarity = rarity,
            sourceBook = sourceBook,
            isCantrip = isCantrip,
        )
    }

    private fun detail(
        id: String,
        rank: Int,
        description: String,
        tradition: String = "arcane",
    ): SpellDetail {
        return SpellDetail(
            id = id,
            name = id.replace('-', ' '),
            rank = rank,
            tradition = tradition,
            rarity = "common",
            traits = emptyList(),
            castTime = "2 actions",
            range = "",
            target = "",
            duration = "",
            description = description,
            license = "",
            sourceBook = "",
            sourcePage = null,
        )
    }

    private fun characterProfile(characterClass: CharacterClass): CharacterProfile {
        return CharacterProfile(
            id = CHARACTER_ID,
            name = "Tester",
            level = 10,
            characterClass = characterClass,
            keyAbility = AbilityScore.INTELLIGENCE,
            spellDc = 28,
            spellAttackModifier = 18,
        )
    }

    private companion object {
        private const val CHARACTER_ID = 100L
    }
}
