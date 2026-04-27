package com.spellapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.spellapp.core.ui.SpellAppTheme
import com.spellapp.core.ui.SpellAppThemeMode

@Composable
fun SpellApp(
    characterFeatureFactoryProvider: CharacterFeatureFactoryProvider,
    spellCatalogFeatureFactoryProvider: SpellCatalogFeatureFactoryProvider,
    preparedCastingFeatureFactoryProvider: PreparedCastingFeatureFactoryProvider,
    navigationViewModelFactory: SpellAppNavigationViewModelFactory,
    seedUiState: SeedUiState,
    onRetrySeed: () -> Unit,
    themeMode: SpellAppThemeMode = SpellAppThemeMode.DARK,
) {
    val navController = rememberNavController()
    val navigationViewModel: SpellAppNavigationViewModel = viewModel(
        key = "app-navigation",
        factory = remember(navigationViewModelFactory) { navigationViewModelFactory },
    )

    SpellAppTheme(themeMode = themeMode) {
        SpellAppNavGraph(
            navController = navController,
            characterFeatureFactoryProvider = characterFeatureFactoryProvider,
            spellCatalogFeatureFactoryProvider = spellCatalogFeatureFactoryProvider,
            preparedCastingFeatureFactoryProvider = preparedCastingFeatureFactoryProvider,
            navigationViewModel = navigationViewModel,
            seedUiState = seedUiState,
            onRetrySeed = onRetrySeed,
        )
    }
}
