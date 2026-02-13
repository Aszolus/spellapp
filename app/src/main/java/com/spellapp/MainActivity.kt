package com.spellapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { AppContainer(applicationContext) }
    private var seedUiState: SeedUiState by mutableStateOf(SeedUiState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncSpellData()
        setContent {
            SpellApp(
                spellRepository = appContainer.spellRepository,
                seedUiState = seedUiState,
                onRetrySeed = ::syncSpellData,
            )
        }
    }

    private fun syncSpellData() {
        seedUiState = SeedUiState.Loading
        lifecycleScope.launch {
            runCatching {
                appContainer.seedSpellsIfNeeded()
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
