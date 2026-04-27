package com.spellapp.core.data.local

import com.spellapp.core.model.RulesReferenceCategory
import com.spellapp.core.model.RulesTextBlock
import com.spellapp.core.model.RulesTextDocument
import com.spellapp.core.model.RulesTextInline
import org.junit.Assert.assertEquals
import org.junit.Test

class HeightenedRulesTextParserTest {
    @Test
    fun parse_preservesConditionReferencesFromHeightenedHtml() {
        val raw = """
            <p><strong>Heightened (4th)</strong> Add @UUID[Compendium.pf2e.conditionitems.Item.Confused], @UUID[Compendium.pf2e.conditionitems.Item.Controlled], and @UUID[Compendium.pf2e.conditionitems.Item.Slowed] to the list of conditions.</p>
            <p><strong>Heightened (6th)</strong> As 4th rank, plus add @UUID[Compendium.pf2e.conditionitems.Item.Doomed].</p>
            <p><strong>Heightened (8th)</strong> As 4th rank, plus add doomed and @UUID[Compendium.pf2e.conditionitems.Item.Stunned].</p>
        """.trimIndent()

        val documents = HeightenedRulesTextParser.parse(
            descriptionRaw = raw,
            description = null,
            localizationResolver = null,
        )

        assertEquals(3, documents.size)
        assertEquals("Add Confused, Controlled, and Slowed to the list of conditions.", documents[0].text())
        assertEquals(
            listOf(
                RulesReferenceCategory.CONDITION,
                RulesReferenceCategory.CONDITION,
                RulesReferenceCategory.CONDITION,
            ),
            documents[0].references().map { it.key.category },
        )
        assertEquals("As 4th rank, plus add Doomed.", documents[1].text())
        assertEquals(RulesReferenceCategory.CONDITION, documents[1].references().single().key.category)
        assertEquals("As 4th rank, plus add doomed and Stunned.", documents[2].text())
        assertEquals(RulesReferenceCategory.CONDITION, documents[2].references().single().key.category)
    }
}

private fun RulesTextDocument.references(): List<RulesTextInline.Reference> {
    return blocks.flatMap { block -> block.references() }
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

private fun RulesTextDocument.text(): String {
    return blocks.joinToString("\n") { block ->
        when (block) {
            is RulesTextBlock.Paragraph -> block.inlines.text()
            is RulesTextBlock.Heading -> block.inlines.text()
            is RulesTextBlock.ListBlock -> block.items.joinToString("\n") { item ->
                item.blocks.joinToString("\n") { child ->
                    when (child) {
                        is RulesTextBlock.Paragraph -> child.inlines.text()
                        is RulesTextBlock.Heading -> child.inlines.text()
                        is RulesTextBlock.ListBlock -> ""
                        RulesTextBlock.ThematicBreak -> "---"
                    }
                }
            }
            RulesTextBlock.ThematicBreak -> "---"
        }
    }.trim()
}

private fun List<RulesTextInline>.text(): String {
    return joinToString(separator = "") { inline ->
        when (inline) {
            is RulesTextInline.Text -> inline.text
            is RulesTextInline.Reference -> inline.label
            is RulesTextInline.Damage -> inline.label
            is RulesTextInline.Check -> inline.label
            is RulesTextInline.Template -> inline.label
            is RulesTextInline.ActionGlyph -> inline.glyph
            is RulesTextInline.InlineRoll -> inline.label
            is RulesTextInline.Strong -> inline.children.text()
            is RulesTextInline.Emphasis -> inline.children.text()
        }
    }.trim()
}
