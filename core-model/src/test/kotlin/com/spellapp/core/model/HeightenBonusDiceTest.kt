package com.spellapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeightenBonusDiceTest {
    private fun step(inc: Int, text: String) =
        HeightenedEntry(HeightenTrigger.Step(inc), text)

    private fun absolute(rank: Int, text: String) =
        HeightenedEntry(HeightenTrigger.Absolute(rank), text)

    @Test
    fun nullWhenNotHeightened() {
        val entries = listOf(step(1, "damage increases by 1d4"))
        assertNull(heightenBonusDice(entries, baseRank = 1, heightenedAt = 1))
    }

    @Test
    fun cantripStepPlusOneTwoApplications() {
        val entries = listOf(step(1, "damage increases by 1d4"))
        assertEquals("+2d4", heightenBonusDice(entries, baseRank = 1, heightenedAt = 3))
    }

    @Test
    fun fireballStepPlusOneTwoApplications() {
        val entries = listOf(step(1, "damage increases by 2d6"))
        assertEquals("+4d6", heightenBonusDice(entries, baseRank = 3, heightenedAt = 5))
    }

    @Test
    fun plusTwoStepFloorsApplications() {
        val entries = listOf(step(2, "damage increases by 1d6"))
        assertEquals("+1d6", heightenBonusDice(entries, baseRank = 1, heightenedAt = 4))
    }

    @Test
    fun ignoresAbsoluteEntries() {
        val entries = listOf(absolute(3, "deals 6d6"))
        assertNull(heightenBonusDice(entries, baseRank = 3, heightenedAt = 5))
    }

    @Test
    fun combinesMatchingDice() {
        val entries = listOf(
            step(1, "fire damage increases by 1d6 and cold by 1d4"),
        )
        assertEquals("+2d6+2d4", heightenBonusDice(entries, baseRank = 3, heightenedAt = 5))
    }

    @Test
    fun noDiceInTextReturnsNull() {
        val entries = listOf(step(1, "range increases by 30 feet"))
        assertNull(heightenBonusDice(entries, baseRank = 1, heightenedAt = 3))
    }
}
