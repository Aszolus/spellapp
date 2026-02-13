package com.spellapp.core.data

import com.spellapp.core.model.FocusState
import kotlinx.coroutines.flow.Flow

interface FocusStateRepository {
    fun observeFocusState(characterId: Long): Flow<FocusState?>
    suspend fun upsertFocusState(state: FocusState)
}
