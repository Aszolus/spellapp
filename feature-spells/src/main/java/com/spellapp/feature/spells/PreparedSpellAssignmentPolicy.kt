package com.spellapp.feature.spells

import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SpellListItem
import com.spellapp.core.rules.SpellTradition
import com.spellapp.core.rules.TrackSpellLegalityProfile
import com.spellapp.core.rules.parseSpellTraditions

data class PreparedSlotAssignmentContext(
    val characterClass: CharacterClass,
    val trackKey: String,
    val slotRank: Int,
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

class DefaultPreparedTrackLegalityProfileSource : PreparedTrackLegalityProfileSource {
    override fun profileFor(context: PreparedSlotAssignmentContext): TrackSpellLegalityProfile {
        val traditions = allowedTraditionsForTrack(context)
        return TrackSpellLegalityProfile(
            allowedTraditions = traditions,
        )
    }

    private fun allowedTraditionsForTrack(context: PreparedSlotAssignmentContext): Set<SpellTradition> {
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
        return when (characterClass) {
            CharacterClass.WIZARD -> SpellTradition.ARCANE
            CharacterClass.CLERIC -> SpellTradition.DIVINE
            CharacterClass.DRUID -> SpellTradition.PRIMAL
            CharacterClass.OTHER -> null
        }
    }

    private fun traditionForArchetype(archetypeId: String): SpellTradition? {
        return when (archetypeId.lowercase()) {
            "wizard" -> SpellTradition.ARCANE
            "cleric" -> SpellTradition.DIVINE
            "druid" -> SpellTradition.PRIMAL
            else -> null
        }
    }

    private companion object {
        private const val ARCHETYPE_TRACK_PREFIX = "archetype-"
    }
}
