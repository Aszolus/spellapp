package com.spellapp.core.data.local

import com.spellapp.core.model.HeightenTrigger
import com.spellapp.core.model.HeightenedEntry

internal object HeightenedDescriptionParser {
    private val BLOCK_PATTERN = Regex(
        pattern = "<p>\\s*<strong>\\s*Heightened\\s*\\(([^)]+)\\)\\s*</strong>(.*?)</p>",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val PLAIN_PATTERN = Regex(
        pattern = "(?m)^\\s*Heightened\\s*\\(([^)]+)\\)\\s*(.*?)(?=(?:\\n\\s*Heightened\\s*\\()|\\Z)",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val STEP_PATTERN = Regex("^\\+(\\d+)$")
    private val ABSOLUTE_PATTERN = Regex("^(\\d+)(st|nd|rd|th)$")
    private val TAG_PATTERN = Regex("<[^>]+>")
    private val INLINE_UUID_PATTERN = Regex("@UUID\\[[^\\]]*\\]\\{([^}]*)\\}")
    private val INLINE_UUID_BARE_PATTERN = Regex("@UUID\\[[^\\]]*\\]")
    private val INLINE_DAMAGE_PATTERN = Regex("@Damage\\[([^\\]]*)\\]")
    private val INLINE_CHECK_PATTERN = Regex("@Check\\[[^\\]]*\\]\\{([^}]*)\\}")
    private val INLINE_CHECK_BARE_PATTERN = Regex("@Check\\[[^\\]]*\\]")

    fun parse(descriptionRaw: String?, description: String?): List<HeightenedEntry> {
        if (!descriptionRaw.isNullOrBlank()) {
            val entries = extractFromHtml(descriptionRaw)
            if (entries.isNotEmpty()) {
                return entries
            }
        }
        if (!description.isNullOrBlank()) {
            return extractFromPlain(description)
        }
        return emptyList()
    }

    private fun extractFromHtml(raw: String): List<HeightenedEntry> {
        val entries = mutableListOf<HeightenedEntry>()
        BLOCK_PATTERN.findAll(raw).forEach { match ->
            val triggerRaw = match.groupValues.getOrNull(1).orEmpty()
            val bodyRaw = match.groupValues.getOrNull(2).orEmpty()
            val trigger = parseTrigger(triggerRaw) ?: return@forEach
            val text = cleanText(bodyRaw)
            entries += HeightenedEntry(trigger = trigger, text = text)
        }
        return entries
    }

    private fun extractFromPlain(description: String): List<HeightenedEntry> {
        val entries = mutableListOf<HeightenedEntry>()
        PLAIN_PATTERN.findAll(description).forEach { match ->
            val triggerRaw = match.groupValues.getOrNull(1).orEmpty()
            val bodyRaw = match.groupValues.getOrNull(2).orEmpty()
            val trigger = parseTrigger(triggerRaw) ?: return@forEach
            val text = cleanText(bodyRaw)
            entries += HeightenedEntry(trigger = trigger, text = text)
        }
        return entries
    }

    private fun parseTrigger(raw: String): HeightenTrigger? {
        val normalized = raw.trim().lowercase().replace(" ", "")
        if (normalized.isBlank()) return null
        STEP_PATTERN.matchEntire(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { step ->
            if (step > 0) return HeightenTrigger.Step(step)
        }
        ABSOLUTE_PATTERN.matchEntire(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { rank ->
            if (rank > 0) return HeightenTrigger.Absolute(rank)
        }
        return null
    }

    private fun cleanText(raw: String): String {
        var text = raw
        text = INLINE_UUID_PATTERN.replace(text) { it.groupValues[1] }
        text = INLINE_UUID_BARE_PATTERN.replace(text, "")
        text = INLINE_CHECK_PATTERN.replace(text) { it.groupValues[1] }
        text = INLINE_CHECK_BARE_PATTERN.replace(text, "")
        text = INLINE_DAMAGE_PATTERN.replace(text) { it.groupValues[1] }
        text = TAG_PATTERN.replace(text, "")
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
