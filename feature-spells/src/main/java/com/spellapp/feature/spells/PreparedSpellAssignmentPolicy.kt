package com.spellapp.feature.spells

import com.spellapp.core.model.CastingStyle
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.ClassSpellcastingCatalog
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SpellListItem
import com.spellapp.core.model.SpellcastingTradition
import com.spellapp.core.model.traditionFor
import com.spellapp.core.rules.spellcasting.SpellTradition
import com.spellapp.core.rules.spellcasting.TrackSpellLegalityProfile
import com.spellapp.core.rules.spellcasting.parseSpellTradition
import com.spellapp.core.rules.spellcasting.parseSpellTraditions

data class PreparedSlotAssignmentContext(
    val characterClass: CharacterClass,
    val trackKey: String,
    val slotRank: Int,
    val preferredTradition: String? = null,
)

interface PreparedSpellAssignmentPolicy {
    fun isSpellLegalTarget(
        spell: SpellListItem,
        context: PreparedSlotAssignmentContext,
    ): Boolean

    fun filterLegalTargets(
        spells: Iterable<SpellListItem>,
        context: PreparedSlotAssignmentContext,
    ): List<SpellListItem> {
        return spells.filter { spell -> isSpellLegalTarget(spell, context) }
    }
}

/**
 * UI-layer pre-filter for the assignment picker.
 * Authoritative validation should still happen in domain/data write paths.
 */
class DefaultPreparedSpellAssignmentPolicy(
    private val legalityProfileSource: PreparedTrackLegalityProfileSource =
        DefaultPreparedTrackLegalityProfileSource(),
) : PreparedSpellAssignmentPolicy {
    override fun isSpellLegalTarget(
        spell: SpellListItem,
        context: PreparedSlotAssignmentContext,
    ): Boolean {
        val legalityProfile = legalityProfileSource.profileFor(context)
        val spellTraditions = parseSpellTraditions(spell.tradition)
        if (!legalityProfile.isSpellLegal(spell.id, spellTraditions)) {
            return false
        }

        return isPreparedRankCompatible(
            spellRank = spell.rank,
            slotRank = context.slotRank,
        )
    }

    private fun isPreparedRankCompatible(
        spellRank: Int,
        slotRank: Int,
    ): Boolean {
        if (slotRank <= 0) {
            return spellRank == 0
        }
        if (spellRank <= 0) {
            return false
        }
        return spellRank <= slotRank
    }
}

fun interface PreparedTrackLegalityProfileSource {
    fun profileFor(context: PreparedSlotAssignmentContext): TrackSpellLegalityProfile
}

class DefaultPreparedTrackLegalityProfileSource(
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource = ClassSpellcastingCatalog,
) : PreparedTrackLegalityProfileSource {
    override fun profileFor(context: PreparedSlotAssignmentContext): TrackSpellLegalityProfile {
        val traditions = allowedTraditionsForTrack(context)
        return TrackSpellLegalityProfile(
            allowedTraditions = traditions,
        )
    }

    private fun allowedTraditionsForTrack(context: PreparedSlotAssignmentContext): Set<SpellTradition> {
        context.preferredTradition
            ?.let { parseSpellTradition(it) }
            ?.takeUnless { tradition -> tradition == SpellTradition.OTHER }
            ?.let { tradition -> return setOf(tradition) }

        if (context.trackKey == PreparedSlot.PRIMARY_TRACK_KEY) {
            return traditionForPrimaryClass(context.characterClass)?.let { setOf(it) }
                ?: emptySet()
        }

        if (context.trackKey.startsWith(ARCHETYPE_TRACK_PREFIX)) {
            val archetypeId = context.trackKey.removePrefix(ARCHETYPE_TRACK_PREFIX).trim()
            return traditionForArchetype(archetypeId)?.let { setOf(it) }
                ?: emptySet()
        }

        return emptySet()
    }

    private fun traditionForPrimaryClass(characterClass: CharacterClass): SpellTradition? {
        return classSpellcastingCatalogSource.traditionFor(
            characterClass = characterClass,
            selectedOptionIds = emptySet(),
        )?.toRulesTradition()
    }

    private fun traditionForArchetype(archetypeId: String): SpellTradition? {
        val definition = ClassSpellcastingCatalog.classFromId(archetypeId)
            ?.let(classSpellcastingCatalogSource::definitionFor)
            ?: return null
        if (definition.primaryTracks.none { track -> track.castingStyle == CastingStyle.PREPARED }) {
            return null
        }
        return definition.baseTradition?.toRulesTradition()
    }

    private companion object {
        private const val ARCHETYPE_TRACK_PREFIX = "archetype-"
    }
}

private fun SpellcastingTradition.toRulesTradition(): SpellTradition? {
    return when (this) {
        SpellcastingTradition.ARCANE -> SpellTradition.ARCANE
        SpellcastingTradition.DIVINE -> SpellTradition.DIVINE
        SpellcastingTradition.OCCULT -> SpellTradition.OCCULT
        SpellcastingTradition.PRIMAL -> SpellTradition.PRIMAL
        SpellcastingTradition.VARIABLE,
        SpellcastingTradition.OTHER,
        -> null
    }
}
