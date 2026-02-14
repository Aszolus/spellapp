package com.spellapp.core.rules

import com.spellapp.core.model.CastingProgressionType
import com.spellapp.core.model.CharacterBuildOptionType
import com.spellapp.core.model.CharacterClass

data class RuleSourceRef(
    val optionType: CharacterBuildOptionType,
    val optionId: String,
    val label: String? = null,
)

enum class SpellcastingStyle {
    PREPARED,
    SPONTANEOUS,
    OTHER,
}

enum class SpellTradition {
    ARCANE,
    DIVINE,
    OCCULT,
    PRIMAL,
    OTHER,
}

enum class TrackSpellExceptionPolicy {
    EXPLICIT_ONLY,
    ALLOW_ANY,
}

data class TrackSpellLegalityProfile(
    val allowedTraditions: Set<SpellTradition>,
    val explicitlyAllowedSpellIds: Set<String> = emptySet(),
    val exceptionPolicy: TrackSpellExceptionPolicy = TrackSpellExceptionPolicy.EXPLICIT_ONLY,
) {
    fun isSpellLegal(spellId: String, spellTradition: SpellTradition): Boolean {
        if (exceptionPolicy == TrackSpellExceptionPolicy.ALLOW_ANY) {
            return true
        }
        if (spellId in explicitlyAllowedSpellIds) {
            return true
        }
        return spellTradition in allowedTraditions
    }

    companion object {
        fun defaultFor(tradition: SpellTradition): TrackSpellLegalityProfile {
            return TrackSpellLegalityProfile(
                allowedTraditions = setOf(tradition),
            )
        }
    }
}

object CastingWarningCodes {
    const val UNKNOWN_TRACK = "UNKNOWN_TRACK"
    const val INVALID_TRADITION_FOR_TRACK = "INVALID_TRADITION_FOR_TRACK"
}

data class BuildTrackDefinition(
    val trackKey: String,
    val progressionType: CastingProgressionType,
    val castingStyle: SpellcastingStyle,
    val tradition: SpellTradition,
    val source: RuleSourceRef,
    val legalityProfile: TrackSpellLegalityProfile = TrackSpellLegalityProfile.defaultFor(tradition),
)

data class CharacterBuildState(
    val characterId: Long,
    val level: Int,
    val primaryClass: CharacterClass,
    val baseTracks: List<BuildTrackDefinition>,
    val effects: List<RuleEffect> = emptyList(),
)

sealed interface RuleEffect {
    val source: RuleSourceRef
}

data class GrantSpellcastingTrackEffect(
    override val source: RuleSourceRef,
    val track: BuildTrackDefinition,
) : RuleEffect

data class GrantPreparedSlotsEffect(
    override val source: RuleSourceRef,
    val trackKey: String,
    val rank: Int,
    val slotCount: Int,
) : RuleEffect

data class AdjustSlotCountsEffect(
    override val source: RuleSourceRef,
    val trackKey: String,
    val adjustments: List<SlotCountAdjustment>,
) : RuleEffect

data class SlotCountAdjustment(
    val rank: Int,
    val delta: Int,
)

data class GrantCantripsEffect(
    override val source: RuleSourceRef,
    val trackKey: String,
    val count: Int,
) : RuleEffect

data class GrantPermanentPreparedSpellEffect(
    override val source: RuleSourceRef,
    val trackKey: String,
    val rank: Int,
    val spellId: String? = null,
    val selectorTag: String? = null,
) : RuleEffect

data class GrantTrackAllowedTraditionEffect(
    override val source: RuleSourceRef,
    val trackKey: String,
    val tradition: SpellTradition,
) : RuleEffect

data class GrantTrackSpellExceptionEffect(
    override val source: RuleSourceRef,
    val trackKey: String,
    val spellId: String,
) : RuleEffect

data class SetTrackSpellExceptionPolicyEffect(
    override val source: RuleSourceRef,
    val trackKey: String,
    val policy: TrackSpellExceptionPolicy,
) : RuleEffect

data class DerivedCastingState(
    val tracks: List<DerivedCastingTrackState>,
    val warnings: List<DerivationWarning>,
)

data class DerivedCastingTrackState(
    val trackKey: String,
    val progressionType: CastingProgressionType,
    val castingStyle: SpellcastingStyle,
    val tradition: SpellTradition,
    val slotCapsByRank: Map<Int, Int>,
    val grantedCantrips: Int,
    val permanentPreparedSpells: List<PermanentPreparedSpellGrant>,
    val legalityProfile: TrackSpellLegalityProfile,
    val contributingSources: Set<RuleSourceRef>,
)

data class PermanentPreparedSpellGrant(
    val rank: Int,
    val spellId: String? = null,
    val selectorTag: String? = null,
)

data class DerivationWarning(
    val code: String,
    val message: String,
    val source: RuleSourceRef? = null,
)

interface CastingStateDeriver {
    fun derive(buildState: CharacterBuildState): DerivedCastingState
}

class DefaultCastingStateDeriver : CastingStateDeriver {
    override fun derive(buildState: CharacterBuildState): DerivedCastingState {
        val warnings = mutableListOf<DerivationWarning>()
        val trackStateByKey = buildState.baseTracks
            .associate { it.trackKey to MutableTrackState.from(it) }
            .toMutableMap()

        buildState.effects.forEach { effect ->
            when (effect) {
                is GrantSpellcastingTrackEffect -> {
                    trackStateByKey[effect.track.trackKey] = MutableTrackState.from(effect.track)
                        .also { state -> state.contributingSources += effect.source }
                }
                is GrantPreparedSlotsEffect -> {
                    val track = trackStateByKey[effect.trackKey]
                    if (track == null) {
                        warnings += unknownTrackWarning(
                            trackKey = effect.trackKey,
                            source = effect.source,
                        )
                    } else {
                        track.slotCapsByRank[effect.rank] =
                            (track.slotCapsByRank[effect.rank] ?: 0) + effect.slotCount
                        track.contributingSources += effect.source
                    }
                }
                is AdjustSlotCountsEffect -> {
                    val track = trackStateByKey[effect.trackKey]
                    if (track == null) {
                        warnings += unknownTrackWarning(
                            trackKey = effect.trackKey,
                            source = effect.source,
                        )
                    } else {
                        effect.adjustments.forEach { adjustment ->
                            val current = track.slotCapsByRank[adjustment.rank] ?: 0
                            track.slotCapsByRank[adjustment.rank] = (current + adjustment.delta)
                                .coerceAtLeast(0)
                        }
                        track.contributingSources += effect.source
                    }
                }
                is GrantCantripsEffect -> {
                    val track = trackStateByKey[effect.trackKey]
                    if (track == null) {
                        warnings += unknownTrackWarning(
                            trackKey = effect.trackKey,
                            source = effect.source,
                        )
                    } else {
                        track.grantedCantrips = (track.grantedCantrips + effect.count).coerceAtLeast(0)
                        track.contributingSources += effect.source
                    }
                }
                is GrantPermanentPreparedSpellEffect -> {
                    val track = trackStateByKey[effect.trackKey]
                    if (track == null) {
                        warnings += unknownTrackWarning(
                            trackKey = effect.trackKey,
                            source = effect.source,
                        )
                    } else {
                        track.permanentPreparedSpells += PermanentPreparedSpellGrant(
                            rank = effect.rank,
                            spellId = effect.spellId,
                            selectorTag = effect.selectorTag,
                        )
                        track.contributingSources += effect.source
                    }
                }
                is GrantTrackAllowedTraditionEffect -> {
                    val track = trackStateByKey[effect.trackKey]
                    if (track == null) {
                        warnings += unknownTrackWarning(
                            trackKey = effect.trackKey,
                            source = effect.source,
                        )
                    } else {
                        track.allowedTraditions += effect.tradition
                        track.contributingSources += effect.source
                    }
                }
                is GrantTrackSpellExceptionEffect -> {
                    val track = trackStateByKey[effect.trackKey]
                    if (track == null) {
                        warnings += unknownTrackWarning(
                            trackKey = effect.trackKey,
                            source = effect.source,
                        )
                    } else {
                        track.explicitlyAllowedSpellIds += effect.spellId
                        track.contributingSources += effect.source
                    }
                }
                is SetTrackSpellExceptionPolicyEffect -> {
                    val track = trackStateByKey[effect.trackKey]
                    if (track == null) {
                        warnings += unknownTrackWarning(
                            trackKey = effect.trackKey,
                            source = effect.source,
                        )
                    } else {
                        track.exceptionPolicy = effect.policy
                        track.contributingSources += effect.source
                    }
                }
            }
        }

        val tracks = trackStateByKey.values
            .sortedBy { it.trackKey }
            .map { state ->
                DerivedCastingTrackState(
                    trackKey = state.trackKey,
                    progressionType = state.progressionType,
                    castingStyle = state.castingStyle,
                    tradition = state.tradition,
                    slotCapsByRank = state.slotCapsByRank
                        .toSortedMap()
                        .filterValues { count -> count > 0 },
                    grantedCantrips = state.grantedCantrips,
                    permanentPreparedSpells = state.permanentPreparedSpells.toList(),
                    legalityProfile = TrackSpellLegalityProfile(
                        allowedTraditions = state.allowedTraditions.toSet(),
                        explicitlyAllowedSpellIds = state.explicitlyAllowedSpellIds.toSet(),
                        exceptionPolicy = state.exceptionPolicy,
                    ),
                    contributingSources = state.contributingSources.toSet(),
                )
            }

        return DerivedCastingState(
            tracks = tracks,
            warnings = warnings.toList(),
        )
    }

    private fun unknownTrackWarning(
        trackKey: String,
        source: RuleSourceRef,
    ): DerivationWarning {
        return DerivationWarning(
            code = CastingWarningCodes.UNKNOWN_TRACK,
            message = "Rule effect referenced unknown track '$trackKey'.",
            source = source,
        )
    }
}

private data class MutableTrackState(
    val trackKey: String,
    var progressionType: CastingProgressionType,
    var castingStyle: SpellcastingStyle,
    var tradition: SpellTradition,
    val slotCapsByRank: MutableMap<Int, Int> = mutableMapOf(),
    var grantedCantrips: Int = 0,
    val permanentPreparedSpells: MutableList<PermanentPreparedSpellGrant> = mutableListOf(),
    val allowedTraditions: MutableSet<SpellTradition> = mutableSetOf(),
    val explicitlyAllowedSpellIds: MutableSet<String> = mutableSetOf(),
    var exceptionPolicy: TrackSpellExceptionPolicy = TrackSpellExceptionPolicy.EXPLICIT_ONLY,
    val contributingSources: MutableSet<RuleSourceRef> = mutableSetOf(),
) {
    companion object {
        fun from(definition: BuildTrackDefinition): MutableTrackState {
            return MutableTrackState(
                trackKey = definition.trackKey,
                progressionType = definition.progressionType,
                castingStyle = definition.castingStyle,
                tradition = definition.tradition,
                allowedTraditions = definition.legalityProfile.allowedTraditions.toMutableSet(),
                explicitlyAllowedSpellIds = definition.legalityProfile.explicitlyAllowedSpellIds.toMutableSet(),
                exceptionPolicy = definition.legalityProfile.exceptionPolicy,
                contributingSources = mutableSetOf(definition.source),
            )
        }
    }
}
