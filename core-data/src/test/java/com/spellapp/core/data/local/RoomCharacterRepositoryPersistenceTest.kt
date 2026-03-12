package com.spellapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.PreparedSlot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomCharacterRepositoryPersistenceTest {
    private lateinit var database: SpellDatabase
    private lateinit var characterRepository: RoomCharacterRepository
    private lateinit var knownSpellRepository: RoomKnownSpellRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            SpellDatabase::class.java,
        ).allowMainThreadQueries().build()
        characterRepository = RoomCharacterRepository(
            database = database,
            characterDao = database.characterDao(),
            characterBuildIdentityDao = database.characterBuildIdentityDao(),
            characterBuildOptionDao = database.characterBuildOptionDao(),
            preparedSlotDao = database.preparedSlotDao(),
            castingTrackDao = database.castingTrackDao(),
            focusStateDao = database.focusStateDao(),
            sessionEventDao = database.sessionEventDao(),
        )
        knownSpellRepository = RoomKnownSpellRepository(database.knownSpellDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertExistingCharacter_preservesKnownSpells() = runBlocking {
        val characterId = characterRepository.upsertCharacter(sampleCharacter())
        knownSpellRepository.addKnownSpell(
            characterId = characterId,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            spellId = "magic-missile",
        )

        characterRepository.upsertCharacter(
            sampleCharacter(
                id = characterId,
                name = "Updated Wizard",
            ),
        )

        val knownSpellIds = knownSpellRepository.observeKnownSpellIds(
            characterId = characterId,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
        ).first()

        assertEquals(setOf("magic-missile"), knownSpellIds)
        assertTrue(
            characterRepository.observePreparedSlots(
                characterId = characterId,
                trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            ).first().isNotEmpty(),
        )
    }

    private fun sampleCharacter(
        id: Long = 0L,
        name: String = "Test Wizard",
    ): CharacterProfile {
        return CharacterProfile(
            id = id,
            name = name,
            level = 5,
            characterClass = CharacterClass.WIZARD,
            keyAbility = AbilityScore.INTELLIGENCE,
            spellDc = 22,
            spellAttackModifier = 12,
            legacyTerminologyEnabled = false,
        )
    }
}
