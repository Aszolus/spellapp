package com.spellapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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

@Composable
fun SpellApp(
    spellRepository: SpellRepository,
) {
    val navController = rememberNavController()
    val spells by spellRepository.observeSpells().collectAsState(initial = emptyList())

    SpellAppTheme {
        NavHost(
            navController = navController,
            startDestination = AppDestinations.SpellList.route,
        ) {
            composable(route = AppDestinations.SpellList.route) {
                SpellListRoute(
                    spells = spells,
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
                val spell by produceState<SpellDetail?>(initialValue = null, key1 = spellId) {
                    value = spellRepository.getSpellDetail(spellId)
                }
                SpellDetailRoute(
                    spell = spell,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
