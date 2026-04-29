package com.spellapp

import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.spellapp.core.data.PerfTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }
    private var seedUiState: SeedUiState by mutableStateOf(SeedUiState.Ready)

    override fun onCreate(savedInstanceState: Bundle?) {
        PerfTrace.enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        PerfTrace.mark("MainActivity.onCreate")
        super.onCreate(savedInstanceState)
        setContent {
            SpellApp(
                characterFeatureFactoryProvider = appContainer.characterFeatureFactoryProvider,
                spellCatalogFeatureFactoryProvider = appContainer.spellCatalogFeatureFactoryProvider,
                preparedCastingFeatureFactoryProvider = appContainer.preparedCastingFeatureFactoryProvider,
                navigationViewModelFactory = appContainer.navigationViewModelFactory,
                seedUiState = seedUiState,
                onRetrySeed = ::syncSpellData,
            )
        }
    }

    private fun syncSpellData() {
        seedUiState = SeedUiState.Loading
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    appContainer.seedSpellsIfNeeded()
                }
            }.onSuccess {
                seedUiState = SeedUiState.Ready
            }.onFailure { throwable ->
                throwable.printStackTrace()
                seedUiState = SeedUiState.Error(
                    message = throwable.message ?: "Spell dataset sync failed.",
                )
            }
        }
    }
}
