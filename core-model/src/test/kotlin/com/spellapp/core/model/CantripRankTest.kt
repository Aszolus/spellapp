package com.spellapp.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CantripRankTest {
    @Test
    fun level1to2_rank1() {
        assertEquals(1, effectiveCantripRank(1))
        assertEquals(1, effectiveCantripRank(2))
    }

    @Test
    fun level3to4_rank2() {
        assertEquals(2, effectiveCantripRank(3))
        assertEquals(2, effectiveCantripRank(4))
    }

    @Test
    fun level19to20_rank10() {
        assertEquals(10, effectiveCantripRank(19))
        assertEquals(10, effectiveCantripRank(20))
    }

    @Test
    fun floorsAtRank1ForZeroOrNegative() {
        assertEquals(1, effectiveCantripRank(0))
        assertEquals(1, effectiveCantripRank(-5))
    }
}
