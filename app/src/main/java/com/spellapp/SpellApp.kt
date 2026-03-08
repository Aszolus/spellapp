package com.spellapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.ui.SpellAppTheme
import com.spellapp.core.ui.SpellAppThemeMode
import com.spellapp.feature.character.ArchetypeSpellcastingCatalogSource
import com.spellapp.feature.character.CharacterClassDefinitionSource
import com.spellapp.feature.character.CharacterListViewModel
import com.spellapp.feature.character.CharacterListViewModelFactory
import com.spellapp.feature.spells.AssignPreparedSpellUseCase
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
    themeMode: SpellAppThemeMode = SpellAppThemeMode.DARK,
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
                acceptedSpellSourceRepository = characterRepository,
                knownSpellRepository = characterRepository,
                spellRepository = spellRepository,
                classDefinitionSource = classDefinitionSource,
                archetypeSpellcastingCatalogSource = archetypeSpellcastingCatalogSource,
            )
        },
    )
    val spellListViewModel: SpellListViewModel = viewModel(
        key = "spell-list",
        factory = remember {
            SpellListViewModelFactory(
                spellRepository = spellRepository,
                acceptedSpellSourceRepository = characterRepository,
                knownSpellRepository = characterRepository,
            )
        },
    )
    val navigationViewModel: SpellAppNavigationViewModel = viewModel(
        key = "app-navigation",
        factory = remember {
            SpellAppNavigationViewModelFactory(
                assignPreparedSpellUseCase = AssignPreparedSpellUseCase(
                    characterCrudRepository = characterRepository,
                    knownSpellRepository = characterRepository,
                    preparedSlotRepository = characterRepository,
                    spellRepository = spellRepository,
                ),
            )
        },
    )

    SpellAppTheme(themeMode = themeMode) {
        SpellAppNavGraph(
            navController = navController,
            spellRepository = spellRepository,
            preparedSlotRepository = characterRepository,
            castingTrackRepository = characterRepository,
            preparedSlotSyncRepository = characterRepository,
            sessionEventRepository = characterRepository,
            focusStateRepository = characterRepository,
            knownSpellRepository = characterRepository,
            characterCrudRepository = characterRepository,
            characterBuildRepository = characterRepository,
            characterListViewModel = characterListViewModel,
            spellListViewModel = spellListViewModel,
            navigationViewModel = navigationViewModel,
            seedUiState = seedUiState,
            onRetrySeed = onRetrySeed,
        )
    }
}
