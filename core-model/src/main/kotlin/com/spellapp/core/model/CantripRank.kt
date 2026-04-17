package com.spellapp.core.model

fun effectiveCantripRank(characterLevel: Int): Int {
    return ((characterLevel + 1) / 2).coerceAtLeast(1)
}
