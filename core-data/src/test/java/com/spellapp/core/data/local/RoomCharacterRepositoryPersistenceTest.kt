package com.spellapp.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CastingStyle
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.ClassSpellcastingDefinition
import com.spellapp.core.model.InMemoryClassSpellcastingCatalogSource
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.PrimaryTrackDefinition
import com.spellapp.core.model.SpellcastingTradition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            classSpellcastingCatalogSource = testSpellcastingCatalog(),
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

    @Test
    fun setSignatureSpell_updatesKnownSpellSignatureFlag() = runBlocking {
        val characterId = characterRepository.upsertCharacter(sampleCharacter())
        knownSpellRepository.addKnownSpell(
            characterId = characterId,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            spellId = "heal",
            knownRank = 1,
        )
        knownSpellRepository.addKnownSpell(
            characterId = characterId,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
            spellId = "heal",
            knownRank = 2,
        )

        assertTrue(
            knownSpellRepository.setSignatureSpell(
                characterId = characterId,
                trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                spellId = "heal",
                isSignature = true,
            ),
        )

        val allRanksMarked = knownSpellRepository.observeKnownSpells(
            characterId = characterId,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
        ).first()
        assertTrue(allRanksMarked.all { knownSpell -> knownSpell.isSignature })

        assertTrue(
            knownSpellRepository.setSignatureSpell(
                characterId = characterId,
                trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                spellId = "heal",
                isSignature = false,
                knownRank = 1,
            ),
        )

        val signaturesByRank = knownSpellRepository.observeKnownSpells(
            characterId = characterId,
            trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
        ).first().associateBy { knownSpell -> knownSpell.knownRank }

        assertFalse(signaturesByRank[1]?.isSignature ?: true)
        assertTrue(signaturesByRank[2]?.isSignature ?: false)
    }

    private fun sampleCharacter(
        id: Long = 0L,
        name: String = "Test Wizard",
    ): CharacterProfile {
        return CharacterProfile(
            id = id,
            name = name,
            level = 5,
            classId = "wizard",
            keyAbility = AbilityScore.INTELLIGENCE,
            spellDc = 22,
            spellAttackModifier = 12,
            legacyTerminologyEnabled = false,
        )
    }

    private fun testSpellcastingCatalog() = InMemoryClassSpellcastingCatalogSource(
        listOf(
            ClassSpellcastingDefinition(
                classId = "wizard",
                label = "Wizard",
                defaultKeyAbility = AbilityScore.INTELLIGENCE,
                keyAbilityOptions = listOf(AbilityScore.INTELLIGENCE),
                baseTradition = SpellcastingTradition.ARCANE,
                primaryTracks = listOf(
                    PrimaryTrackDefinition(
                        trackKey = PreparedSlot.PRIMARY_TRACK_KEY,
                        displayName = "Wizard Spellcasting",
                        progressionType = CastingProgressionType.FULL_PREPARED,
                        castingStyle = CastingStyle.PREPARED,
                        tradition = SpellcastingTradition.ARCANE,
                        slotProgressionKey = "full-prepared",
                        slotsByLevel = mapOf(
                            5 to mapOf(0 to 5, 1 to 3, 2 to 3, 3 to 2),
                        ),
                    ),
                ),
            ),
        ),
    )
}
