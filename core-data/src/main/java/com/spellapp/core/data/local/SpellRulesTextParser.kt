package com.spellapp.core.data.local

import androidx.core.text.HtmlCompat
import com.spellapp.core.model.RulesReferenceCategory
import com.spellapp.core.model.RulesReferenceKey
import com.spellapp.core.model.SpellRulesReference
import com.spellapp.core.model.SpellRulesText

internal object SpellRulesTextParser {
    private val uuidWithLabelPattern = Regex("@UUID\\[([^\\]]+)\\]\\{([^}]+)\\}")
    private val uuidBarePattern = Regex("@UUID\\[([^\\]]+)\\]")
    private val damagePattern = Regex("@Damage\\[(.*?)]")
    private val checkPattern = Regex("@Check\\[(.*?)]\\{([^}]+)\\}")
    private val checkBarePattern = Regex("@Check\\[(.*?)]")
    private val templatePattern = Regex("@Template\\[(.*?)]")
    private val localizePattern = Regex("@Localize\\[(.*?)]")
    private val inlineRollPattern = Regex("\\[\\[/[^\\]]+\\]\\]")
    private val brPattern = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val hrPattern = Regex("<\\s*hr\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val paragraphClosePattern = Regex("<\\s*/p\\s*>", RegexOption.IGNORE_CASE)
    private val paragraphOpenPattern = Regex("<\\s*p[^>]*\\s*>", RegexOption.IGNORE_CASE)
    private val listItemOpenPattern = Regex("<\\s*li[^>]*\\s*>", RegexOption.IGNORE_CASE)
    private val listItemClosePattern = Regex("<\\s*/li\\s*>", RegexOption.IGNORE_CASE)
    private val blockTagPattern = Regex("<\\s*/?(ul|ol|h1|h2|h3|h4|h5|h6)[^>]*\\s*>", RegexOption.IGNORE_CASE)
    private val tagPattern = Regex("<[^>]+>")
    private val whitespacePattern = Regex("[\\t ]+")
    private val trimmedNewlinePattern = Regex(" *\\n *")
    private val repeatedNewlinePattern = Regex("(\\n){3,}")
    private const val CONDITION_UUID_SEGMENT = "Compendium.pf2e.conditionitems.Item."

    fun parse(
        descriptionRaw: String?,
        description: String?,
    ): SpellRulesText {
        if (descriptionRaw.isNullOrBlank()) {
            return SpellRulesText(text = description.orEmpty().trim())
        }

        val pendingReferences = mutableListOf<PendingReference>()
        var working = uuidWithLabelPattern.replace(descriptionRaw) { match ->
            replaceUuidReference(
                uuid = match.groupValues[1],
                label = match.groupValues[2],
                pendingReferences = pendingReferences,
            )
        }
        working = uuidBarePattern.replace(working) { match ->
            val fallbackLabel = labelFromUuid(match.groupValues[1])
            replaceUuidReference(
                uuid = match.groupValues[1],
                label = fallbackLabel,
                pendingReferences = pendingReferences,
            )
        }
        working = damagePattern.replace(working) { "Damage: ${it.groupValues[1]}" }
        working = checkPattern.replace(working) { it.groupValues[2] }
        working = checkBarePattern.replace(working) { "Check: ${it.groupValues[1]}" }
        working = templatePattern.replace(working) { "Template: ${it.groupValues[1]}" }
        working = localizePattern.replace(working) { it.groupValues[1] }
        working = inlineRollPattern.replace(working, "")
        working = brPattern.replace(working, "\n")
        working = hrPattern.replace(working, "\n---\n")
        working = paragraphClosePattern.replace(working, "\n")
        working = paragraphOpenPattern.replace(working, "")
        working = listItemOpenPattern.replace(working, "- ")
        working = listItemClosePattern.replace(working, "\n")
        working = blockTagPattern.replace(working, "\n")
        working = tagPattern.replace(working, "")
        working = runCatching {
            HtmlCompat.fromHtml(working, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }.getOrElse {
            working
        }
        working = working.replace("\r", "")
        working = whitespacePattern.replace(working, " ")
        working = trimmedNewlinePattern.replace(working, "\n")
        working = repeatedNewlinePattern.replace(working, "\n\n")
        var plainText = working.trim()
        val resolvedReferences = mutableListOf<SpellRulesReference>()
        pendingReferences.forEach { pending ->
            val start = plainText.indexOf(pending.token)
            if (start < 0) {
                return@forEach
            }
            plainText = plainText.replaceRange(start, start + pending.token.length, pending.label)
            resolvedReferences += SpellRulesReference(
                key = pending.key,
                label = pending.label,
                start = start,
                end = start + pending.label.length,
            )
        }

        return SpellRulesText(
            text = plainText,
            references = resolvedReferences.sortedBy { it.start },
        )
    }

    private fun replaceUuidReference(
        uuid: String,
        label: String,
        pendingReferences: MutableList<PendingReference>,
    ): String {
        if (!uuid.contains(CONDITION_UUID_SEGMENT, ignoreCase = true)) {
            return label
        }
        val slug = normalizeRulesReferenceSlug(uuid.substringAfterLast('.'))
        if (slug.isBlank()) {
            return label
        }
        val token = "__COND_REF_${pendingReferences.size}__"
        pendingReferences += PendingReference(
            token = token,
            key = RulesReferenceKey(
                category = RulesReferenceCategory.CONDITION,
                slug = slug,
            ),
            label = label,
        )
        return token
    }

    private fun labelFromUuid(uuid: String): String {
        return uuid.substringAfterLast('.')
            .replace('_', ' ')
            .trim()
    }

    private data class PendingReference(
        val token: String,
        val key: RulesReferenceKey,
        val label: String,
    )
}
