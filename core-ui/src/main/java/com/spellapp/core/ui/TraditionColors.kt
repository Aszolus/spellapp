package com.spellapp.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The four Pathfinder 2e magical traditions, exposed as theme-aware accents.
// These are content-level signal (category/tag), not chrome. Use sparingly.
data class TraditionColors(
    val arcane: Color,
    val divine: Color,
    val occult: Color,
    val primal: Color,
) {
    fun forKey(key: String): Color? = when (key.trim().lowercase()) {
        "arcane" -> arcane
        "divine" -> divine
        "occult" -> occult
        "primal" -> primal
        else -> null
    }
}

internal val SpellAppDarkTraditionColors = TraditionColors(
    arcane = oklch(0.70, 0.10, 285.0),
    divine = oklch(0.80, 0.10, 85.0),
    occult = oklch(0.66, 0.12, 330.0),
    primal = oklch(0.66, 0.10, 150.0),
)

internal val SpellAppLightTraditionColors = TraditionColors(
    arcane = oklch(0.40, 0.11, 285.0),
    divine = oklch(0.50, 0.11, 80.0),
    occult = oklch(0.42, 0.13, 330.0),
    primal = oklch(0.40, 0.10, 150.0),
)

val LocalTraditionColors = staticCompositionLocalOf { SpellAppDarkTraditionColors }
