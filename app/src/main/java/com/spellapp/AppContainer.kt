package com.spellapp

import android.content.Context
import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.RulesReferenceRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.data.local.AssetRulesReferenceRepository
import com.spellapp.core.data.local.AssetSpellRulesTextRepository
import com.spellapp.core.data.local.RoomAcceptedSpellSourceRepository
import com.spellapp.core.data.local.RoomCharacterRepository
import com.spellapp.core.data.local.RoomKnownSpellRepository
import com.spellapp.core.data.local.RoomSpellRepository
import com.spellapp.core.data.local.SpellDatabase
import com.spellapp.feature.character.ArchetypeSpellcastingCatalogSource
import com.spellapp.feature.character.AssetArchetypeSpellcastingCatalogSource
import com.spellapp.feature.character.AssetCharacterClassDefinitionSource
import com.spellapp.feature.character.CharacterClassDefinitionSource
import com.spellapp.feature.spells.AssignPreparedSpellUseCase

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

    val knownSpellRepository: KnownSpellRepository by lazy {
        RoomKnownSpellRepository(spellDatabase.knownSpellDao())
    }

    val acceptedSpellSourceRepository: AcceptedSpellSourceRepository by lazy {
        RoomAcceptedSpellSourceRepository(spellDatabase.acceptedSpellSourceDao())
    }

    val rulesReferenceRepository: RulesReferenceRepository by lazy {
        AssetRulesReferenceRepository(appContext)
    }

    val spellRulesTextRepository: SpellRulesTextRepository by lazy {
        AssetSpellRulesTextRepository(appContext)
    }

    val characterClassDefinitionSource: CharacterClassDefinitionSource by lazy {
        AssetCharacterClassDefinitionSource(appContext)
    }

    val archetypeSpellcastingCatalogSource: ArchetypeSpellcastingCatalogSource by lazy {
        AssetArchetypeSpellcastingCatalogSource(appContext)
    }

    val characterFeatureFactoryProvider: CharacterFeatureFactoryProvider by lazy {
        AppCharacterFeatureFactoryProvider(
            characterCrudRepository = characterRepository,
            characterBuildRepository = characterRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            spellRepository = spellRepository,
            knownSpellRepository = knownSpellRepository,
            castingTrackRepository = characterRepository,
            preparedSlotSyncRepository = characterRepository,
            classDefinitionSource = characterClassDefinitionSource,
            archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
        )
    }

    val spellCatalogFeatureFactoryProvider: SpellCatalogFeatureFactoryProvider by lazy {
        AppSpellCatalogFeatureFactoryProvider(
            spellRepository = spellRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            knownSpellRepository = knownSpellRepository,
            rulesReferenceRepository = rulesReferenceRepository,
            spellRulesTextRepository = spellRulesTextRepository,
        )
    }

    val preparedCastingFeatureFactoryProvider: PreparedCastingFeatureFactoryProvider by lazy {
        AppPreparedCastingFeatureFactoryProvider(
            preparedSlotRepository = characterRepository,
            castingTrackRepository = characterRepository,
            preparedSlotSyncRepository = characterRepository,
            sessionEventRepository = characterRepository,
            focusStateRepository = characterRepository,
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
            characterCrudRepository = characterRepository,
            characterBuildRepository = characterRepository,
        )
    }

    val navigationViewModelFactory: SpellAppNavigationViewModelFactory by lazy {
        SpellAppNavigationViewModelFactory(
            assignPreparedSpellUseCase = AssignPreparedSpellUseCase(
                knownSpellRepository = knownSpellRepository,
                preparedSlotRepository = characterRepository,
                spellRepository = spellRepository,
            ),
        )
    }

    suspend fun seedSpellsIfNeeded() {
        val datasetJson = appContext.assets
            .open("spells.normalized.json")
            .bufferedReader()
            .use { it.readText() }
        spellRepository.seedFromDatasetIfEmpty(datasetJson)
    }
}
