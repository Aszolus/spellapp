package com.spellapp

import androidx.lifecycle.ViewModelProvider
import com.spellapp.core.data.AcceptedSpellSourceRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.RulesReferenceRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.data.SpellRulesTextRepository
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.feature.character.ArchetypeSpellcastingCatalogSource
import com.spellapp.feature.character.CharacterBuilderCatalogSource
import com.spellapp.feature.character.CharacterBuilderViewModelFactory
import com.spellapp.feature.character.CharacterClassDefinitionSource
import com.spellapp.feature.character.CharacterListViewModelFactory
import com.spellapp.feature.character.spellcasting.DefaultKnownSpellsSeeder
import com.spellapp.feature.character.spellcasting.RefreshSpellcastingProjectionUseCase
import com.spellapp.feature.character.spellcasting.prepared.PreparedSlotsViewModelFactory
import com.spellapp.feature.spells.DefaultKnownSpellWarningPolicy
import com.spellapp.feature.spells.SpellDetailViewModelFactory
import com.spellapp.feature.spells.SpellListViewModelFactory
import com.spellapp.feature.spells.ToggleKnownSpellUseCase

interface CharacterFeatureFactoryProvider {
    fun characterListFactory(): ViewModelProvider.Factory
    fun characterBuilderFactory(characterId: Long): ViewModelProvider.Factory
}

interface SpellCatalogFeatureFactoryProvider {
    fun spellListFactory(): ViewModelProvider.Factory
    fun spellDetailFactory(
        spellId: String,
        heightenedAt: Int?,
    ): ViewModelProvider.Factory
}

interface PreparedCastingFeatureFactoryProvider {
    fun preparedSlotsFactory(characterId: Long): ViewModelProvider.Factory
}

class AppCharacterFeatureFactoryProvider(
    private val characterCrudRepositoryProvider: () -> CharacterCrudRepository,
    private val characterBuildRepositoryProvider: () -> CharacterBuildRepository,
    private val acceptedSpellSourceRepositoryProvider: () -> AcceptedSpellSourceRepository,
    private val spellRepositoryProvider: () -> SpellRepository,
    private val knownSpellRepositoryProvider: () -> KnownSpellRepository,
    private val castingTrackRepositoryProvider: () -> com.spellapp.core.data.CastingTrackRepository,
    private val preparedSlotSyncRepositoryProvider: () -> PreparedSlotSyncRepository,
    private val classDefinitionSourceProvider: () -> CharacterClassDefinitionSource,
    private val characterBuilderCatalogSourceProvider: () -> CharacterBuilderCatalogSource,
    private val archetypeSpellcastingCatalogSourceProvider: () -> ArchetypeSpellcastingCatalogSource,
    private val classSpellcastingCatalogSourceProvider: () -> ClassSpellcastingCatalogSource,
) : CharacterFeatureFactoryProvider {
    override fun characterListFactory(): ViewModelProvider.Factory {
        return CharacterListViewModelFactory(
            characterCrudRepositoryProvider = characterCrudRepositoryProvider,
        )
    }

    override fun characterBuilderFactory(characterId: Long): ViewModelProvider.Factory {
        val spellRepository = spellRepositoryProvider()
        val knownSpellRepository = knownSpellRepositoryProvider()
        val archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSourceProvider()
        val classSpellcastingCatalogSource = classSpellcastingCatalogSourceProvider()
        return CharacterBuilderViewModelFactory(
            characterId = characterId,
            characterCrudRepository = characterCrudRepositoryProvider(),
            characterBuildRepository = characterBuildRepositoryProvider(),
            acceptedSpellSourceRepository = acceptedSpellSourceRepositoryProvider(),
            spellRepository = spellRepository,
            refreshSpellcastingProjectionUseCase = RefreshSpellcastingProjectionUseCase(
                castingTrackRepository = castingTrackRepositoryProvider(),
                preparedSlotSyncRepository = preparedSlotSyncRepositoryProvider(),
                knownSpellsSeeder = DefaultKnownSpellsSeeder(
                    spellRepository = spellRepository,
                    knownSpellRepository = knownSpellRepository,
                    classSpellcastingCatalogSource = classSpellcastingCatalogSource,
                ),
                archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
            ),
            classDefinitionSource = classDefinitionSourceProvider(),
            characterBuilderCatalogSource = characterBuilderCatalogSourceProvider(),
            archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
            classSpellcastingCatalogSource = classSpellcastingCatalogSource,
        )
    }
}

class AppSpellCatalogFeatureFactoryProvider(
    private val spellRepositoryProvider: () -> SpellRepository,
    private val acceptedSpellSourceRepositoryProvider: () -> AcceptedSpellSourceRepository,
    private val knownSpellRepositoryProvider: () -> KnownSpellRepository,
    private val rulesReferenceRepositoryProvider: () -> RulesReferenceRepository,
    private val spellRulesTextRepositoryProvider: () -> SpellRulesTextRepository,
    private val classSpellcastingCatalogSourceProvider: () -> ClassSpellcastingCatalogSource,
) : SpellCatalogFeatureFactoryProvider {
    override fun spellListFactory(): ViewModelProvider.Factory {
        val spellRepository = spellRepositoryProvider()
        val knownSpellRepository = knownSpellRepositoryProvider()
        val classSpellcastingCatalogSource = classSpellcastingCatalogSourceProvider()
        return SpellListViewModelFactory(
            spellRepository = spellRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepositoryProvider(),
            knownSpellRepository = knownSpellRepository,
            toggleKnownSpellUseCase = ToggleKnownSpellUseCase(
                knownSpellRepository = knownSpellRepository,
                spellRepository = spellRepository,
                warningPolicy = DefaultKnownSpellWarningPolicy(classSpellcastingCatalogSource),
            ),
            classSpellcastingCatalogSource = classSpellcastingCatalogSource,
        )
    }

    override fun spellDetailFactory(
        spellId: String,
        heightenedAt: Int?,
    ): ViewModelProvider.Factory {
        return SpellDetailViewModelFactory(
            spellId = spellId,
            spellRepository = spellRepositoryProvider(),
            rulesReferenceRepository = rulesReferenceRepositoryProvider(),
            spellRulesTextRepository = spellRulesTextRepositoryProvider(),
            initialHeightenedAt = heightenedAt,
        )
    }
}

class AppPreparedCastingFeatureFactoryProvider(
    private val preparedSlotRepositoryProvider: () -> PreparedSlotRepository,
    private val castingTrackRepositoryProvider: () -> com.spellapp.core.data.CastingTrackRepository,
    private val preparedSlotSyncRepositoryProvider: () -> PreparedSlotSyncRepository,
    private val sessionEventRepositoryProvider: () -> SessionEventRepository,
    private val focusStateRepositoryProvider: () -> FocusStateRepository,
    private val knownSpellRepositoryProvider: () -> KnownSpellRepository,
    private val spellRepositoryProvider: () -> SpellRepository,
    private val characterCrudRepositoryProvider: () -> CharacterCrudRepository,
    private val characterBuildRepositoryProvider: () -> CharacterBuildRepository,
    private val classSpellcastingCatalogSourceProvider: () -> ClassSpellcastingCatalogSource,
) : PreparedCastingFeatureFactoryProvider {
    override fun preparedSlotsFactory(characterId: Long): ViewModelProvider.Factory {
        return PreparedSlotsViewModelFactory(
            characterId = characterId,
            preparedSlotRepository = preparedSlotRepositoryProvider(),
            castingTrackRepository = castingTrackRepositoryProvider(),
            preparedSlotSyncRepository = preparedSlotSyncRepositoryProvider(),
            sessionEventRepository = sessionEventRepositoryProvider(),
            focusStateRepository = focusStateRepositoryProvider(),
            knownSpellRepository = knownSpellRepositoryProvider(),
            spellRepository = spellRepositoryProvider(),
            characterCrudRepository = characterCrudRepositoryProvider(),
            characterBuildRepository = characterBuildRepositoryProvider(),
            classSpellcastingCatalogSource = classSpellcastingCatalogSourceProvider(),
        )
    }
}
