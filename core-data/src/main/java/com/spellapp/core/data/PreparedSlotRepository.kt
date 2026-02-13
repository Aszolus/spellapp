package com.spellapp.core.data

import com.spellapp.core.model.PreparedSlot
import kotlinx.coroutines.flow.Flow

interface PreparedSlotRepository {
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
    suspend fun clearPreparedSlotsForTrack(
        characterId: Long,
        trackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    ): Int
    suspend fun undoLastCast(
        characterId: Long,
        trackKey: String? = null,
    ): Boolean
}
