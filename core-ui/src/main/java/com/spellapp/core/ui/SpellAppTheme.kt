package com.spellapp.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

enum class SpellAppThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

@Composable
fun SpellAppTheme(
    themeMode: SpellAppThemeMode = SpellAppThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        SpellAppThemeMode.DARK -> true
        SpellAppThemeMode.LIGHT -> false
        SpellAppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
