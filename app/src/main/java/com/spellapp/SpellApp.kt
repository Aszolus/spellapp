package com.spellapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.ui.SpellAppTheme
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
    seedUiState: SeedUiState,
    onRetrySeed: () -> Unit,
) {
    val navController = rememberNavController()
    val characterListViewModel: CharacterListViewModel = viewModel(
        key = "character-list",
        factory = remember {
            CharacterListViewModelFactory(
                characterRepository = characterRepository,
                classDefinitionSource = classDefinitionSource,
            )
        },
    )
    val characterListUiState by characterListViewModel.uiState.collectAsState()
    val spellListViewModel: SpellListViewModel = viewModel(
        key = "spell-list",
        factory = remember {
            SpellListViewModelFactory(spellRepository = spellRepository)
        },
    )
    val spellListUiState by spellListViewModel.uiState.collectAsState()
    val spells by spellListViewModel.spells.collectAsState()
    val navigationViewModel: SpellAppNavigationViewModel = viewModel(
        key = "app-navigation",
    )
    val navigationUiState by navigationViewModel.uiState.collectAsState()

    SpellAppTheme {
        SpellAppNavGraph(
            navController = navController,
            spellRepository = spellRepository,
            characterRepository = characterRepository,
            seedUiState = seedUiState,
            onRetrySeed = onRetrySeed,
            characterListUiState = characterListUiState,
            characterListViewModel = characterListViewModel,
            spellListUiState = spellListUiState,
            spellListViewModel = spellListViewModel,
            spells = spells,
            navigationUiState = navigationUiState,
            onOpenPreparedSlots = navigationViewModel::openPreparedSlots,
            onOpenSpellList = navigationViewModel::openSpellList,
            onStartPreparedSlotAssignment = navigationViewModel::startPreparedSlotAssignment,
            onClearPreparedSlotTarget = navigationViewModel::clearPreparedSlotTarget,
        )
    }
}
