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
import com.spellapp.feature.character.ArchetypeSpellcastingCatalogSource
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
    private val characterCrudRepository: CharacterCrudRepository,
    private val characterBuildRepository: CharacterBuildRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    private val spellRepository: SpellRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val castingTrackRepository: com.spellapp.core.data.CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val classDefinitionSource: CharacterClassDefinitionSource,
    private val archetypeSpellcastingCatalogSource: ArchetypeSpellcastingCatalogSource,
) : CharacterFeatureFactoryProvider {
    override fun characterListFactory(): ViewModelProvider.Factory {
        return CharacterListViewModelFactory(
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            spellRepository = spellRepository,
            refreshSpellcastingProjectionUseCase = RefreshSpellcastingProjectionUseCase(
                castingTrackRepository = castingTrackRepository,
                preparedSlotSyncRepository = preparedSlotSyncRepository,
                knownSpellsSeeder = DefaultKnownSpellsSeeder(
                    spellRepository = spellRepository,
                    knownSpellRepository = knownSpellRepository,
                ),
                archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
            ),
            classDefinitionSource = classDefinitionSource,
            archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
        )
    }
}

class AppSpellCatalogFeatureFactoryProvider(
    private val spellRepository: SpellRepository,
    private val acceptedSpellSourceRepository: AcceptedSpellSourceRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val rulesReferenceRepository: RulesReferenceRepository,
    private val spellRulesTextRepository: SpellRulesTextRepository,
) : SpellCatalogFeatureFactoryProvider {
    override fun spellListFactory(): ViewModelProvider.Factory {
        return SpellListViewModelFactory(
            spellRepository = spellRepository,
            acceptedSpellSourceRepository = acceptedSpellSourceRepository,
            knownSpellRepository = knownSpellRepository,
            toggleKnownSpellUseCase = ToggleKnownSpellUseCase(
                knownSpellRepository = knownSpellRepository,
                spellRepository = spellRepository,
                warningPolicy = DefaultKnownSpellWarningPolicy(),
            ),
        )
    }

    override fun spellDetailFactory(
        spellId: String,
        heightenedAt: Int?,
    ): ViewModelProvider.Factory {
        return SpellDetailViewModelFactory(
            spellId = spellId,
            spellRepository = spellRepository,
            rulesReferenceRepository = rulesReferenceRepository,
            spellRulesTextRepository = spellRulesTextRepository,
            initialHeightenedAt = heightenedAt,
        )
    }
}

class AppPreparedCastingFeatureFactoryProvider(
    private val preparedSlotRepository: PreparedSlotRepository,
    private val castingTrackRepository: com.spellapp.core.data.CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val focusStateRepository: FocusStateRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val spellRepository: SpellRepository,
    private val characterCrudRepository: CharacterCrudRepository,
    private val characterBuildRepository: CharacterBuildRepository,
) : PreparedCastingFeatureFactoryProvider {
    override fun preparedSlotsFactory(characterId: Long): ViewModelProvider.Factory {
        return PreparedSlotsViewModelFactory(
            characterId = characterId,
            preparedSlotRepository = preparedSlotRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
            sessionEventRepository = sessionEventRepository,
            focusStateRepository = focusStateRepository,
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
        )
    }
}
