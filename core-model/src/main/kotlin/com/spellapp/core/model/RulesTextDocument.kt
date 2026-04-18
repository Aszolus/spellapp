package com.spellapp.core.model

data class RulesTextDocument(
    val blocks: List<RulesTextBlock> = emptyList(),
) {
    val isEmpty: Boolean
        get() = blocks.isEmpty()

    companion object {
        fun fromPlainText(text: String?): RulesTextDocument {
            val normalized = text.orEmpty()
                .replace("\r", "")
                .trim()
            if (normalized.isBlank()) {
                return RulesTextDocument()
            }

            val blocks = normalized
                .split(Regex("\\n{2,}"))
                .mapNotNull { paragraph ->
                    val collapsed = paragraph
                        .lines()
                        .joinToString(" ") { it.trim() }
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    if (collapsed.isBlank()) {
                        null
                    } else {
                        RulesTextBlock.Paragraph(
                            inlines = listOf(RulesTextInline.Text(collapsed)),
                        )
                    }
                }

            return RulesTextDocument(blocks = blocks)
        }
    }
}

fun RulesTextDocument.referenceKeys(): Set<RulesReferenceKey> {
    return buildSet {
        blocks.forEach { block ->
            addReferenceKeysFromBlock(block)
        }
    }
}

sealed interface RulesTextBlock {
    data class Paragraph(
        val inlines: List<RulesTextInline>,
    ) : RulesTextBlock

    data class Heading(
        val level: Int,
        val inlines: List<RulesTextInline>,
    ) : RulesTextBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<RulesTextListItem>,
    ) : RulesTextBlock

    data object ThematicBreak : RulesTextBlock
}

data class RulesTextListItem(
    val blocks: List<RulesTextBlock>,
)

sealed interface RulesTextInline {
    data class Text(
        val text: String,
    ) : RulesTextInline

    data class Strong(
        val children: List<RulesTextInline>,
    ) : RulesTextInline

    data class Emphasis(
        val children: List<RulesTextInline>,
    ) : RulesTextInline

    data class Reference(
        val key: RulesReferenceKey,
        val label: String,
    ) : RulesTextInline

    data class Damage(
        val formula: String,
        val label: String,
    ) : RulesTextInline

    data class Check(
        val target: String,
        val label: String,
    ) : RulesTextInline

    data class Template(
        val params: String,
        val label: String,
    ) : RulesTextInline

    data class ActionGlyph(
        val glyph: String,
    ) : RulesTextInline

    data class InlineRoll(
        val formula: String,
        val label: String,
    ) : RulesTextInline
}

private fun MutableSet<RulesReferenceKey>.addReferenceKeysFromBlock(
    block: RulesTextBlock,
) {
    when (block) {
        is RulesTextBlock.Paragraph -> addReferenceKeysFromInlines(block.inlines)
        is RulesTextBlock.Heading -> addReferenceKeysFromInlines(block.inlines)
        is RulesTextBlock.ListBlock -> block.items.forEach { item ->
            item.blocks.forEach { child ->
                addReferenceKeysFromBlock(child)
            }
        }
        RulesTextBlock.ThematicBreak -> Unit
    }
}

private fun MutableSet<RulesReferenceKey>.addReferenceKeysFromInlines(
    inlines: List<RulesTextInline>,
) {
    inlines.forEach { inline ->
        when (inline) {
            is RulesTextInline.Reference -> add(inline.key)
            is RulesTextInline.Strong -> addReferenceKeysFromInlines(inline.children)
            is RulesTextInline.Emphasis -> addReferenceKeysFromInlines(inline.children)
            else -> Unit
        }
    }
}
