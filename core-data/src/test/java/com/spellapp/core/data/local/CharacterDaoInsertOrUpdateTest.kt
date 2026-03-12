package com.spellapp.core.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterDaoInsertOrUpdateTest {
    @Test
    fun insertOrUpdate_insertsNewCharacter() = runBlocking {
        val dao = RecordingCharacterDao(insertResult = 41L)

        val result = dao.insertOrUpdate(
            CharacterEntity(
                id = 0L,
                name = "New",
                level = 1,
                characterClass = "WIZARD",
                keyAbility = "INTELLIGENCE",
                spellDc = 15,
                spellAttackModifier = 5,
                legacyTerminologyEnabled = false,
            ),
        )

        assertEquals(41L, result)
        assertEquals(1, dao.insertCalls.size)
        assertTrue(dao.updateCalls.isEmpty())
    }

    @Test
    fun insertOrUpdate_updatesExistingCharacterWhenPresent() = runBlocking {
        val dao = RecordingCharacterDao(updateResult = 1)
        val character = CharacterEntity(
            id = 7L,
            name = "Existing",
            level = 5,
            characterClass = "CLERIC",
            keyAbility = "WISDOM",
            spellDc = 21,
            spellAttackModifier = 11,
            legacyTerminologyEnabled = false,
        )

        val result = dao.insertOrUpdate(character)

        assertEquals(7L, result)
        assertEquals(listOf(character), dao.updateCalls)
        assertTrue(dao.insertCalls.isEmpty())
    }

    @Test
    fun insertOrUpdate_fallsBackToInsertWhenUpdateMisses() = runBlocking {
        val dao = RecordingCharacterDao(updateResult = 0, insertResult = 88L)
        val character = CharacterEntity(
            id = 12L,
            name = "Missing",
            level = 3,
            characterClass = "DRUID",
            keyAbility = "WISDOM",
            spellDc = 18,
            spellAttackModifier = 8,
            legacyTerminologyEnabled = false,
        )

        val result = dao.insertOrUpdate(character)

        assertEquals(88L, result)
        assertEquals(listOf(character), dao.updateCalls)
        assertEquals(listOf(character), dao.insertCalls)
    }
}

private class RecordingCharacterDao(
    private val updateResult: Int = 0,
    private val insertResult: Long = 1L,
) : CharacterDao {
    val insertCalls = mutableListOf<CharacterEntity>()
    val updateCalls = mutableListOf<CharacterEntity>()
    val deletedIds = mutableListOf<Long>()

    override fun observeCharacters(): Flow<List<CharacterEntity>> = emptyFlow()

    override suspend fun getCharacterById(characterId: Long): CharacterEntity? = null

    override suspend fun insert(character: CharacterEntity): Long {
        insertCalls += character
        return insertResult
    }

    override suspend fun update(character: CharacterEntity): Int {
        updateCalls += character
        return updateResult
    }

    override suspend fun deleteById(characterId: Long) {
        deletedIds += characterId
    }
}
