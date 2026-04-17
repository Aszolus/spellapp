package com.spellapp.core.data.local

import com.spellapp.core.model.HeightenTrigger
import com.spellapp.core.model.HeightenedEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HeightenedEntryCodecTest {
    @Test
    fun encode_and_decode_roundTripsEntries() {
        val entries = listOf(
            HeightenedEntry(HeightenTrigger.Absolute(3), "Damage increases to 2d6."),
            HeightenedEntry(HeightenTrigger.Step(2), "Damage increases by 1d6."),
        )

        val json = HeightenedEntryCodec.encode(entries)
        val decoded = HeightenedEntryCodec.decode(json)

        assertEquals(entries, decoded)
    }

    @Test
    fun encode_emptyList_returnsCanonicalEmptyArray() {
        assertEquals("[]", HeightenedEntryCodec.encode(emptyList()))
    }

    @Test
    fun decode_blankStringReturnsEmpty() {
        assertTrue(HeightenedEntryCodec.decode("").isEmpty())
    }

    @Test
    fun decode_skipsMalformedEntries() {
        val json = """[{"type":"absolute","rank":5,"text":"ok"},{"type":"step"},{"type":"absolute","rank":-1}]"""

        val decoded = HeightenedEntryCodec.decode(json)

        assertEquals(1, decoded.size)
        assertEquals(HeightenTrigger.Absolute(5), decoded[0].trigger)
    }
}
