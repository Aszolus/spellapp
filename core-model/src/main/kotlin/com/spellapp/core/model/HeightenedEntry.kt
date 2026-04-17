package com.spellapp.core.model

data class HeightenedEntry(
    val trigger: HeightenTrigger,
    val text: String,
)

sealed class HeightenTrigger {
    data class Absolute(val rank: Int) : HeightenTrigger()

    data class Step(val increment: Int) : HeightenTrigger()
}
