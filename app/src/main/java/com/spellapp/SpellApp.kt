package com.spellapp

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.spellapp.core.ui.SpellAppTheme
import com.spellapp.feature.spells.SpellDetailRoute
import com.spellapp.feature.spells.SpellListRoute

@Composable
fun SpellApp() {
    val navController = rememberNavController()

    SpellAppTheme {
        NavHost(
            navController = navController,
            startDestination = AppDestinations.SpellList.route,
        ) {
            composable(route = AppDestinations.SpellList.route) {
                SpellListRoute(
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
                SpellDetailRoute(
                    spellId = spellId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
