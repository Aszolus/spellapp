package com.spellapp.feature.spells

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.spellapp.core.model.RulesReferenceCategory
import com.spellapp.core.model.RulesReferenceKey
import com.spellapp.core.model.RulesTextBlock
import com.spellapp.core.model.RulesTextDocument
import com.spellapp.core.model.RulesTextInline
import com.spellapp.core.model.RulesTextListItem

@Composable
internal fun RulesTextDocumentText(
    document: RulesTextDocument,
    referenceLookups: Map<RulesReferenceKey, SpellReferenceLookupUiState>,
    modifier: Modifier = Modifier,
    enableLookups: Boolean = true,
    onLookupClick: (SpellLookupDialogState) -> Unit = {},
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val rendered = remember(document, referenceLookups, enableLookups, primaryColor) {
        renderRulesTextDocument(
            document = document,
            referenceLookups = referenceLookups,
            enableLookups = enableLookups,
            primaryColor = primaryColor,
        )
    }
    val style = MaterialTheme.typography.bodyMedium.copy(color = onSurfaceColor)
    if (rendered.lookupTargets.isEmpty()) {
        Text(
            text = rendered.text,
            style = style,
            modifier = modifier,
        )
    } else {
        ClickableText(
            text = rendered.text,
            style = style,
            modifier = modifier,
            onClick = { offset ->
                rendered.text
                    .getStringAnnotations(
                        tag = SPELL_LOOKUP_ANNOTATION_TAG,
                        start = offset,
                        end = offset,
                    )
                    .firstOrNull()
                    ?.item
                    ?.toIntOrNull()
                    ?.let { index ->
                        rendered.lookupTargets.getOrNull(index)?.let(onLookupClick)
                    }
            },
        )
    }
}

private fun renderRulesTextDocument(
    document: RulesTextDocument,
    referenceLookups: Map<RulesReferenceKey, SpellReferenceLookupUiState>,
    enableLookups: Boolean,
    primaryColor: Color,
): RenderedRulesText {
    val lookupTargets = mutableListOf<SpellLookupDialogState>()
    val annotated = buildAnnotatedString {
        appendBlocks(
            blocks = document.blocks,
            referenceLookups = referenceLookups,
            enableLookups = enableLookups,
            primaryColor = primaryColor,
            lookupTargets = lookupTargets,
            indent = "",
        )
    }
    return RenderedRulesText(
        text = annotated,
        lookupTargets = lookupTargets,
    )
}

private fun AnnotatedString.Builder.appendBlocks(
    blocks: List<RulesTextBlock>,
    referenceLookups: Map<RulesReferenceKey, SpellReferenceLookupUiState>,
    enableLookups: Boolean,
    primaryColor: Color,
    lookupTargets: MutableList<SpellLookupDialogState>,
    indent: String,
) {
    blocks.forEachIndexed { index, block ->
        if (index > 0) {
            append("\n\n")
        }
        when (block) {
            is RulesTextBlock.Paragraph -> {
                if (indent.isNotEmpty()) append(indent)
                appendInlines(
                    inlines = block.inlines,
                    referenceLookups = referenceLookups,
                    enableLookups = enableLookups,
                    primaryColor = primaryColor,
                    lookupTargets = lookupTargets,
                )
            }

            is RulesTextBlock.Heading -> {
                if (indent.isNotEmpty()) append(indent)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    appendInlines(
                        inlines = block.inlines,
                        referenceLookups = referenceLookups,
                        enableLookups = enableLookups,
                        primaryColor = primaryColor,
                        lookupTargets = lookupTargets,
                    )
                }
            }

            is RulesTextBlock.ListBlock -> {
                block.items.forEachIndexed { itemIndex, item ->
                    if (itemIndex > 0) append("\n")
                    appendListItem(
                        item = item,
                        prefix = if (block.ordered) "${itemIndex + 1}. " else "- ",
                        referenceLookups = referenceLookups,
                        enableLookups = enableLookups,
                        primaryColor = primaryColor,
                        lookupTargets = lookupTargets,
                        indent = indent,
                    )
                }
            }

            RulesTextBlock.ThematicBreak -> {
                if (indent.isNotEmpty()) append(indent)
                append("---")
            }
        }
    }
}

private fun AnnotatedString.Builder.appendListItem(
    item: RulesTextListItem,
    prefix: String,
    referenceLookups: Map<RulesReferenceKey, SpellReferenceLookupUiState>,
    enableLookups: Boolean,
    primaryColor: Color,
    lookupTargets: MutableList<SpellLookupDialogState>,
    indent: String,
) {
    append(indent)
    append(prefix)
    item.blocks.forEachIndexed { blockIndex, childBlock ->
        if (blockIndex > 0) {
            append("\n")
            append(indent)
            append("  ")
        }
        when (childBlock) {
            is RulesTextBlock.Paragraph -> appendInlines(
                inlines = childBlock.inlines,
                referenceLookups = referenceLookups,
                enableLookups = enableLookups,
                primaryColor = primaryColor,
                lookupTargets = lookupTargets,
            )

            is RulesTextBlock.Heading -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendInlines(
                    inlines = childBlock.inlines,
                    referenceLookups = referenceLookups,
                    enableLookups = enableLookups,
                    primaryColor = primaryColor,
                    lookupTargets = lookupTargets,
                )
            }

            is RulesTextBlock.ListBlock -> appendBlocks(
                blocks = listOf(childBlock),
                referenceLookups = referenceLookups,
                enableLookups = enableLookups,
                primaryColor = primaryColor,
                lookupTargets = lookupTargets,
                indent = "$indent  ",
            )

            RulesTextBlock.ThematicBreak -> append("---")
        }
    }
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<RulesTextInline>,
    referenceLookups: Map<RulesReferenceKey, SpellReferenceLookupUiState>,
    enableLookups: Boolean,
    primaryColor: Color,
    lookupTargets: MutableList<SpellLookupDialogState>,
) {
    inlines.forEach { inline ->
        when (inline) {
            is RulesTextInline.Text -> append(inline.text)

            is RulesTextInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                appendInlines(
                    inlines = inline.children,
                    referenceLookups = referenceLookups,
                    enableLookups = enableLookups,
                    primaryColor = primaryColor,
                    lookupTargets = lookupTargets,
                )
            }

            is RulesTextInline.Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(
                    inlines = inline.children,
                    referenceLookups = referenceLookups,
                    enableLookups = enableLookups,
                    primaryColor = primaryColor,
                    lookupTargets = lookupTargets,
                )
            }

            is RulesTextInline.Reference -> {
                val lookup = referenceLookups[inline.key]
                val isClickable = enableLookups &&
                    inline.key.category == RulesReferenceCategory.CONDITION &&
                    lookup?.document != null
                if (isClickable) {
                    val annotation = lookupTargets.size.toString()
                    lookupTargets += SpellLookupDialogState(
                        title = inline.label,
                        document = lookup.document,
                    )
                    val start = length
                    append(inline.label)
                    val end = length
                    addStyle(
                        style = SpanStyle(
                            color = primaryColor,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                        ),
                        start = start,
                        end = end,
                    )
                    addStringAnnotation(
                        tag = SPELL_LOOKUP_ANNOTATION_TAG,
                        annotation = annotation,
                        start = start,
                        end = end,
                    )
                } else {
                    append(inline.label)
                }
            }

            is RulesTextInline.Damage -> append(inline.label)
            is RulesTextInline.Check -> append(inline.label)
            is RulesTextInline.Template -> append(inline.label)
            is RulesTextInline.InlineRoll -> append(inline.label)
            is RulesTextInline.ActionGlyph -> append("[${inline.glyph.toReadableActionGlyph()}]")
        }
    }
}

private fun String.toReadableActionGlyph(): String {
    return when (trim().lowercase()) {
        "f" -> "Free Action"
        "r" -> "Reaction"
        "1" -> "1 action"
        "2" -> "2 actions"
        "3" -> "3 actions"
        "1/2" -> "1 or 2 actions"
        "1 - 3" -> "1 to 3 actions"
        "2/3" -> "2 or 3 actions"
        "3,3" -> "2 rounds"
        "a" -> "Attack"
        else -> trim()
    }
}

private data class RenderedRulesText(
    val text: AnnotatedString,
    val lookupTargets: List<SpellLookupDialogState>,
)
