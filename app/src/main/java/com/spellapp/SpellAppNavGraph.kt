package com.spellapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.spellapp.core.data.CharacterRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CharacterProfile
import com.spellapp.core.model.SpellListItem
import com.spellapp.feature.character.CharacterEditorDialog
import com.spellapp.feature.character.CharacterListUiState
import com.spellapp.feature.character.CharacterListRoute
import com.spellapp.feature.character.CharacterListViewModel
import com.spellapp.feature.character.PreparedSlotsRoute
import com.spellapp.feature.character.PreparedSlotsViewModel
import com.spellapp.feature.character.PreparedSlotsViewModelFactory
import com.spellapp.feature.spells.SpellDetailRoute
import com.spellapp.feature.spells.SpellDetailViewModel
import com.spellapp.feature.spells.SpellDetailViewModelFactory
import com.spellapp.feature.spells.SpellListRoute
import com.spellapp.feature.spells.SpellListUiState
import com.spellapp.feature.spells.SpellListViewModel
import kotlinx.coroutines.launch

@Composable
fun SpellAppNavGraph(
    navController: NavHostController,
    spellRepository: SpellRepository,
    characterRepository: CharacterRepository,
    seedUiState: SeedUiState,
    onRetrySeed: () -> Unit,
    characterListUiState: CharacterListUiState,
    characterListViewModel: CharacterListViewModel,
    spellListUiState: SpellListUiState,
    spellListViewModel: SpellListViewModel,
    spells: List<SpellListItem>,
    navigationUiState: SpellAppNavigationUiState,
    onOpenPreparedSlots: (Long) -> Unit,
    onOpenSpellList: (Long) -> Unit,
    onStartPreparedSlotAssignment: (Long, Int, Int) -> Unit,
    onClearPreparedSlotTarget: () -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = AppDestinations.CharacterList.route,
    ) {
        characterListDestination(
            navController = navController,
            characterListUiState = characterListUiState,
            characterListViewModel = characterListViewModel,
            onOpenPreparedSlots = onOpenPreparedSlots,
            onOpenSpellList = onOpenSpellList,
        )
        preparedSlotsDestination(
            navController = navController,
            characterListUiState = characterListUiState,
            characterRepository = characterRepository,
            spellRepository = spellRepository,
            spellListViewModel = spellListViewModel,
            onOpenSpellList = onOpenSpellList,
            onStartPreparedSlotAssignment = onStartPreparedSlotAssignment,
            onClearPreparedSlotTarget = onClearPreparedSlotTarget,
        )
        spellListDestination(
            navController = navController,
            spells = spells,
            spellListUiState = spellListUiState,
            spellListViewModel = spellListViewModel,
            characterListUiState = characterListUiState,
            navigationUiState = navigationUiState,
            characterRepository = characterRepository,
            seedUiState = seedUiState,
            onRetrySeed = onRetrySeed,
            onClearPreparedSlotTarget = onClearPreparedSlotTarget,
        )
        spellDetailDestination(
            navController = navController,
            spellRepository = spellRepository,
        )
    }
}

private fun NavGraphBuilder.characterListDestination(
    navController: NavHostController,
    characterListUiState: CharacterListUiState,
    characterListViewModel: CharacterListViewModel,
    onOpenPreparedSlots: (Long) -> Unit,
    onOpenSpellList: (Long) -> Unit,
) {
    composable(route = AppDestinations.CharacterList.route) {
        CharacterListRoute(
            characters = characterListUiState.characters,
            classDefinitionsByClass = characterListUiState.classDefinitionsByClass,
            onAddCharacter = characterListViewModel::onAddCharacterRequest,
            onEditCharacter = characterListViewModel::onEditCharacterRequest,
            onDeleteCharacter = { character -> characterListViewModel.deleteCharacter(character.id) },
            onOpenPreparedSlots = { character ->
                onOpenPreparedSlots(character.id)
                navController.navigate(AppDestinations.PreparedSlots.routeFor(character.id))
            },
            onOpenSpells = { character ->
                onOpenSpellList(character.id)
                navController.navigate(AppDestinations.SpellList.route)
            },
        )
        if (characterListUiState.isEditorVisible) {
            CharacterEditorDialog(
                initialCharacter = characterListUiState.editingCharacter,
                availableClasses = characterListUiState.availableClasses,
                classDefinitionsByClass = characterListUiState.classDefinitionsByClass,
                onDismiss = characterListViewModel::dismissEditor,
                onSave = characterListViewModel::saveCharacter,
            )
        }
    }
}

private fun NavGraphBuilder.preparedSlotsDestination(
    navController: NavHostController,
    characterListUiState: CharacterListUiState,
    characterRepository: CharacterRepository,
    spellRepository: SpellRepository,
    spellListViewModel: SpellListViewModel,
    onOpenSpellList: (Long) -> Unit,
    onStartPreparedSlotAssignment: (Long, Int, Int) -> Unit,
    onClearPreparedSlotTarget: () -> Unit,
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
        val character = characterListUiState.characters.firstOrNull { it.id == characterId }
        val preparedSlotsViewModel: PreparedSlotsViewModel = viewModel(
            key = "prepared-slots-$characterId",
            factory = remember(characterId) {
                PreparedSlotsViewModelFactory(
                    characterId = characterId,
                    characterRepository = characterRepository,
                    spellRepository = spellRepository,
                )
            },
        )
        val preparedSlotsUiState by preparedSlotsViewModel.uiState.collectAsState()

        PreparedSlotsRoute(
            characterName = character?.name ?: "Character",
            selectedRank = preparedSlotsUiState.selectedRank,
            slotsForRank = preparedSlotsUiState.slotsForRank,
            spellNameById = preparedSlotsUiState.spellNameById,
            onRankChange = preparedSlotsViewModel::onRankChange,
            onAddSlot = preparedSlotsViewModel::addSlot,
            onRemoveSlot = preparedSlotsViewModel::removeSlot,
            onChooseSpell = { rank, slotIndex ->
                onStartPreparedSlotAssignment(characterId, rank, slotIndex)
                spellListViewModel.setRank(rank)
                spellListViewModel.clearTextFilters()
                navController.navigate(AppDestinations.SpellList.route)
            },
            onClearSpell = preparedSlotsViewModel::clearSpell,
            onCastSlot = preparedSlotsViewModel::castSlot,
            canUndoLastCast = preparedSlotsUiState.canUndoLastCast,
            onUndoLastCast = preparedSlotsViewModel::undoLastCast,
            onOpenSpellBrowser = {
                onClearPreparedSlotTarget()
                onOpenSpellList(characterId)
                navController.navigate(AppDestinations.SpellList.route)
            },
            recentEvents = preparedSlotsUiState.recentEventLines,
            onBack = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.spellListDestination(
    navController: NavHostController,
    spells: List<SpellListItem>,
    spellListUiState: SpellListUiState,
    spellListViewModel: SpellListViewModel,
    characterListUiState: CharacterListUiState,
    navigationUiState: SpellAppNavigationUiState,
    characterRepository: CharacterRepository,
    seedUiState: SeedUiState,
    onRetrySeed: () -> Unit,
    onClearPreparedSlotTarget: () -> Unit,
) {
    composable(route = AppDestinations.SpellList.route) {
        val coroutineScope = rememberCoroutineScope()
        SpellListRoute(
            spells = spells,
            title = spellListTitle(
                target = navigationUiState.preparedSlotTarget,
                activeCharacter = characterListUiState.characters.firstOrNull {
                    it.id == navigationUiState.activeCharacterId
                },
            ),
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
                val target = navigationUiState.preparedSlotTarget
                if (target != null) {
                    coroutineScope.launch {
                        characterRepository.assignSpellToPreparedSlot(
                            characterId = target.characterId,
                            rank = target.rank,
                            slotIndex = target.slotIndex,
                            spellId = spellId,
                        )
                        onClearPreparedSlotTarget()
                        navController.popBackStack()
                    }
                } else {
                    navController.navigate(AppDestinations.SpellDetail.routeFor(spellId))
                }
            },
            onBack = {
                onClearPreparedSlotTarget()
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
    target: PreparedSlotTarget?,
    activeCharacter: CharacterProfile?,
): String {
    return target?.let {
        if (it.rank == 0) {
            "Choose Cantrip ${it.slotIndex + 1}"
        } else {
            "Choose Rank ${it.rank} Slot ${it.slotIndex + 1}"
        }
    } ?: activeCharacter?.let { "${it.name} Spells" } ?: "Spell List"
}
