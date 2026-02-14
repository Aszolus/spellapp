package com.spellapp

import android.content.Context
import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.local.RoomCharacterRepository
import com.spellapp.core.data.local.RoomSpellRepository
import com.spellapp.core.data.local.SpellDatabase
import com.spellapp.feature.character.AssetCharacterClassDefinitionSource
import com.spellapp.feature.character.CharacterClassDefinitionSource

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val spellDatabase: SpellDatabase by lazy {
        SpellDatabase.create(appContext)
    }

    val spellRepository: SpellRepository by lazy {
        RoomSpellRepository(spellDatabase.spellDao())
    }

    val characterRepository: CharacterRepository by lazy {
        RoomCharacterRepository(
            database = spellDatabase,
            characterDao = spellDatabase.characterDao(),
            characterBuildIdentityDao = spellDatabase.characterBuildIdentityDao(),
            characterBuildOptionDao = spellDatabase.characterBuildOptionDao(),
            preparedSlotDao = spellDatabase.preparedSlotDao(),
            castingTrackDao = spellDatabase.castingTrackDao(),
            focusStateDao = spellDatabase.focusStateDao(),
            sessionEventDao = spellDatabase.sessionEventDao(),
        )
    }

    val characterClassDefinitionSource: CharacterClassDefinitionSource by lazy {
        AssetCharacterClassDefinitionSource(appContext)
    }

    suspend fun seedSpellsIfNeeded() {
        val datasetJson = appContext.assets
            .open("spells.normalized.json")
            .bufferedReader()
            .use { it.readText() }
        spellRepository.seedFromDatasetIfEmpty(datasetJson)
    }
}
