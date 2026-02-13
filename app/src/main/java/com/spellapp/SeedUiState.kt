package com.spellapp

sealed interface SeedUiState {
    data object Loading : SeedUiState
    data object Ready : SeedUiState
    data class Error(val message: String) : SeedUiState
}
