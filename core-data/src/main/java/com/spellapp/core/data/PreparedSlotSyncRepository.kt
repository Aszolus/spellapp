package com.spellapp.core.data

interface PreparedSlotSyncRepository {
    suspend fun syncPreparedSlotsForCharacter(characterId: Long)
}
