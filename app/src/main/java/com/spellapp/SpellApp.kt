package com.spellapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.ui.SpellAppTheme
import com.spellapp.feature.spells.SpellDetailRoute
import com.spellapp.feature.spells.SpellListRoute
import kotlinx.coroutines.delay

@Composable
fun SpellApp(
    spellRepository: SpellRepository,
    seedUiState: SeedUiState,
    onRetrySeed: () -> Unit,
) {
    val navController = rememberNavController()
    var queryInput by remember { mutableStateOf("") }
    var traitQueryInput by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var traitQuery by remember { mutableStateOf("") }
    var selectedRank by remember { mutableStateOf<Int?>(null) }
    var selectedTradition by remember { mutableStateOf<String?>(null) }
    var selectedRarity by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(queryInput) {
        delay(250)
        query = queryInput
    }
    LaunchedEffect(traitQueryInput) {
        delay(250)
        traitQuery = traitQueryInput
    }

    val spells by spellRepository.observeSpells(
        query = query,
        rank = selectedRank,
        tradition = selectedTradition,
        rarity = selectedRarity,
        trait = traitQuery,
    ).collectAsState(initial = emptyList())

    SpellAppTheme {
        NavHost(
            navController = navController,
            startDestination = AppDestinations.SpellList.route,
        ) {
            composable(route = AppDestinations.SpellList.route) {
                SpellListRoute(
                    spells = spells,
                    query = queryInput,
                    onQueryChange = { queryInput = it },
                    traitQuery = traitQueryInput,
                    onTraitQueryChange = { traitQueryInput = it },
                    selectedRank = selectedRank,
                    onRankChange = { rank ->
                        selectedRank = if (selectedRank == rank) null else rank
                    },
                    selectedTradition = selectedTradition,
                    onTraditionChange = { tradition ->
                        selectedTradition = if (selectedTradition == tradition) null else tradition
                    },
                    selectedRarity = selectedRarity,
                    onRarityChange = { rarity ->
                        selectedRarity = if (selectedRarity == rarity) null else rarity
                    },
                    isLoading = seedUiState == SeedUiState.Loading,
                    loadError = (seedUiState as? SeedUiState.Error)?.message,
                    onRetryLoad = onRetrySeed,
                    onClearFilters = {
                        queryInput = ""
                        traitQueryInput = ""
                        query = ""
                        traitQuery = ""
                        selectedRank = null
                        selectedTradition = null
                        selectedRarity = null
                    },
                    onSpellClick = { spellId ->
                        navController.navigate(AppDestinations.SpellDetail.routeFor(spellId))
                    },
                )
            }
            composable(
                route = AppDestinations.SpellDetail.route,
                arguments = listOf(navArgument(AppDestinations.SpellDetail.argSpellId) {
                    type = NavType.StringType
                }),
            ) { backStackEntry ->
                val spellId = backStackEntry.arguments
                    ?.getString(AppDestinations.SpellDetail.argSpellId)
                    .orEmpty()
                var spell by remember(spellId) { mutableStateOf<SpellDetail?>(null) }
                var isSpellLoading by remember(spellId) { mutableStateOf(true) }
                LaunchedEffect(spellId) {
                    isSpellLoading = true
                    spell = spellRepository.getSpellDetail(spellId)
                    isSpellLoading = false
                }
                SpellDetailRoute(
                    spell = spell,
                    isLoading = isSpellLoading,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
