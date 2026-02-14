package com.spellapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.ui.SpellAppTheme
import com.spellapp.feature.character.ArchetypeSpellcastingCatalogSource
import com.spellapp.feature.character.CharacterClassDefinitionSource
import com.spellapp.feature.character.CharacterListViewModel
import com.spellapp.feature.character.CharacterListViewModelFactory
import com.spellapp.feature.spells.SpellListViewModel
import com.spellapp.feature.spells.SpellListViewModelFactory

@Composable
fun SpellApp(
    spellRepository: SpellRepository,
    characterRepository: CharacterRepository,
    classDefinitionSource: CharacterClassDefinitionSource,
    archetypeSpellcastingCatalogSource: ArchetypeSpellcastingCatalogSource,
    seedUiState: SeedUiState,
    onRetrySeed: () -> Unit,
) {
    val navController = rememberNavController()
    val characterListViewModel: CharacterListViewModel = viewModel(
        key = "character-list",
        factory = remember {
            CharacterListViewModelFactory(
                characterCrudRepository = characterRepository,
                characterBuildRepository = characterRepository,
                castingTrackRepository = characterRepository,
                preparedSlotSyncRepository = characterRepository,
                classDefinitionSource = classDefinitionSource,
                archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
            )
        },
    )
    val spellListViewModel: SpellListViewModel = viewModel(
        key = "spell-list",
        factory = remember {
            SpellListViewModelFactory(spellRepository = spellRepository)
        },
    )
    val navigationViewModel: SpellAppNavigationViewModel = viewModel(
        key = "app-navigation",
        factory = remember {
            SpellAppNavigationViewModelFactory(
                preparedSlotRepository = characterRepository,
            )
        },
    )

    SpellAppTheme {
        SpellAppNavGraph(
            navController = navController,
            spellRepository = spellRepository,
            preparedSlotRepository = characterRepository,
            castingTrackRepository = characterRepository,
            preparedSlotSyncRepository = characterRepository,
            sessionEventRepository = characterRepository,
            focusStateRepository = characterRepository,
            characterListViewModel = characterListViewModel,
            spellListViewModel = spellListViewModel,
            navigationViewModel = navigationViewModel,
            seedUiState = seedUiState,
            onRetrySeed = onRetrySeed,
        )
    }
}
