package com.spellapp.core.data.local.foundry

import com.spellapp.core.model.RulesReferenceCategory
import com.spellapp.core.model.RulesTextBlock
import com.spellapp.core.model.RulesTextDocument
import com.spellapp.core.model.RulesTextInline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundryMarkupParserTest {
    @Test
    fun parse_extractsConditionReferenceWithExplicitLabel() {
        val document = FoundryMarkupParser.parse(
            descriptionRaw = "<p>The target is @UUID[Compendium.pf2e.conditionitems.Item.Sickened]{Sickened 1}.</p>",
            description = null,
            localizationResolver = null,
        )

        val references = document.references()
        assertEquals(1, references.size)
        val reference = references.single()
        assertEquals(RulesReferenceCategory.CONDITION, reference.key.category)
        assertEquals("Sickened 1", reference.label)
    }

    @Test
    fun parse_classifiesReferenceCategoriesFromCompendiumPack() {
        val document = FoundryMarkupParser.parse(
            descriptionRaw = """
                <p>
                    @UUID[Compendium.pf2e.actionspf2e.Item.Stride]{Stride}
                    @UUID[Compendium.pf2e.feats-srd.Item.Toughness]{Toughness}
                    @UUID[Compendium.pf2e.spell-effects.Item.ABCD1234]{Bless}
                    @UUID[Compendium.pf2e.spells-srd.Item.MagicMissile]{Magic Missile}
                    @UUID[Compendium.pf2e.equipment-srd.Item.Rope]{Rope}
                </p>
            """.trimIndent(),
            description = null,
            localizationResolver = null,
        )

        val categories = document.references().map { it.key.category }
        assertEquals(
            listOf(
                RulesReferenceCategory.ACTION,
                RulesReferenceCategory.FEAT,
                RulesReferenceCategory.SPELL_EFFECT,
                RulesReferenceCategory.SPELL,
                RulesReferenceCategory.ITEM,
            ),
            categories,
        )
    }

    @Test
    fun parse_resolvesLocalizedMarkup() {
        val document = FoundryMarkupParser.parse(
            descriptionRaw = "<p>@Localize[PF2E.condition.sickened.rules]</p>",
            description = null,
            localizationResolver = MapFoundryLocalizationResolver(
                mapOf("pf2e.condition.sickened.rules" to "You cannot willingly ingest anything."),
            ),
        )

        val paragraph = document.blocks.single() as RulesTextBlock.Paragraph
        assertEquals("You cannot willingly ingest anything.", paragraph.text())
    }

    @Test
    fun parse_preservesActionGlyphDamageCheckTemplateAndInlineRolls() {
        val document = FoundryMarkupParser.parse(
            descriptionRaw = """
                <p><span class="action-glyph">2</span> Deal @Damage[2d6[fire]] and attempt @Check[type:reflex]{a Reflex save} in a @Template[type:burst|distance:20]{20-foot burst}. [[/r 1d4]]{1d4 rounds}</p>
            """.trimIndent(),
            description = null,
            localizationResolver = null,
        )

        val paragraph = document.blocks.single() as RulesTextBlock.Paragraph
        val inlines = paragraph.inlines
        assertTrue(inlines.first() is RulesTextInline.ActionGlyph)
        assertTrue(inlines.any { it is RulesTextInline.Damage && it.label == "2d6 fire" })
        assertTrue(inlines.any { it is RulesTextInline.Check && it.label == "a Reflex save" })
        assertTrue(inlines.any { it is RulesTextInline.Template && it.label == "20-foot burst" })
        assertTrue(inlines.any { it is RulesTextInline.InlineRoll && it.label == "1d4 rounds" })
    }

    @Test
    fun parse_formatsInlineDamageWithoutAddingExplanatoryText() {
        val document = FoundryMarkupParser.parse(
            descriptionRaw = """
                <p>On a critical success, the target also takes @Damage[(@item.level)d4[bleed]] damage.</p>
            """.trimIndent(),
            description = null,
            localizationResolver = null,
            itemLevel = 1,
            itemRank = 1,
        )

        val paragraph = document.blocks.single() as RulesTextBlock.Paragraph
        assertEquals(
            "On a critical success, the target also takes 1d4 bleed damage.",
            paragraph.text(),
        )
    }

    @Test
    fun parse_resolvesRankScaledPersistentDamageInline() {
        val document = FoundryMarkupParser.parse(
            descriptionRaw = """
                <p>The target takes @Damage[(ceil(@item.rank / 2))d4[persistent,electricity]] damage.</p>
            """.trimIndent(),
            description = null,
            localizationResolver = null,
            itemLevel = 3,
            itemRank = 3,
        )

        val paragraph = document.blocks.single() as RulesTextBlock.Paragraph
        assertEquals(
            "The target takes 2d4 persistent electricity damage.",
            paragraph.text(),
        )
    }

    @Test
    fun parse_buildsStructuredBlocksForHeadingsAndLists() {
        val document = FoundryMarkupParser.parse(
            descriptionRaw = """
                <h2>Critical Success</h2>
                <ul>
                    <li>The target takes no damage.</li>
                    <li>The target is @UUID[Compendium.pf2e.conditionitems.Item.OffGuard]{off-guard}.</li>
                </ul>
            """.trimIndent(),
            description = null,
            localizationResolver = null,
        )

        assertEquals(2, document.blocks.size)
        assertTrue(document.blocks[0] is RulesTextBlock.Heading)
        assertTrue(document.blocks[1] is RulesTextBlock.ListBlock)
        val listBlock = document.blocks[1] as RulesTextBlock.ListBlock
        assertFalse(listBlock.ordered)
        assertEquals(2, listBlock.items.size)
        assertEquals(RulesReferenceCategory.CONDITION, document.references().single().key.category)
    }

    @Test
    fun parse_fallsBackToDescriptionWhenRawMarkupMissing() {
        val document = FoundryMarkupParser.parse(
            descriptionRaw = null,
            description = "Plain spell text.",
            localizationResolver = null,
        )

        val paragraph = document.blocks.single() as RulesTextBlock.Paragraph
        assertEquals("Plain spell text.", paragraph.text())
    }
}

private fun RulesTextDocument.references(): List<RulesTextInline.Reference> {
    return buildList {
        blocks.forEach { block ->
            addAll(block.references())
        }
    }
}

private fun RulesTextBlock.references(): List<RulesTextInline.Reference> {
    return when (this) {
        is RulesTextBlock.Paragraph -> inlines.references()
        is RulesTextBlock.Heading -> inlines.references()
        is RulesTextBlock.ListBlock -> items.flatMap { item -> item.blocks.flatMap { block -> block.references() } }
        RulesTextBlock.ThematicBreak -> emptyList()
    }
}

private fun List<RulesTextInline>.references(): List<RulesTextInline.Reference> {
    return flatMap { inline ->
        when (inline) {
            is RulesTextInline.Reference -> listOf(inline)
            is RulesTextInline.Strong -> inline.children.references()
            is RulesTextInline.Emphasis -> inline.children.references()
            else -> emptyList()
        }
    }
}

private fun RulesTextBlock.Paragraph.text(): String {
    return inlines.joinToString(separator = "") { inline ->
        when (inline) {
            is RulesTextInline.Text -> inline.text
            is RulesTextInline.Reference -> inline.label
            is RulesTextInline.Damage -> inline.label
            is RulesTextInline.Check -> inline.label
            is RulesTextInline.Template -> inline.label
            is RulesTextInline.ActionGlyph -> inline.glyph
            is RulesTextInline.InlineRoll -> inline.label
            is RulesTextInline.Strong -> inline.children.filterIsInstance<RulesTextInline.Text>().joinToString("") { it.text }
            is RulesTextInline.Emphasis -> inline.children.filterIsInstance<RulesTextInline.Text>().joinToString("") { it.text }
            else -> ""
        }
    }.trim()
}
