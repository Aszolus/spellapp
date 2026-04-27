package com.spellapp.core.data.local

import com.spellapp.core.model.HeightenTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeightenedDescriptionParserTest {
    @Test
    fun parse_extractsAbsoluteAndStepTriggersFromHtml() {
        val raw = """
            <p>Base effect.</p><hr /><p><strong>Heightened (3rd)</strong> Damage increases to 2d6.</p>
            <p><strong>Heightened (+2)</strong> Damage increases by 1d6.</p>
        """.trimIndent()

        val entries = HeightenedDescriptionParser.parse(descriptionRaw = raw, description = null)

        assertEquals(2, entries.size)
        assertEquals(HeightenTrigger.Absolute(3), entries[0].trigger)
        assertTrue(entries[0].text.contains("2d6"))
        assertEquals(HeightenTrigger.Step(2), entries[1].trigger)
    }

    @Test
    fun parse_stripsHtmlTagsAndInlineMacros() {
        val raw = "<p><strong>Heightened (5th)</strong> The target is @UUID[Compendium.x.Item.y]{frightened}" +
            " and takes @Damage[2d6[fire]] extra fire damage.</p>"

        val entries = HeightenedDescriptionParser.parse(descriptionRaw = raw, description = null)

        assertEquals(1, entries.size)
        val text = entries[0].text
        assertTrue("should replace UUID label: $text", text.contains("frightened"))
        assertTrue("should keep damage expression: $text", text.contains("2d6[fire]"))
        assertTrue("should strip tags: $text", !text.contains("<") && !text.contains(">"))
    }

    @Test
    fun parse_preservesBareUuidItemNamesFromHtml() {
        val raw = """
            <p><strong>Heightened (4th)</strong> Add @UUID[Compendium.pf2e.conditionitems.Item.Confused], @UUID[Compendium.pf2e.conditionitems.Item.Controlled], and @UUID[Compendium.pf2e.conditionitems.Item.Slowed] to the list of conditions.</p>
            <p><strong>Heightened (6th)</strong> As 4th rank, plus add @UUID[Compendium.pf2e.conditionitems.Item.Doomed].</p>
            <p><strong>Heightened (8th)</strong> As 4th rank, plus add doomed and @UUID[Compendium.pf2e.conditionitems.Item.Stunned].</p>
        """.trimIndent()

        val entries = HeightenedDescriptionParser.parse(descriptionRaw = raw, description = null)

        assertEquals(3, entries.size)
        assertEquals("Add Confused, Controlled, and Slowed to the list of conditions.", entries[0].text)
        assertEquals("As 4th rank, plus add Doomed.", entries[1].text)
        assertEquals("As 4th rank, plus add doomed and Stunned.", entries[2].text)
    }

    @Test
    fun parse_returnsEmptyWhenNoHeightenBlocks() {
        val raw = "<p>Plain description with no heighten text.</p>"

        val entries = HeightenedDescriptionParser.parse(descriptionRaw = raw, description = null)

        assertTrue(entries.isEmpty())
    }

    @Test
    fun parse_fallsBackToPlainDescriptionWhenRawMissing() {
        val plain = "Base effect.\n\n---\nHeightened (+1) Damage increases by 1d4.\nHeightened (7th) Add persistent acid."

        val entries = HeightenedDescriptionParser.parse(descriptionRaw = null, description = plain)

        assertEquals(2, entries.size)
        assertEquals(HeightenTrigger.Step(1), entries[0].trigger)
        assertEquals(HeightenTrigger.Absolute(7), entries[1].trigger)
    }

    @Test
    fun parse_ignoresMalformedTrigger() {
        val raw = "<p><strong>Heightened (banana)</strong> Does nothing.</p>"

        val entries = HeightenedDescriptionParser.parse(descriptionRaw = raw, description = null)

        assertTrue(entries.isEmpty())
    }
}
