package com.spellapp

import android.content.Context
import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.data.CatalogRecordRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.RulesReferenceRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.data.local.AssetClassSpellcastingCatalogSource
import com.spellapp.core.data.local.AssetRulesReferenceRepository
import com.spellapp.core.data.local.AssetSpellRulesTextRepository
import com.spellapp.core.data.local.CatalogDatabase
import com.spellapp.core.data.local.CatalogFirstSpellRepository
import com.spellapp.core.data.local.CatalogSpellRepository
import com.spellapp.core.data.local.CatalogSpellRulesTextRepository
import com.spellapp.core.data.local.FallbackSpellRulesTextRepository
import com.spellapp.core.data.local.RoomAcceptedSpellSourceRepository
import com.spellapp.core.data.local.RoomCatalogRecordRepository
import com.spellapp.core.data.local.RoomCharacterRepository
import com.spellapp.core.data.local.RoomKnownSpellRepository
import com.spellapp.core.data.local.RoomSpellRepository
import com.spellapp.core.data.local.SpellDatabase
import com.spellapp.core.model.ClassSpellcastingCatalog
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.feature.character.ArchetypeSpellcastingCatalogSource
import com.spellapp.feature.character.AssetArchetypeSpellcastingCatalogSource
import com.spellapp.feature.character.AssetCharacterBuilderCatalogSource
import com.spellapp.feature.character.AssetCharacterClassDefinitionSource
import com.spellapp.feature.character.CatalogCharacterBuilderCatalogSource
import com.spellapp.feature.character.CharacterBuilderCatalogSource
import com.spellapp.feature.character.CharacterClassDefinitionSource
import com.spellapp.feature.character.FallbackCharacterBuilderCatalogSource
import com.spellapp.feature.spells.AssignPreparedSpellUseCase

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val spellDatabase: SpellDatabase by lazy {
        SpellDatabase.create(appContext)
    }

    private val catalogDatabase: CatalogDatabase by lazy {
        CatalogDatabase.create(appContext)
    }

    private val legacySpellRepository: RoomSpellRepository by lazy {
        RoomSpellRepository(spellDatabase.spellDao())
    }

    private val catalogSpellRepository: CatalogSpellRepository by lazy {
        CatalogSpellRepository(catalogDatabase.catalogDao())
    }

    val spellRepository: SpellRepository by lazy {
        CatalogFirstSpellRepository(
            catalogRepository = catalogSpellRepository,
            fallbackRepository = legacySpellRepository,
        )
    }

    val catalogRecordRepository: CatalogRecordRepository by lazy {
        RoomCatalogRecordRepository(catalogDatabase.catalogDao())
    }

    val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource by lazy {
        AssetClassSpellcastingCatalogSource(appContext).also(ClassSpellcastingCatalog::install)
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
            classSpellcastingCatalogSource = classSpellcastingCatalogSource,
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
        FallbackSpellRulesTextRepository(
            primary = CatalogSpellRulesTextRepository(catalogDatabase.catalogDao()),
            fallback = AssetSpellRulesTextRepository(appContext),
        )
    }

    val characterClassDefinitionSource: CharacterClassDefinitionSource by lazy {
        AssetCharacterClassDefinitionSource(
            context = appContext,
            classSpellcastingCatalogSource = classSpellcastingCatalogSource,
        )
    }

    val archetypeSpellcastingCatalogSource: ArchetypeSpellcastingCatalogSource by lazy {
        AssetArchetypeSpellcastingCatalogSource(appContext)
    }

    val characterBuilderCatalogSource: CharacterBuilderCatalogSource by lazy {
        FallbackCharacterBuilderCatalogSource(
            primary = CatalogCharacterBuilderCatalogSource(catalogDatabase.catalogDao()),
            fallback = AssetCharacterBuilderCatalogSource(appContext),
        )
    }

    val characterFeatureFactoryProvider: CharacterFeatureFactoryProvider by lazy {
        AppCharacterFeatureFactoryProvider(
            characterCrudRepositoryProvider = { characterRepository },
            characterBuildRepositoryProvider = { characterRepository },
            acceptedSpellSourceRepositoryProvider = { acceptedSpellSourceRepository },
            spellRepositoryProvider = { spellRepository },
            knownSpellRepositoryProvider = { knownSpellRepository },
            castingTrackRepositoryProvider = { characterRepository },
            preparedSlotSyncRepositoryProvider = { characterRepository },
            classDefinitionSourceProvider = { characterClassDefinitionSource },
            characterBuilderCatalogSourceProvider = { characterBuilderCatalogSource },
            archetypeSpellcastingCatalogSourceProvider = { archetypeSpellcastingCatalogSource },
            classSpellcastingCatalogSourceProvider = { classSpellcastingCatalogSource },
        )
    }

    val spellCatalogFeatureFactoryProvider: SpellCatalogFeatureFactoryProvider by lazy {
        AppSpellCatalogFeatureFactoryProvider(
            spellRepositoryProvider = { spellRepository },
            acceptedSpellSourceRepositoryProvider = { acceptedSpellSourceRepository },
            knownSpellRepositoryProvider = { knownSpellRepository },
            rulesReferenceRepositoryProvider = { rulesReferenceRepository },
            spellRulesTextRepositoryProvider = { spellRulesTextRepository },
            classSpellcastingCatalogSourceProvider = { classSpellcastingCatalogSource },
        )
    }

    val preparedCastingFeatureFactoryProvider: PreparedCastingFeatureFactoryProvider by lazy {
        AppPreparedCastingFeatureFactoryProvider(
            preparedSlotRepositoryProvider = { characterRepository },
            castingTrackRepositoryProvider = { characterRepository },
            preparedSlotSyncRepositoryProvider = { characterRepository },
            sessionEventRepositoryProvider = { characterRepository },
            focusStateRepositoryProvider = { characterRepository },
            knownSpellRepositoryProvider = { knownSpellRepository },
            spellRepositoryProvider = { spellRepository },
            characterCrudRepositoryProvider = { characterRepository },
            characterBuildRepositoryProvider = { characterRepository },
            classSpellcastingCatalogSourceProvider = { classSpellcastingCatalogSource },
        )
    }

    val navigationViewModelFactory: SpellAppNavigationViewModelFactory by lazy {
        SpellAppNavigationViewModelFactory(
            assignPreparedSpellUseCaseProvider = {
                AssignPreparedSpellUseCase(
                    knownSpellRepository = knownSpellRepository,
                    preparedSlotRepository = characterRepository,
                    spellRepository = spellRepository,
                )
            },
        )
    }

    suspend fun seedSpellsIfNeeded() {
        if (catalogSpellRepository.isAvailable()) {
            return
        }
        val datasetJson = appContext.assets
            .open("spells.normalized.json")
            .bufferedReader()
            .use { it.readText() }
        spellRepository.seedFromDatasetIfEmpty(datasetJson)
    }
}
