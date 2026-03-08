package com.spellapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CharacterProfile
import com.spellapp.feature.character.CharacterEditorDialog
import com.spellapp.feature.character.CharacterListRoute
import com.spellapp.feature.character.CharacterListViewModel
import com.spellapp.feature.character.PreparedSlotsRoute
import com.spellapp.feature.character.PreparedSlotsViewModel
import com.spellapp.feature.character.PreparedSlotsViewModelFactory
import com.spellapp.feature.spells.SpellDetailRoute
import com.spellapp.feature.spells.SpellDetailViewModel
import com.spellapp.feature.spells.SpellDetailViewModelFactory
import com.spellapp.feature.spells.SpellBrowserMode
import com.spellapp.feature.spells.SpellListRoute
import com.spellapp.feature.spells.SpellListViewModel
import kotlinx.coroutines.flow.collect

@Composable
fun SpellAppNavGraph(
    navController: NavHostController,
    spellRepository: SpellRepository,
    preparedSlotRepository: PreparedSlotRepository,
    castingTrackRepository: CastingTrackRepository,
    preparedSlotSyncRepository: PreparedSlotSyncRepository,
    sessionEventRepository: SessionEventRepository,
    focusStateRepository: FocusStateRepository,
    knownSpellRepository: KnownSpellRepository,
    characterCrudRepository: CharacterCrudRepository,
    characterBuildRepository: CharacterBuildRepository,
    characterListViewModel: CharacterListViewModel,
    spellListViewModel: SpellListViewModel,
    navigationViewModel: SpellAppNavigationViewModel,
    seedUiState: SeedUiState,
    onRetrySeed: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.CharacterList.route,
    ) {
        characterListDestination(
            navController = navController,
            characterListViewModel = characterListViewModel,
            navigationViewModel = navigationViewModel,
        )
        preparedSlotsDestination(
            navController = navController,
            preparedSlotRepository = preparedSlotRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
            sessionEventRepository = sessionEventRepository,
            focusStateRepository = focusStateRepository,
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
            navigationViewModel = navigationViewModel,
        )
        spellListDestination(
            navController = navController,
            spellListViewModel = spellListViewModel,
            characterListViewModel = characterListViewModel,
            navigationViewModel = navigationViewModel,
            seedUiState = seedUiState,
            onRetrySeed = onRetrySeed,
        )
        spellDetailDestination(
            navController = navController,
            spellRepository = spellRepository,
        )
    }
}

private fun NavGraphBuilder.characterListDestination(
    navController: NavHostController,
    characterListViewModel: CharacterListViewModel,
    navigationViewModel: SpellAppNavigationViewModel,
) {
    composable(route = AppDestinations.CharacterList.route) {
        val characterListUiState by characterListViewModel.uiState.collectAsState()
        CharacterListRoute(
            characters = characterListUiState.characters,
            classDefinitionsByClass = characterListUiState.classDefinitionsByClass,
            onAddCharacter = characterListViewModel::onAddCharacterRequest,
            onEditCharacter = characterListViewModel::onEditCharacterRequest,
            onDeleteCharacter = { character -> characterListViewModel.deleteCharacter(character.id) },
            onOpenPreparedSlots = { character ->
                navigationViewModel.openPreparedSlots(character.id)
                navController.navigate(AppDestinations.PreparedSlots.routeFor(character.id))
            },
            onOpenSpells = { character ->
                navigationViewModel.openSpellList(character.id)
                navController.navigate(AppDestinations.SpellList.route)
            },
        )
        if (characterListUiState.isEditorVisible) {
            CharacterEditorDialog(
                initialCharacter = characterListUiState.editingCharacter,
                initialSelectedBuildOptionIds = characterListUiState.editingSelectedBuildOptionIds,
                initialAcceptedSourceBooks = characterListUiState.editingAcceptedSourceBooks,
                availableSpellSources = characterListUiState.availableSpellSources,
                availableClasses = characterListUiState.availableClasses,
                classDefinitionsByClass = characterListUiState.classDefinitionsByClass,
                archetypeSpellcastingPackages = characterListUiState.archetypeSpellcastingPackages,
                onDismiss = characterListViewModel::dismissEditor,
                onSave = { character, selectedBuildOptionIds, acceptedSourceBooks ->
                    characterListViewModel.saveCharacter(
                        character = character,
                        selectedBuildOptionIds = selectedBuildOptionIds,
                        acceptedSourceBooks = acceptedSourceBooks,
                    )
                },
            )
        }
    }
}

private fun NavGraphBuilder.preparedSlotsDestination(
    navController: NavHostController,
    preparedSlotRepository: PreparedSlotRepository,
    castingTrackRepository: CastingTrackRepository,
    preparedSlotSyncRepository: PreparedSlotSyncRepository,
    sessionEventRepository: SessionEventRepository,
    focusStateRepository: FocusStateRepository,
    knownSpellRepository: KnownSpellRepository,
    spellRepository: SpellRepository,
    characterCrudRepository: CharacterCrudRepository,
    characterBuildRepository: CharacterBuildRepository,
    navigationViewModel: SpellAppNavigationViewModel,
) {
    composable(
        route = AppDestinations.PreparedSlots.route,
        arguments = listOf(navArgument(AppDestinations.PreparedSlots.argCharacterId) {
            type = NavType.LongType
        }),
    ) { backStackEntry ->
        val characterId = backStackEntry.arguments
            ?.getLong(AppDestinations.PreparedSlots.argCharacterId)
            ?: 0L
        val preparedSlotsViewModel: PreparedSlotsViewModel = viewModel(
            key = "prepared-slots-$characterId",
            factory = remember(characterId) {
                PreparedSlotsViewModelFactory(
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
            },
        )
        val uiState by preparedSlotsViewModel.uiState.collectAsState()

        PreparedSlotsRoute(
            uiState = uiState,
            onTrackChange = preparedSlotsViewModel::onTrackChange,
            onChooseSpell = { rank, slotIndex, trackKey ->
                navigationViewModel.startPreparedSlotAssignment(
                    characterId = characterId,
                    rank = rank,
                    slotIndex = slotIndex,
                    trackKey = trackKey,
                )
                navController.navigate(AppDestinations.SpellList.route)
            },
            onClearSpell = preparedSlotsViewModel::clearSpell,
            onCastSlot = preparedSlotsViewModel::castSlot,
            onUncastSlot = preparedSlotsViewModel::uncastSlot,
            onUseFocusPoint = preparedSlotsViewModel::useFocusPoint,
            onIncreaseFocusMax = preparedSlotsViewModel::increaseFocusMax,
            onDecreaseFocusMax = preparedSlotsViewModel::decreaseFocusMax,
            onRefocus = preparedSlotsViewModel::refocus,
            onCastLayOnHands = preparedSlotsViewModel::castLayOnHands,
            onRest = preparedSlotsViewModel::rest,
            onNewDayPreparation = preparedSlotsViewModel::newDayPreparation,
            onPrepareRandom = preparedSlotsViewModel::prepareRandom,
            onRandomPrepareSourceFilterChange = preparedSlotsViewModel::onRandomPrepareSourceFilterChange,
            onRandomPrepareRarityFilterChange = preparedSlotsViewModel::onRandomPrepareRarityFilterChange,
            onClearRandomPrepareFilters = preparedSlotsViewModel::clearRandomPrepareFilters,
            onUndoLastCast = preparedSlotsViewModel::undoLastCast,
            onManageKnownSpells = { trackKey ->
                navigationViewModel.manageKnownSpells(
                    characterId = characterId,
                    trackKey = trackKey,
                )
                navController.navigate(AppDestinations.SpellList.route)
            },
            onOpenSpellBrowser = {
                navigationViewModel.clearSpellBrowserMode()
                navigationViewModel.openSpellList(characterId)
                navController.navigate(AppDestinations.SpellList.route)
            },
            onOpenPreparedSpell = { spellId ->
                navController.navigate(AppDestinations.SpellDetail.routeFor(spellId))
            },
            onBack = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.spellListDestination(
    navController: NavHostController,
    spellListViewModel: SpellListViewModel,
    characterListViewModel: CharacterListViewModel,
    navigationViewModel: SpellAppNavigationViewModel,
    seedUiState: SeedUiState,
    onRetrySeed: () -> Unit,
) {
    composable(route = AppDestinations.SpellList.route) {
        val spellListUiState by spellListViewModel.uiState.collectAsState()
        val spells by spellListViewModel.spells.collectAsState()
        val navigationUiState by navigationViewModel.uiState.collectAsState()
        val characterListUiState by characterListViewModel.uiState.collectAsState()
        LaunchedEffect(Unit) {
            navigationViewModel.slotAssignmentResult.collect { success ->
                if (success) {
                    navController.popBackStack()
                }
            }
        }

        val browserMode = navigationUiState.spellBrowserMode
            ?: SpellBrowserMode.BrowseCatalog(characterId = navigationUiState.activeCharacterId)
        LaunchedEffect(browserMode) {
            spellListViewModel.clearAllFilters()
            spellListViewModel.setBrowserMode(browserMode)
            if (browserMode is SpellBrowserMode.AssignPreparedSlot && browserMode.slotRank == 0) {
                spellListViewModel.setRank(0)
            } else {
                spellListViewModel.clearRankSelection()
            }
        }

        SpellListRoute(
            spells = spells,
            title = spellListTitle(
                browserMode = browserMode,
                activeCharacter = characterListUiState.characters.firstOrNull {
                    it.id == navigationUiState.activeCharacterId
                },
            ),
            browserMode = spellListUiState.browserMode,
            knownSpellIds = spellListUiState.knownSpellIds,
            query = spellListUiState.queryInput,
            onQueryChange = spellListViewModel::onQueryChange,
            traitQuery = spellListUiState.traitQueryInput,
            onTraitQueryChange = spellListViewModel::onTraitQueryChange,
            selectedRank = spellListUiState.selectedRank,
            onRankChange = spellListViewModel::onRankToggle,
            selectedTradition = spellListUiState.selectedTradition,
            onTraditionChange = spellListViewModel::onTraditionToggle,
            selectedRarity = spellListUiState.selectedRarity,
            onRarityChange = spellListViewModel::onRarityToggle,
            isLoading = seedUiState == SeedUiState.Loading,
            loadError = (seedUiState as? SeedUiState.Error)?.message,
            onRetryLoad = onRetrySeed,
            onClearFilters = spellListViewModel::clearAllFilters,
            onSpellClick = { spellId ->
                if (browserMode is SpellBrowserMode.AssignPreparedSlot) {
                    navigationViewModel.completeSlotAssignment(spellId)
                } else {
                    navController.navigate(AppDestinations.SpellDetail.routeFor(spellId))
                }
            },
            onKnownSpellToggle = spellListViewModel::toggleKnownSpell,
            onBack = {
                navigationViewModel.clearSpellBrowserMode()
                navController.popBackStack()
            },
        )
    }
}

private fun NavGraphBuilder.spellDetailDestination(
    navController: NavHostController,
    spellRepository: SpellRepository,
) {
    composable(
        route = AppDestinations.SpellDetail.route,
        arguments = listOf(navArgument(AppDestinations.SpellDetail.argSpellId) {
            type = NavType.StringType
        }),
    ) { backStackEntry ->
        val spellId = backStackEntry.arguments
            ?.getString(AppDestinations.SpellDetail.argSpellId)
            .orEmpty()
        val spellDetailViewModel: SpellDetailViewModel = viewModel(
            key = "spell-detail-$spellId",
            factory = remember(spellId) {
                SpellDetailViewModelFactory(
                    spellId = spellId,
                    spellRepository = spellRepository,
                )
            },
        )
        val spellDetailUiState by spellDetailViewModel.uiState.collectAsState()
        SpellDetailRoute(
            spell = spellDetailUiState.spell,
            isLoading = spellDetailUiState.isLoading,
            onBack = { navController.popBackStack() },
        )
    }
}

private fun spellListTitle(
    browserMode: SpellBrowserMode,
    activeCharacter: CharacterProfile?,
): String {
    return when (browserMode) {
        is SpellBrowserMode.AssignPreparedSlot -> {
            if (browserMode.slotRank == 0) {
                "Choose Cantrip ${browserMode.slotIndex + 1}"
            } else {
                "Choose Rank ${browserMode.slotRank} Slot ${browserMode.slotIndex + 1}"
            }
        }

        is SpellBrowserMode.ManageKnownSpells -> {
            activeCharacter?.let { "${it.name} Known Spells" } ?: "Known Spells"
        }

        is SpellBrowserMode.BrowseCatalog -> {
            activeCharacter?.let { "${it.name} Spells" } ?: "Spell List"
        }
    }
}
