package com.spellapp

import android.content.Context
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.local.RoomSpellRepository
import com.spellapp.core.data.local.SpellDatabase

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

    suspend fun seedSpellsIfNeeded() {
        val datasetJson = appContext.assets
            .open("spells.normalized.json")
            .bufferedReader()
            .use { it.readText() }
        spellRepository.seedFromDatasetIfEmpty(datasetJson)
    }
}
