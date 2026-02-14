package com.spellapp.core.data

import com.spellapp.core.model.CharacterBuildIdentity
import com.spellapp.core.model.CharacterBuildOption
import com.spellapp.core.model.CharacterBuildOptionType
import kotlinx.coroutines.flow.Flow

interface CharacterBuildRepository {
    fun observeBuildIdentity(characterId: Long): Flow<CharacterBuildIdentity?>
    suspend fun getBuildIdentity(characterId: Long): CharacterBuildIdentity?
    suspend fun upsertBuildIdentity(identity: CharacterBuildIdentity)

    fun observeBuildOptions(characterId: Long): Flow<List<CharacterBuildOption>>
    suspend fun getBuildOptions(characterId: Long): List<CharacterBuildOption>
    suspend fun upsertBuildOption(option: CharacterBuildOption): Long
    suspend fun deleteBuildOption(
        characterId: Long,
        optionType: CharacterBuildOptionType,
        optionId: String,
    ): Boolean
    suspend fun replaceBuildOptions(
        characterId: Long,
        options: List<CharacterBuildOption>,
    )
}
