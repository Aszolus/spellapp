package com.spellapp.core.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomSpellRepositoryTest {
    @Test
    fun normalizeTraitCatalog_splitsDeduplicatesAndSortsTraits() {
        val traits = normalizeTraitCatalog(
            listOf(
                "fire,attack",
                "Attack, evocation",
                "",
                "  healing ",
            ),
        )

        assertEquals(
            listOf("attack", "evocation", "fire", "healing"),
            traits,
        )
    }
}
