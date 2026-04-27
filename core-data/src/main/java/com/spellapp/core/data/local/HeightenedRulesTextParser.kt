package com.spellapp.core.data.local

import com.spellapp.core.data.local.foundry.FoundryLocalizationResolver
import com.spellapp.core.data.local.foundry.FoundryMarkupParser
import com.spellapp.core.model.RulesTextDocument

internal object HeightenedRulesTextParser {
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

    fun parse(
        descriptionRaw: String?,
        description: String?,
        localizationResolver: FoundryLocalizationResolver?,
        itemLevel: Int? = null,
        itemRank: Int? = null,
    ): List<RulesTextDocument> {
        if (!descriptionRaw.isNullOrBlank()) {
            val documents = extractFromHtml(
                raw = descriptionRaw,
                localizationResolver = localizationResolver,
                itemLevel = itemLevel,
                itemRank = itemRank,
            )
            if (documents.isNotEmpty()) {
                return documents
            }
        }
        if (!description.isNullOrBlank()) {
            return extractFromPlain(
                description = description,
                localizationResolver = localizationResolver,
                itemLevel = itemLevel,
                itemRank = itemRank,
            )
        }
        return emptyList()
    }

    private fun extractFromHtml(
        raw: String,
        localizationResolver: FoundryLocalizationResolver?,
        itemLevel: Int?,
        itemRank: Int?,
    ): List<RulesTextDocument> {
        return BLOCK_PATTERN.findAll(raw)
            .mapNotNull { match ->
                val triggerRaw = match.groupValues.getOrNull(1).orEmpty()
                if (!isValidTrigger(triggerRaw)) return@mapNotNull null
                val bodyRaw = match.groupValues.getOrNull(2).orEmpty().trim()
                FoundryMarkupParser.parse(
                    descriptionRaw = "<p>$bodyRaw</p>",
                    description = null,
                    localizationResolver = localizationResolver,
                    itemLevel = itemLevel,
                    itemRank = itemRank,
                )
            }
            .toList()
    }

    private fun extractFromPlain(
        description: String,
        localizationResolver: FoundryLocalizationResolver?,
        itemLevel: Int?,
        itemRank: Int?,
    ): List<RulesTextDocument> {
        return PLAIN_PATTERN.findAll(description)
            .mapNotNull { match ->
                val triggerRaw = match.groupValues.getOrNull(1).orEmpty()
                if (!isValidTrigger(triggerRaw)) return@mapNotNull null
                val bodyRaw = match.groupValues.getOrNull(2).orEmpty().trim()
                FoundryMarkupParser.parse(
                    descriptionRaw = null,
                    description = bodyRaw,
                    localizationResolver = localizationResolver,
                    itemLevel = itemLevel,
                    itemRank = itemRank,
                )
            }
            .toList()
    }

    private fun isValidTrigger(raw: String): Boolean {
        val normalized = raw.trim().lowercase().replace(" ", "")
        if (normalized.isBlank()) return false
        val step = STEP_PATTERN.matchEntire(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (step != null && step > 0) return true
        val rank = ABSOLUTE_PATTERN.matchEntire(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        return rank != null && rank > 0
    }
}
