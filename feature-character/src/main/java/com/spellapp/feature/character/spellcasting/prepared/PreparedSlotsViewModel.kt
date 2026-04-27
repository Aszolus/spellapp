package com.spellapp.feature.character.spellcasting.prepared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spellapp.core.data.CastingTrackRepository
import com.spellapp.core.data.CharacterBuildRepository
import com.spellapp.core.data.CharacterCrudRepository
import com.spellapp.core.data.FocusStateRepository
import com.spellapp.core.data.KnownSpellRepository
import com.spellapp.core.data.PreparedSlotRepository
import com.spellapp.core.data.PreparedSlotSyncRepository
import com.spellapp.core.data.SessionEventRepository
import com.spellapp.core.data.SpellRepository
import com.spellapp.core.model.CastingStyle
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.ClassSpellcastingCatalogSource
import com.spellapp.core.model.EmptyClassSpellcastingCatalogSource
import com.spellapp.core.model.HeightenedEntry
import com.spellapp.core.model.KnownSpell
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SessionEventType
import com.spellapp.core.model.SpellAllowanceKind
import com.spellapp.core.model.SpellAllowancePolicy
import com.spellapp.core.model.SpellAllowanceSummary
import com.spellapp.core.model.SpellSlotSummary
import com.spellapp.core.model.allowanceRulesForTrack
import com.spellapp.core.model.buildSpellAllowanceSummaries
import com.spellapp.core.model.effectiveCantripRank
import com.spellapp.core.model.preferredSpellTradition
import com.spellapp.feature.character.spellcasting.CastLayOnHandsUseCase
import com.spellapp.feature.character.spellcasting.SpellcastingSupportService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreparedSlotsUiState(
    val characterName: String = "Character",
    val characterLevel: Int = 1,
    val effectiveCantripRank: Int = 1,
    val spellDc: Int = 0,
    val spellAttackModifier: Int = 0,
    val selectedTrackKey: String = PreparedSlot.PRIMARY_TRACK_KEY,
    val castingTracks: List<CastingTrack> = emptyList(),
    val selectedTrackCastingStyle: CastingStyle = CastingStyle.PREPARED,
    val selectedTrackPreferredTradition: String? = null,
    val selectedTrackSourceId: String? = null,
    val allSlots: List<PreparedSlot> = emptyList(),
    val knownSpellSummaries: List<SpellSlotSummary> = emptyList(),
    val knownSpellCastingSummaries: List<KnownSpellCastingSummary> = emptyList(),
    val allowanceSummaries: List<SpellAllowanceSummary> = emptyList(),
    val spellSummaryById: Map<String, SpellSlotSummary> = emptyMap(),
    val focusCurrentPoints: Int = 0,
    val focusMaxPoints: Int = 1,
    val recentEventLines: List<String> = emptyList(),
    val canUndoLastCast: Boolean = false,
    val hasBlessedOneDedication: Boolean = false,
)

data class KnownSpellCastingSummary(
    val knownSpellId: Long,
    val spellId: String,
    val name: String,
    val baseRank: Int,
    val knownRank: Int,
    val isSignature: Boolean,
    val signatureLabel: String? = null,
    val castTime: String,
    val range: String,
    val area: String = "",
    val target: String = "",
    val defense: String = "",
    val duration: String = "",
    val description: String = "",
    val traits: List<String>,
    val heightenedEntries: List<HeightenedEntry> = emptyList(),
) {
    fun canUseSlotRank(slotRank: Int): Boolean {
        return knownSpellCanUseSlotRank(
            baseRank = baseRank,
            knownRank = knownRank,
            isSignature = isSignature,
            slotRank = slotRank,
        )
    }
}

private data class SlotContext(
    val selectedTrackKey: String,
    val castingTracks: List<CastingTrack>,
    val allSlots: List<PreparedSlot>,
    val knownSpells: List<KnownSpell>,
)

private data class EventContext(
    val spellSummaryById: Map<String, SpellSlotSummary>,
    val recentEventLines: List<String>,
    val canUndoLastCast: Boolean,
)

private data class CharacterContext(
    val characterName: String,
    val characterLevel: Int,
    val spellDc: Int,
    val spellAttackModifier: Int,
)

private data class UiMetaContext(
    val focusCurrentPoints: Int,
    val focusMaxPoints: Int,
    val hasBlessedOneDedication: Boolean,
    val characterName: String,
    val characterLevel: Int,
    val spellDc: Int,
    val spellAttackModifier: Int,
)

class PreparedSlotsViewModel(
    private val characterId: Long,
    private val preparedSlotsService: PreparedSlotsService,
    private val spellcastingSupportService: SpellcastingSupportService,
    private val castLayOnHandsUseCase: CastLayOnHandsUseCase,
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        EmptyClassSpellcastingCatalogSource,
) : ViewModel() {
    private val selectedTrackKey = MutableStateFlow(PreparedSlot.PRIMARY_TRACK_KEY)
    private val characterProfile = MutableStateFlow(
        CharacterContext(
            characterName = "Character",
            characterLevel = 1,
            spellDc = 0,
            spellAttackModifier = 0,
        )
    )
    private val castingTracks = spellcastingSupportService.observeCastingTracks(characterId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeTrackKey = combine(
        selectedTrackKey,
        castingTracks,
    ) { selected, tracks ->
        if (tracks.any { it.trackKey == selected }) {
            selected
        } else {
            tracks.firstOrNull()?.trackKey ?: PreparedSlot.PRIMARY_TRACK_KEY
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreparedSlot.PRIMARY_TRACK_KEY,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val preparedSlots = activeTrackKey.flatMapLatest { trackKey ->
        preparedSlotsService.observePreparedSlots(
            characterId = characterId,
            trackKey = trackKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val knownSpells = activeTrackKey.flatMapLatest { trackKey ->
        spellcastingSupportService.observeKnownSpells(
            characterId = characterId,
            trackKey = trackKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sessionEvents = activeTrackKey.flatMapLatest { trackKey ->
        spellcastingSupportService.observeSessionEvents(
            characterId = characterId,
            trackKey = trackKey,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val focusState = spellcastingSupportService.observeFocusState(characterId)
    private val hasBlessedOneDedication = spellcastingSupportService.observeHasBlessedOneDedication(characterId)
    private val uiMetaContext = combine(
        focusState,
        hasBlessedOneDedication,
        characterProfile,
    ) { focus, blessedOne, character ->
        UiMetaContext(
            focusCurrentPoints = focus.currentPoints,
            focusMaxPoints = focus.maxPoints,
            hasBlessedOneDedication = blessedOne,
            characterName = character.characterName,
            characterLevel = character.characterLevel,
            spellDc = character.spellDc,
            spellAttackModifier = character.spellAttackModifier,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val spellSummaryById = combine(preparedSlots, sessionEvents, knownSpells) { slots, events, known ->
        buildSet {
            addAll(slots.mapNotNull { it.preparedSpellId })
            addAll(events.mapNotNull { it.spellId })
            addAll(known.map { it.spellId })
        }
    }.mapLatest { spellIds ->
        spellcastingSupportService.resolveSpellSummaries(spellIds)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap(),
    )

    private val slotContext = combine(
        activeTrackKey,
        castingTracks,
        preparedSlots,
        knownSpells,
    ) { trackKey, tracks, slots, known ->
        val sortedSlots = slots.sortedWith(
            compareBy<PreparedSlot> { it.rank }.thenBy { it.slotIndex },
        )
        SlotContext(
            selectedTrackKey = trackKey,
            castingTracks = tracks,
            allSlots = sortedSlots,
            knownSpells = known.sortedBy { it.spellId },
        )
    }

    private val eventContext = combine(
        sessionEvents,
        spellSummaryById,
    ) { events, summaries ->
        EventContext(
            spellSummaryById = summaries,
            recentEventLines = spellcastingSupportService.formatRecentEventLines(
                sessionEvents = events,
                spellSummaryById = summaries,
            ),
            canUndoLastCast = events.any { event -> event.type == SessionEventType.CAST_SPELL },
        )
    }

    val uiState = combine(
        slotContext,
        eventContext,
        uiMetaContext,
    ) { slots, events, meta ->
        val selectedTrack = slots.castingTracks
            .firstOrNull { track -> track.trackKey == slots.selectedTrackKey }
        val knownSpellBaseRanksById = events.spellSummaryById.mapValues { (_, summary) -> summary.rank }
        val allowanceRules = selectedTrack?.let { track ->
            classSpellcastingCatalogSource.allowanceRulesForTrack(
                trackKey = track.trackKey,
                sourceId = track.sourceId,
            )
        }.orEmpty()
        val treatsAllKnownSpellsAsSignature = allowanceRules.any { rule ->
            rule.kind == SpellAllowanceKind.SIGNATURE_SPELLS &&
                rule.policy == SpellAllowancePolicy.ALL_KNOWN
        }
        PreparedSlotsUiState(
            characterName = meta.characterName,
            characterLevel = meta.characterLevel,
            effectiveCantripRank = effectiveCantripRank(meta.characterLevel),
            spellDc = meta.spellDc,
            spellAttackModifier = meta.spellAttackModifier,
            selectedTrackKey = slots.selectedTrackKey,
            castingTracks = slots.castingTracks,
            selectedTrackCastingStyle = slots.castingTracks
                .firstOrNull { track -> track.trackKey == slots.selectedTrackKey }
                ?.castingStyle
                ?: CastingStyle.PREPARED,
            selectedTrackPreferredTradition = slots.castingTracks
                .firstOrNull { track -> track.trackKey == slots.selectedTrackKey }
                ?.preferredSpellTradition(),
            selectedTrackSourceId = slots.castingTracks
                .firstOrNull { track -> track.trackKey == slots.selectedTrackKey }
                ?.sourceId,
            allSlots = slots.allSlots,
            knownSpellSummaries = slots.knownSpells.mapNotNull { knownSpell ->
                events.spellSummaryById[knownSpell.spellId]
            }.sortedWith(
                compareBy<SpellSlotSummary> { it.rank }.thenBy { it.name },
            ),
            knownSpellCastingSummaries = slots.knownSpells.mapNotNull { knownSpell ->
                events.spellSummaryById[knownSpell.spellId]?.let { summary ->
                    KnownSpellCastingSummary(
                        knownSpellId = knownSpell.id,
                        spellId = knownSpell.spellId,
                        name = summary.name,
                        baseRank = summary.rank,
                        knownRank = knownSpell.knownRank ?: summary.rank,
                        isSignature = knownSpell.isSignature || treatsAllKnownSpellsAsSignature,
                        signatureLabel = signatureLabelForKnownSpell(
                            baseRank = summary.rank,
                            isExplicitSignature = knownSpell.isSignature,
                            treatsAllKnownSpellsAsSignature = treatsAllKnownSpellsAsSignature,
                        ),
                        castTime = summary.castTime,
                        range = summary.range,
                        area = summary.area,
                        target = summary.target,
                        defense = summary.defense,
                        duration = summary.duration,
                        description = summary.description,
                        traits = summary.traits,
                        heightenedEntries = summary.heightenedEntries,
                    )
                }
            }.sortedWith(
                compareBy<KnownSpellCastingSummary> { it.knownRank }.thenBy { it.name },
            ),
            allowanceSummaries = buildSpellAllowanceSummaries(
                rules = allowanceRules,
                characterLevel = meta.characterLevel,
                knownSpells = slots.knownSpells,
                knownSpellBaseRanksById = knownSpellBaseRanksById,
                preparedSlots = slots.allSlots,
            ),
            spellSummaryById = events.spellSummaryById,
            focusCurrentPoints = meta.focusCurrentPoints,
            focusMaxPoints = meta.focusMaxPoints,
            recentEventLines = events.recentEventLines,
            canUndoLastCast = events.canUndoLastCast,
            hasBlessedOneDedication = meta.hasBlessedOneDedication,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PreparedSlotsUiState(),
    )

    init {
        viewModelScope.launch {
            preparedSlotsService.syncPreparedSlots(characterId)
        }
        viewModelScope.launch {
            spellcastingSupportService.getCharacterProfile(characterId)?.let { profile ->
                characterProfile.value = CharacterContext(
                    characterName = profile.name,
                    characterLevel = profile.level,
                    spellDc = profile.spellDc,
                    spellAttackModifier = profile.spellAttackModifier,
                )
            }
        }
    }

    fun onTrackChange(trackKey: String) {
        selectedTrackKey.update { trackKey }
    }

    fun clearSpell(rank: Int, slotIndex: Int) {
        viewModelScope.launch {
            preparedSlotsService.clearSpell(
                characterId = characterId,
                rank = rank,
                slotIndex = slotIndex,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun castSlot(rank: Int, slotIndex: Int) {
        viewModelScope.launch {
            preparedSlotsService.castSlot(
                characterId = characterId,
                rank = rank,
                slotIndex = slotIndex,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun castKnownSpell(spellId: String, slotRank: Int) {
        val slot = compatibleKnownSpellSlot(
            spellId = spellId,
            slotRank = slotRank,
        ) ?: return
        viewModelScope.launch {
            preparedSlotsService.castKnownSpell(
                characterId = characterId,
                trackKey = activeTrackKey.value,
                spellId = spellId,
                slotRank = slot.rank,
                slotIndex = slot.slotIndex,
            )
        }
    }

    fun uncastSlot(rank: Int, slotIndex: Int) {
        viewModelScope.launch {
            preparedSlotsService.uncastSlot(
                characterId = characterId,
                rank = rank,
                slotIndex = slotIndex,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun undoLastCast() {
        viewModelScope.launch {
            preparedSlotsService.undoLastCast(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun useFocusPoint() {
        viewModelScope.launch {
            spellcastingSupportService.useFocusPoint(characterId)
        }
    }

    fun increaseFocusMax() {
        viewModelScope.launch {
            spellcastingSupportService.increaseFocusMax(characterId)
        }
    }

    fun decreaseFocusMax() {
        viewModelScope.launch {
            spellcastingSupportService.decreaseFocusMax(characterId)
        }
    }

    fun refocus() {
        viewModelScope.launch {
            spellcastingSupportService.refocus(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun castLayOnHands() {
        viewModelScope.launch {
            castLayOnHandsUseCase.cast(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun rest() {
        viewModelScope.launch {
            spellcastingSupportService.rest(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun newDayPreparation() {
        viewModelScope.launch {
            preparedSlotsService.newDayPreparation(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    fun prepareRandom() {
        viewModelScope.launch {
            preparedSlotsService.prepareRandom(
                characterId = characterId,
                trackKey = activeTrackKey.value,
            )
        }
    }

    private fun compatibleKnownSpellSlot(
        spellId: String,
        slotRank: Int,
    ): PreparedSlot? {
        val summary = spellSummaryById.value[spellId] ?: return null
        val treatsAllKnownSpellsAsSignature = activeTrackTreatsAllKnownSpellsAsSignature()
        val hasCompatibleKnownSpell = knownSpells.value
            .filter { knownSpell -> knownSpell.spellId == spellId }
            .any { knownSpell ->
                knownSpellCanUseSlotRank(
                    baseRank = summary.rank,
                    knownRank = knownSpell.knownRank ?: summary.rank,
                    isSignature = knownSpell.isSignature || treatsAllKnownSpellsAsSignature,
                    slotRank = slotRank,
                )
            }
        if (!hasCompatibleKnownSpell) {
            return null
        }
        return preparedSlots.value
            .asSequence()
            .filter { slot -> slot.trackKey == activeTrackKey.value }
            .filter { slot -> slot.rank == slotRank }
            .filter { slot -> slot.rank == 0 || !slot.isExpended }
            .sortedBy { it.slotIndex }
            .firstOrNull()
    }

    private fun activeTrackTreatsAllKnownSpellsAsSignature(): Boolean {
        val trackKey = activeTrackKey.value
        val track = castingTracks.value.firstOrNull { candidate -> candidate.trackKey == trackKey }
            ?: return false
        return classSpellcastingCatalogSource.allowanceRulesForTrack(
            trackKey = track.trackKey,
            sourceId = track.sourceId,
        ).any { rule ->
            rule.kind == SpellAllowanceKind.SIGNATURE_SPELLS &&
                rule.policy == SpellAllowancePolicy.ALL_KNOWN
        }
    }
}

private fun signatureLabelForKnownSpell(
    baseRank: Int,
    isExplicitSignature: Boolean,
    treatsAllKnownSpellsAsSignature: Boolean,
): String? {
    if (baseRank == 0) {
        return null
    }
    return when {
        treatsAllKnownSpellsAsSignature -> "Always signature"
        isExplicitSignature -> "Signature"
        else -> null
    }
}

internal fun knownSpellCanUseSlotRank(
    baseRank: Int,
    knownRank: Int,
    isSignature: Boolean,
    slotRank: Int,
): Boolean {
    return when {
        baseRank == 0 -> slotRank == 0
        slotRank == 0 -> false
        isSignature -> slotRank >= knownRank
        else -> slotRank == knownRank
    }
}

class PreparedSlotsViewModelFactory(
    private val characterId: Long,
    private val preparedSlotRepository: PreparedSlotRepository,
    private val castingTrackRepository: CastingTrackRepository,
    private val preparedSlotSyncRepository: PreparedSlotSyncRepository,
    private val sessionEventRepository: SessionEventRepository,
    private val focusStateRepository: FocusStateRepository,
    private val knownSpellRepository: KnownSpellRepository,
    private val spellRepository: SpellRepository,
    private val characterCrudRepository: CharacterCrudRepository,
    private val characterBuildRepository: CharacterBuildRepository,
    private val classSpellcastingCatalogSource: ClassSpellcastingCatalogSource =
        EmptyClassSpellcastingCatalogSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(PreparedSlotsViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel class: ${modelClass.name}")
        }
        val spellcastingSupportService = SpellcastingSupportService(
            castingTrackRepository = castingTrackRepository,
            sessionEventRepository = sessionEventRepository,
            focusStateRepository = focusStateRepository,
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
            characterCrudRepository = characterCrudRepository,
            characterBuildRepository = characterBuildRepository,
        )
        val preparedSlotsService = PreparedSlotsService(
            preparedSlotRepository = preparedSlotRepository,
            castingTrackRepository = castingTrackRepository,
            preparedSlotSyncRepository = preparedSlotSyncRepository,
            focusStateRepository = focusStateRepository,
            sessionEventRepository = sessionEventRepository,
            knownSpellRepository = knownSpellRepository,
            spellRepository = spellRepository,
            spellcastingSupportService = spellcastingSupportService,
        )
        val castLayOnHandsUseCase = CastLayOnHandsUseCase(
            characterBuildRepository = characterBuildRepository,
            focusStateRepository = focusStateRepository,
            sessionEventRepository = sessionEventRepository,
            spellcastingSupportService = spellcastingSupportService,
        )
        return PreparedSlotsViewModel(
            characterId = characterId,
            preparedSlotsService = preparedSlotsService,
            spellcastingSupportService = spellcastingSupportService,
            castLayOnHandsUseCase = castLayOnHandsUseCase,
            classSpellcastingCatalogSource = classSpellcastingCatalogSource,
        ) as T
    }
}
