package com.spellapp.core.data.local

import com.spellapp.core.model.CastingProgressionType

internal interface SlotProgressionEngine {
    fun slotCountsByRank(
        level: Int,
        progressionType: CastingProgressionType,
    ): Map<Int, Int>
}

internal class DefaultSlotProgressionEngine : SlotProgressionEngine {
    override fun slotCountsByRank(
        level: Int,
        progressionType: CastingProgressionType,
    ): Map<Int, Int> {
        return when (progressionType) {
            CastingProgressionType.FULL_PREPARED -> fullPrepared(level)
            CastingProgressionType.ARCHETYPE_PREPARED -> archetypePrepared(level)
        }
    }

    private fun fullPrepared(level: Int): Map<Int, Int> {
        val slots = mutableMapOf<Int, Int>()
        slots[0] = 5
        for (rank in 1..9) {
            val unlockLevel = (rank * 2) - 1
            if (level >= unlockLevel) {
                slots[rank] = 3
            }
        }
        if (level >= 19) {
            slots[10] = 1
        }
        return slots
    }

    private fun archetypePrepared(level: Int): Map<Int, Int> {
        val unlockByRank = mapOf(
            1 to 4,
            2 to 6,
            3 to 8,
            4 to 10,
            5 to 12,
            6 to 14,
            7 to 16,
            8 to 18,
        )
        return buildMap {
            unlockByRank.forEach { (rank, unlockLevel) ->
                if (level >= unlockLevel) {
                    put(rank, 1)
                }
            }
        }
    }
}
