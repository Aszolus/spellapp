package com.spellapp.feature.spells

import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignPreparedSpellUseCaseTest {
    @Test
    fun assign_rejects_spell_that_is_not_known() = runBlocking {
        val preparedSlotRepository = FakePreparedSlotRepository()
        val useCase = AssignPreparedSpellUseCase(
            characterCrudRepository = FakeCharacterCrudRepository(),
            knownSpellRepository = FakeKnownSpellRepository(emptySet()),
            preparedSlotRepository = preparedSlotRepository,
            spellRepository = FakeSpellRepository(
                detailsById = mapOf("magic-missile" to spellDetail("magic-missile", 1, "arcane")),
            ),
        )

        val success = useCase.assign(
            mode = SpellBrowserMode.AssignPreparedSlot(
                characterId = CHARACTER_ID,
                trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                slotRank = 1,
                slotIndex = 0,
            ),
            spellId = "magic-missile",
        )

        assertFalse(success)
        assertEquals(emptyList<Assignment>(), preparedSlotRepository.assignments)
    }

    @Test
    fun assign_rejects_illegal_spell_for_track() = runBlocking {
        val preparedSlotRepository = FakePreparedSlotRepository()
        val useCase = AssignPreparedSpellUseCase(
            characterCrudRepository = FakeCharacterCrudRepository(),
            knownSpellRepository = FakeKnownSpellRepository(setOf("heal")),
            preparedSlotRepository = preparedSlotRepository,
            spellRepository = FakeSpellRepository(
                detailsById = mapOf("heal" to spellDetail("heal", 1, "divine")),
            ),
        )

        val success = useCase.assign(
            mode = SpellBrowserMode.AssignPreparedSlot(
                characterId = CHARACTER_ID,
                trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                slotRank = 1,
                slotIndex = 0,
            ),
            spellId = "heal",
        )

        assertFalse(success)
        assertEquals(emptyList<Assignment>(), preparedSlotRepository.assignments)
    }

    @Test
    fun assign_accepts_known_and_legal_spell() = runBlocking {
        val preparedSlotRepository = FakePreparedSlotRepository()
        val useCase = AssignPreparedSpellUseCase(
            characterCrudRepository = FakeCharacterCrudRepository(),
            knownSpellRepository = FakeKnownSpellRepository(setOf("magic-missile")),
            preparedSlotRepository = preparedSlotRepository,
            spellRepository = FakeSpellRepository(
                detailsById = mapOf("magic-missile" to spellDetail("magic-missile", 1, "arcane")),
            ),
        )

        val success = useCase.assign(
            mode = SpellBrowserMode.AssignPreparedSlot(
                characterId = CHARACTER_ID,
                trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                slotRank = 1,
                slotIndex = 0,
            ),
            spellId = "magic-missile",
        )

        assertTrue(success)
        assertEquals(
            listOf(
                Assignment(
                    characterId = CHARACTER_ID,
                    rank = 1,
                    slotIndex = 0,
                    spellId = "magic-missile",
                    trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                ),
            ),
            preparedSlotRepository.assignments,
        )
    }

    private class FakeCharacterCrudRepository : CharacterCrudRepository {
        private val character = CharacterProfile(
            id = CHARACTER_ID,
            name = "Merisiel",
            level = 5,
            characterClass = CharacterClass.WIZARD,
            keyAbility = AbilityScore.INTELLIGENCE,
            spellDc = 21,
            spellAttackModifier = 11,
        )

        override fun observeCharacters(): Flow<List<CharacterProfile>> = flowOf(listOf(character))

        override suspend fun getCharacter(characterId: Long): CharacterProfile? = character

        override suspend fun upsertCharacter(character: CharacterProfile): Long = character.id

        override suspend fun deleteCharacter(characterId: Long) = Unit
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
            },
        )

        override fun observeKnownSpells(characterId: Long, trackKey: String): Flow<List<KnownSpell>> = knownSpells

        override fun observeKnownSpellIds(characterId: Long, trackKey: String): Flow<Set<String>> {
            return knownSpells.map { spells -> spells.map { it.spellId }.toSet() }
        }

        override suspend fun addKnownSpell(characterId: Long, trackKey: String, spellId: String): Long = 0L

        override suspend fun removeKnownSpell(characterId: Long, trackKey: String, spellId: String): Boolean = false

        override suspend fun isKnownSpell(characterId: Long, trackKey: String, spellId: String): Boolean {
            return knownSpells.value.any { it.spellId == spellId }
        }
    }

    private class FakePreparedSlotRepository : PreparedSlotRepository {
        val assignments = mutableListOf<Assignment>()

        override fun observePreparedSlots(characterId: Long, trackKey: String?): Flow<List<PreparedSlot>> =
            flowOf(emptyList())

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
            assignments += Assignment(characterId, rank, slotIndex, spellId, trackKey)
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
    }

    private class FakeSpellRepository(
        private val detailsById: Map<String, SpellDetail>,
    ) : SpellRepository {
        override fun observeAvailableSources(): Flow<List<String>> = flowOf(emptyList())

        override fun observeSpells(
            query: String,
            rank: Int?,
            tradition: String?,
            rarity: String?,
            trait: String?,
        ): Flow<List<SpellListItem>> = flowOf(emptyList())

        override suspend fun getSpellDetail(spellId: String): SpellDetail? = detailsById[spellId]

        override suspend fun seedFromDatasetIfEmpty(datasetJson: String) = Unit
    }

    private fun spellDetail(
        id: String,
        rank: Int,
        tradition: String,
    ): SpellDetail {
        return SpellDetail(
            id = id,
            name = id,
            rank = rank,
            tradition = tradition,
            rarity = "common",
            traits = emptyList(),
            castTime = "2 actions",
            range = "",
            target = "",
            duration = "",
            description = "",
            license = "",
            sourceBook = "",
            sourcePage = null,
        )
    }

    private data class Assignment(
        val characterId: Long,
        val rank: Int,
        val slotIndex: Int,
        val spellId: String,
        val trackKey: String,
    )

    private companion object {
        private const val CHARACTER_ID = 1L
    }
}
