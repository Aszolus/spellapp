package com.spellapp.core.data.local

import com.spellapp.core.model.RulesReferenceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellRulesTextParserTest {
    @Test
    fun parse_extractsConditionReferenceWithExplicitLabel() {
        val parsed = SpellRulesTextParser.parse(
            descriptionRaw = "<p>The target is @UUID[Compendium.pf2e.conditionitems.Item.Sickened]{Sickened 1}.</p>",
            description = null,
        )

        assertEquals("The target is Sickened 1.", parsed.text)
        assertEquals(1, parsed.references.size)
        assertEquals(RulesReferenceCategory.CONDITION, parsed.references[0].key.category)
        assertEquals("sickened", parsed.references[0].key.slug)
        assertEquals("Sickened 1", parsed.references[0].label)
        assertEquals("Sickened 1", parsed.text.substring(parsed.references[0].start, parsed.references[0].end))
    }

    @Test
    fun parse_extractsConditionReferenceWithoutExplicitLabel() {
        val parsed = SpellRulesTextParser.parse(
            descriptionRaw = "<p>The ally becomes @UUID[Compendium.pf2e.conditionitems.Item.Quickened].</p>",
            description = null,
        )

        assertEquals("The ally becomes Quickened.", parsed.text)
        assertEquals(1, parsed.references.size)
        assertEquals("quickened", parsed.references[0].key.slug)
        assertEquals("Quickened", parsed.references[0].label)
    }

    @Test
    fun parse_keepsRepeatedConditionReferencesInOrder() {
        val parsed = SpellRulesTextParser.parse(
            descriptionRaw = """
                <p>The target is @UUID[Compendium.pf2e.conditionitems.Item.Frightened]{Frightened 1}.</p>
                <p>On a critical failure, it becomes @UUID[Compendium.pf2e.conditionitems.Item.Frightened]{Frightened 2}.</p>
            """.trimIndent(),
            description = null,
        )

        assertEquals(2, parsed.references.size)
        assertTrue(parsed.references[0].start < parsed.references[1].start)
        assertEquals("frightened", parsed.references[0].key.slug)
        assertEquals("frightened", parsed.references[1].key.slug)
        assertEquals("Frightened 1", parsed.references[0].label)
        assertEquals("Frightened 2", parsed.references[1].label)
    }

    @Test
    fun parse_preservesNumericConditionLabelWhileResolvingBaseSlug() {
        val parsed = SpellRulesTextParser.parse(
            descriptionRaw = "<p>The target is @UUID[Compendium.pf2e.conditionitems.Item.Stunned]{Stunned 1}.</p>",
            description = null,
        )

        assertEquals(1, parsed.references.size)
        assertEquals("stunned", parsed.references[0].key.slug)
        assertEquals("Stunned 1", parsed.references[0].label)
    }

    @Test
    fun parse_ignoresNonConditionUuidsForClickability() {
        val parsed = SpellRulesTextParser.parse(
            descriptionRaw = "<p>You gain @UUID[Compendium.pf2e.spells-srd.Item.magic-missile]{magic missile}.</p>",
            description = null,
        )

        assertEquals("You gain magic missile.", parsed.text)
        assertTrue(parsed.references.isEmpty())
    }
}
