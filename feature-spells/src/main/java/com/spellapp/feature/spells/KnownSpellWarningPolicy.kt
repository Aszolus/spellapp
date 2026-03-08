package com.spellapp.feature.spells

import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.spellSupportsTradition

interface KnownSpellWarningPolicy {
    fun warningFor(
        detail: SpellDetail,
        preferredTradition: String?,
        trackSourceId: String?,
    ): PendingKnownSpellWarning?
}

class DefaultKnownSpellWarningPolicy : KnownSpellWarningPolicy {
    override fun warningFor(
        detail: SpellDetail,
        preferredTradition: String?,
        trackSourceId: String?,
    ): PendingKnownSpellWarning? = knownSpellWarningFor(
        detail = detail,
        preferredTradition = preferredTradition,
        trackSourceId = trackSourceId,
    )
}

data class PendingKnownSpellWarning(
    val spellId: String,
    val title: String,
    val message: String,
    val confirmLabel: String = "Add Anyway",
)

internal fun knownSpellWarningFor(
    detail: SpellDetail,
    preferredTradition: String?,
    trackSourceId: String?,
): PendingKnownSpellWarning? {
    val normalizedPreferredTradition = preferredTradition?.trim()?.lowercase().orEmpty()
    val explicitTraditions = detail.tradition.split(',')
        .map { tradition -> tradition.trim() }
        .filter { tradition -> tradition.isNotBlank() }

    if (normalizedPreferredTradition.isNotBlank() && explicitTraditions.isNotEmpty()) {
        val isPreferredTraditionMatch = spellSupportsTradition(
            traditions = detail.tradition,
            preferredTradition = normalizedPreferredTradition,
        )
        if (!isPreferredTraditionMatch) {
            return PendingKnownSpellWarning(
                spellId = detail.id,
                title = "Add Off-Tradition Spell?",
                message = "${detail.name} is not part of the default ${
                    normalizedPreferredTradition.replaceFirstChar { it.uppercase() }
                } tradition for this track. Add it to known spells anyway?",
            )
        }
        return null
    }

    if (explicitTraditions.isNotEmpty()) {
        return null
    }

    val normalizedTrackSourceId = trackSourceId?.trim()?.lowercase().orEmpty()
    val classTraits = detail.traits
        .map { trait -> trait.trim().lowercase() }
        .filter { trait -> trait in classTraitMarkers }
        .distinct()

    if (classTraits.isNotEmpty()) {
        if (normalizedTrackSourceId !in classTraits) {
            val markerLabel = classTraits.joinToString(", ") { trait ->
                trait.replaceFirstChar { it.uppercase() }
            }
            val sourceLabel = normalizedTrackSourceId
                .takeIf { it.isNotBlank() }
                ?.replaceFirstChar { it.uppercase() }
                ?: "this track"
            return PendingKnownSpellWarning(
                spellId = detail.id,
                title = "Add Class-Specific Spell?",
                message = "${detail.name} has no listed tradition and is marked $markerLabel, not $sourceLabel. Add it to known spells anyway?",
            )
        }
        return null
    }

    return PendingKnownSpellWarning(
        spellId = detail.id,
        title = "Add Spell Without Tradition?",
        message = "${detail.name} has no listed tradition. Add it to known spells anyway?",
    )
}

// Derived from the dev-time PF2e class pack filenames under D:\pf2e\packs\pf2e\classes.
private val classTraitMarkers = setOf(
    "alchemist",
    "animist",
    "barbarian",
    "bard",
    "champion",
    "cleric",
    "commander",
    "druid",
    "exemplar",
    "fighter",
    "guardian",
    "gunslinger",
    "inventor",
    "investigator",
    "kineticist",
    "magus",
    "monk",
    "oracle",
    "psychic",
    "ranger",
    "rogue",
    "sorcerer",
    "summoner",
    "swashbuckler",
    "thaumaturge",
    "witch",
    "wizard",
)
