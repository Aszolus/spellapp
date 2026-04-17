package com.spellapp.core.model

private val DICE_PATTERN = Regex("(\\d+)d(\\d+)")

fun heightenBonusDice(
    entries: List<HeightenedEntry>,
    baseRank: Int,
    heightenedAt: Int,
): String? {
    if (heightenedAt <= baseRank) return null
    val totals = linkedMapOf<Int, Int>()
    for (entry in entries) {
        val trigger = entry.trigger as? HeightenTrigger.Step ?: continue
        val increment = trigger.increment
        if (increment <= 0) continue
        val applications = (heightenedAt - baseRank) / increment
        if (applications <= 0) continue
        DICE_PATTERN.findAll(entry.text).forEach { match ->
            val count = match.groupValues[1].toIntOrNull() ?: return@forEach
            val size = match.groupValues[2].toIntOrNull() ?: return@forEach
            totals[size] = (totals[size] ?: 0) + count * applications
        }
    }
    if (totals.isEmpty()) return null
    return totals.entries
        .sortedByDescending { it.key }
        .joinToString("+") { (size, count) -> "${count}d${size}" }
        .let { "+$it" }
}
