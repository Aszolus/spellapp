package com.spellapp.core.model

fun ordinalRank(rank: Int): String {
    val suffix = when {
        rank % 100 in 11..13 -> "th"
        rank % 10 == 1 -> "st"
        rank % 10 == 2 -> "nd"
        rank % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$rank$suffix"
}
