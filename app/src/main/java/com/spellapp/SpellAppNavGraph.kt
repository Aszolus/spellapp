package com.spellapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.spellapp.core.model.CharacterProfile
import com.spellapp.feature.character.CharacterBuilderRoute
import com.spellapp.feature.character.CharacterBuilderViewModel
import com.spellapp.feature.character.CharacterListRoute
import com.spellapp.feature.character.CharacterListViewModel
import com.spellapp.feature.character.spellcasting.prepared.PreparedSlotsRoute
import com.spellapp.feature.character.spellcasting.prepared.PreparedSlotsViewModel
import com.spellapp.feature.spells.SpellBrowserMode
import com.spellapp.feature.spells.SpellDetailRoute
import com.spellapp.feature.spells.SpellDetailViewModel
import com.spellapp.feature.spells.SpellListRoute
import com.spellapp.feature.spells.SpellListViewModel

@Composable
fun SpellAppNavGraph(
    navController: NavHostController,
    characterFeatureFactoryProvider: CharacterFeatureFactoryProvider,
    spellCatalogFeatureFactoryProvider: SpellCatalogFeatureFactoryProvider,
    preparedCastingFeatureFactoryProvider: PreparedCastingFeatureFactoryProvider,
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
            characterFeatureFactoryProvider = characterFeatureFactoryProvider,
            navigationViewModel = navigationViewModel,
        )
        characterBuilderDestination(
            navController = navController,
            characterFeatureFactoryProvider = characterFeatureFactoryProvider,
        )
        preparedSlotsDestination(
            navController = navController,
            preparedCastingFeatureFactoryProvider = preparedCastingFeatureFactoryProvider,
            navigationViewModel = navigationViewModel,
        )
        spellListDestination(
            navController = navController,
            spellCatalogFeatureFactoryProvider = spellCatalogFeatureFactoryProvider,
            characterFeatureFactoryProvider = characterFeatureFactoryProvider,
            navigationViewModel = navigationViewModel,
            seedUiState = seedUiState,
            onRetrySeed = onRetrySeed,
        )
        spellDetailDestination(
            navController = navController,
            spellCatalogFeatureFactoryProvider = spellCatalogFeatureFactoryProvider,
        )
    }
}

private fun NavGraphBuilder.characterListDestination(
    navController: NavHostController,
    characterFeatureFactoryProvider: CharacterFeatureFactoryProvider,
    navigationViewModel: SpellAppNavigationViewModel,
) {
    composable(route = AppDestinations.CharacterList.route) {
        val characterListViewModel: CharacterListViewModel = viewModel(
            key = "character-list",
            factory = remember(characterFeatureFactoryProvider) {
                characterFeatureFactoryProvider.characterListFactory()
            },
        )
        val characterListUiState by characterListViewModel.uiState.collectAsState()
        CharacterListRoute(
            characters = characterListUiState.characters,
            isLoading = characterListUiState.isLoading,
            loadError = characterListUiState.loadError,
            onAddCharacter = {
                navController.navigate(AppDestinations.CharacterBuilder.routeFor(0L))
            },
            onEditCharacter = { character ->
                navController.navigate(AppDestinations.CharacterBuilder.routeFor(character.id))
            },
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
    }
}

private fun NavGraphBuilder.characterBuilderDestination(
    navController: NavHostController,
    characterFeatureFactoryProvider: CharacterFeatureFactoryProvider,
) {
    composable(
        route = AppDestinations.CharacterBuilder.route,
        arguments = listOf(navArgument(AppDestinations.CharacterBuilder.argCharacterId) {
            type = NavType.LongType
        }),
    ) { backStackEntry ->
        val characterId = backStackEntry.arguments
            ?.getLong(AppDestinations.CharacterBuilder.argCharacterId)
            ?: 0L
        val characterBuilderViewModel: CharacterBuilderViewModel = viewModel(
            key = "character-builder-$characterId",
            factory = remember(characterId, characterFeatureFactoryProvider) {
                characterFeatureFactoryProvider.characterBuilderFactory(characterId)
            },
        )
        val uiState by characterBuilderViewModel.uiState.collectAsState()
        LaunchedEffect(characterBuilderViewModel) {
            characterBuilderViewModel.saveEvents.collect {
                navController.popBackStackIfResumed()
            }
        }
        CharacterBuilderRoute(
            uiState = uiState,
            onNameChange = characterBuilderViewModel::updateName,
            onLevelChange = characterBuilderViewModel::updateLevel,
            onAncestrySelected = characterBuilderViewModel::selectAncestry,
            onHeritageSelected = characterBuilderViewModel::selectHeritage,
            onBackgroundSelected = characterBuilderViewModel::selectBackground,
            onClassSelected = characterBuilderViewModel::selectClass,
            onClassChoiceSelected = characterBuilderViewModel::selectClassChoice,
            onKeyAbilitySelected = characterBuilderViewModel::selectKeyAbility,
            onAbilityBoostSelected = characterBuilderViewModel::selectAbilityBoost,
            onVoluntaryFlawEnabledChange = characterBuilderViewModel::setVoluntaryFlawEnabled,
            onSkillChoiceSelected = characterBuilderViewModel::selectSkillChoice,
            onLoreSkillChoiceSelected = characterBuilderViewModel::selectLoreSkillChoice,
            onPromptChoiceSelected = characterBuilderViewModel::selectPromptChoice,
            onFeatSelected = characterBuilderViewModel::selectFeatForSlot,
            onFeatPickerOpen = characterBuilderViewModel::openFeatPicker,
            onFeatPickerDismiss = characterBuilderViewModel::dismissFeatPicker,
            onAcceptedSourcesChange = characterBuilderViewModel::setAcceptedSourceBooks,
            onSourceBookToggle = characterBuilderViewModel::toggleSourceBook,
            onSectionToggle = characterBuilderViewModel::toggleSection,
            onSave = characterBuilderViewModel::save,
            onBack = { navController.popBackStackIfResumed() },
        )
    }
}

private fun NavGraphBuilder.preparedSlotsDestination(
    navController: NavHostController,
    preparedCastingFeatureFactoryProvider: PreparedCastingFeatureFactoryProvider,
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
                preparedCastingFeatureFactoryProvider.preparedSlotsFactory(characterId)
            },
        )
        val uiState by preparedSlotsViewModel.uiState.collectAsState()

        PreparedSlotsRoute(
            uiState = uiState,
            onTrackChange = preparedSlotsViewModel::onTrackChange,
            onChooseSpell = { rank, slotIndex, trackKey, preferredTradition ->
                navigationViewModel.startPreparedSlotAssignment(
                    characterId = characterId,
                    rank = rank,
                    slotIndex = slotIndex,
                    trackKey = trackKey,
                    preferredTradition = preferredTradition,
                )
                navController.navigate(AppDestinations.SpellList.route)
            },
            onClearSpell = preparedSlotsViewModel::clearSpell,
            onCastSlot = preparedSlotsViewModel::castSlot,
            onCastKnownSpell = { spellId, slotRank ->
                preparedSlotsViewModel.castKnownSpell(spellId, slotRank)
            },
            onRemoveKnownSpellFromRepertoire = preparedSlotsViewModel::removeKnownSpellFromRepertoire,
            onUncastSlot = preparedSlotsViewModel::uncastSlot,
            onUseFocusPoint = preparedSlotsViewModel::useFocusPoint,
            onIncreaseFocusMax = preparedSlotsViewModel::increaseFocusMax,
            onDecreaseFocusMax = preparedSlotsViewModel::decreaseFocusMax,
            onRefocus = preparedSlotsViewModel::refocus,
            onCastLayOnHands = preparedSlotsViewModel::castLayOnHands,
            onRest = preparedSlotsViewModel::rest,
            onNewDayPreparation = preparedSlotsViewModel::newDayPreparation,
            onPrepareRandom = preparedSlotsViewModel::prepareRandom,
            onUndoLastCast = preparedSlotsViewModel::undoLastCast,
            onManageKnownSpells = { trackKey, preferredTradition, trackSourceId, initialRank ->
                navigationViewModel.manageKnownSpells(
                    characterId = characterId,
                    trackKey = trackKey,
                    characterLevel = uiState.characterLevel,
                    preferredTradition = preferredTradition,
                    trackSourceId = trackSourceId,
                    initialRank = initialRank,
                )
                navController.navigate(AppDestinations.SpellList.route)
            },
            onOpenSpellBrowser = {
                navigationViewModel.clearSpellBrowserMode()
                navigationViewModel.openSpellList(characterId)
                navController.navigate(AppDestinations.SpellList.route)
            },
            onOpenPreparedSpell = { spellId, heightenedAt ->
                navController.navigate(AppDestinations.SpellDetail.routeFor(spellId, heightenedAt))
            },
            onBack = { navController.popBackStackIfResumed() },
        )
    }
}

private fun NavGraphBuilder.spellListDestination(
    navController: NavHostController,
    spellCatalogFeatureFactoryProvider: SpellCatalogFeatureFactoryProvider,
    characterFeatureFactoryProvider: CharacterFeatureFactoryProvider,
    navigationViewModel: SpellAppNavigationViewModel,
    seedUiState: SeedUiState,
    onRetrySeed: () -> Unit,
) {
    composable(route = AppDestinations.SpellList.route) {
        val spellListViewModel: SpellListViewModel = viewModel(
            key = "spell-list",
            factory = remember(spellCatalogFeatureFactoryProvider) {
                spellCatalogFeatureFactoryProvider.spellListFactory()
            },
        )
        val characterListViewModel: CharacterListViewModel = viewModel(
            key = "character-list",
            factory = remember(characterFeatureFactoryProvider) {
                characterFeatureFactoryProvider.characterListFactory()
            },
        )
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
        LaunchedEffect(browserMode, navigationUiState.spellBrowserSessionId) {
            spellListViewModel.openBrowserMode(
                mode = browserMode,
                sessionId = navigationUiState.spellBrowserSessionId,
            )
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
            knownSpellStatuses = spellListUiState.knownSpellStatuses,
            signatureSpellIds = spellListUiState.signatureSpellIds,
            canManageSignatureSpells = spellListUiState.canManageSignatureSpells,
            allKnownSpellsAreSignature = spellListUiState.allKnownSpellsAreSignature,
            allowanceSummaries = spellListUiState.allowanceSummaries,
            query = spellListUiState.queryInput,
            onQueryChange = spellListViewModel::onQueryChange,
            traitQuery = spellListUiState.traitQueryInput,
            availableTraits = spellListUiState.availableTraits,
            onTraitQueryChange = spellListViewModel::onTraitQueryChange,
            selectedRank = spellListUiState.selectedRank,
            onRankChange = spellListViewModel::onRankToggle,
            selectedTradition = spellListUiState.selectedTradition,
            onTraditionChange = spellListViewModel::onTraditionToggle,
            selectedRarities = spellListUiState.selectedRarities,
            onRarityChange = spellListViewModel::onRarityToggle,
            onClearTraitFilter = spellListViewModel::clearTraitFilter,
            onClearRankFilter = spellListViewModel::clearRankFilter,
            onClearTraditionFilter = spellListViewModel::clearTraditionFilter,
            onClearRarityFilter = spellListViewModel::clearRarityFilter,
            pendingKnownSpellWarning = spellListUiState.pendingKnownSpellWarning,
            onConfirmKnownSpellWarning = spellListViewModel::confirmKnownSpellWarning,
            onDismissKnownSpellWarning = spellListViewModel::dismissKnownSpellWarning,
            isLoading = seedUiState == SeedUiState.Loading,
            loadError = (seedUiState as? SeedUiState.Error)?.message,
            onRetryLoad = onRetrySeed,
            onClearFilters = spellListViewModel::clearStructuredFilters,
            onSpellClick = { spellId ->
                if (browserMode is SpellBrowserMode.AssignPreparedSlot) {
                    navigationViewModel.completeSlotAssignment(spellId)
                } else {
                    navController.navigate(AppDestinations.SpellDetail.routeFor(spellId))
                }
            },
            onKnownSpellToggle = spellListViewModel::toggleKnownSpell,
            onSignatureSpellToggle = spellListViewModel::toggleSignatureSpell,
            onLearnAllKnownSpells = spellListViewModel::learnAllVisibleSpells,
            onUnlearnAllKnownSpells = spellListViewModel::unlearnAllVisibleSpells,
            onBack = {
                if (navController.popBackStackIfResumed()) {
                    navigationViewModel.clearSpellBrowserMode()
                }
            },
        )
    }
}

private fun NavGraphBuilder.spellDetailDestination(
    navController: NavHostController,
    spellCatalogFeatureFactoryProvider: SpellCatalogFeatureFactoryProvider,
) {
    composable(
        route = AppDestinations.SpellDetail.route,
        arguments = listOf(
            navArgument(AppDestinations.SpellDetail.argSpellId) {
                type = NavType.StringType
            },
            navArgument(AppDestinations.SpellDetail.argHeightenedAt) {
                type = NavType.IntType
                defaultValue = -1
            },
        ),
    ) { backStackEntry ->
        val spellId = backStackEntry.arguments
            ?.getString(AppDestinations.SpellDetail.argSpellId)
            .orEmpty()
        val heightenedAtArg = backStackEntry.arguments
            ?.getInt(AppDestinations.SpellDetail.argHeightenedAt)
            ?: -1
        val heightenedAt = heightenedAtArg.takeIf { it >= 0 }
        val spellDetailViewModel: SpellDetailViewModel = viewModel(
            key = "spell-detail-$spellId-${heightenedAt ?: "none"}",
            factory = remember(spellId, heightenedAt) {
                spellCatalogFeatureFactoryProvider.spellDetailFactory(
                    spellId = spellId,
                    heightenedAt = heightenedAt,
                )
            },
        )
        val spellDetailUiState by spellDetailViewModel.uiState.collectAsState()
        SpellDetailRoute(
            spell = spellDetailUiState.spell,
            isLoading = spellDetailUiState.isLoading,
            traitLookups = spellDetailUiState.traitLookups,
            rulesDocument = spellDetailUiState.rulesDocument,
            heightenedEntryDocuments = spellDetailUiState.heightenedEntryDocuments,
            referenceLookups = spellDetailUiState.referenceLookups,
            heightenedAt = spellDetailUiState.heightenedAt,
            onBack = { navController.popBackStackIfResumed() },
        )
    }
}

private fun NavHostController.popBackStackIfResumed(): Boolean {
    val lifecycle = currentBackStackEntry?.lifecycle ?: return false
    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return false
    return popBackStack()
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
