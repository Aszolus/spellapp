package com.spellapp.core.data

import com.spellapp.core.model.SessionEvent
import kotlinx.coroutines.flow.Flow

interface SessionEventRepository {
    fun observeSessionEvents(
        characterId: Long,
        trackKey: String? = null,
    ): Flow<List<SessionEvent>>
    suspend fun appendSessionEvent(event: SessionEvent): Long
}
