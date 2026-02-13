package com.spellapp.core.data

import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.FocusState
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEvent
import kotlinx.coroutines.flow.Flow

interface CharacterRepository {
    fun observeCharacters(): Flow<List<CharacterProfile>>
    suspend fun getCharacter(characterId: Long): CharacterProfile?
    suspend fun upsertCharacter(character: CharacterProfile): Long
    suspend fun deleteCharacter(characterId: Long)

    fun observePreparedSlots(
        characterId: Long,
        trackKey: String? = null,
    ): Flow<List<PreparedSlot>>
    suspend fun addPreparedSlot(
        characterId: Long,
        rank: Int,
        trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    ): Long
    suspend fun removePreparedSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    ): Boolean
    suspend fun clearPreparedSlotSpell(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    ): Boolean
    suspend fun assignSpellToPreparedSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        spellId: String,
        trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    )
    suspend fun castPreparedSlot(
        characterId: Long,
        rank: Int,
        slotIndex: Int,
        trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    ): Boolean
    suspend fun undoLastCast(characterId: Long): Boolean

    fun observeFocusState(characterId: Long): Flow<FocusState?>
    suspend fun upsertFocusState(state: FocusState)

    fun observeSessionEvents(characterId: Long): Flow<List<SessionEvent>>
    suspend fun appendSessionEvent(event: SessionEvent): Long
}
