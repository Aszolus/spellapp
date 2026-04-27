package com.spellapp.feature.character.spellcasting.prepared

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spellapp.core.model.CastingStyle
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.HeightenTrigger
import com.spellapp.core.model.HeightenedEntry
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SpellAllowanceKind
import com.spellapp.core.model.SpellAllowancePolicy
import com.spellapp.core.model.SpellAllowanceSummary
import com.spellapp.core.model.SpellSlotSummary
import com.spellapp.core.model.heightenBonusDice
import com.spellapp.core.model.ordinalRank

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PreparedSlotsRoute(
    uiState: PreparedSlotsUiState,
    onTrackChange: (String) -> Unit,
    onChooseSpell: (Int, Int, String, String?) -> Unit,
    onClearSpell: (Int, Int) -> Unit,
    onCastSlot: (Int, Int) -> Unit,
    onCastKnownSpell: (String, Int) -> Unit,
    onUncastSlot: (Int, Int) -> Unit,
    onUseFocusPoint: () -> Unit,
    onIncreaseFocusMax: () -> Unit,
    onDecreaseFocusMax: () -> Unit,
    onRefocus: () -> Unit,
    onCastLayOnHands: () -> Unit,
    onRest: () -> Unit,
    onNewDayPreparation: () -> Unit,
    onPrepareRandom: () -> Unit,
    onUndoLastCast: () -> Unit,
    onManageKnownSpells: (String, String?, String?, Int?) -> Unit,
    onOpenSpellBrowser: () -> Unit,
    onOpenPreparedSpell: (String, Int) -> Unit,
    onBack: () -> Unit,
) {
    var pendingAction by remember { mutableStateOf<DayCycleAction?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showRecentActions by rememberSaveable { mutableStateOf(false) }
    var showRandomPrepareDialog by remember { mutableStateOf(false) }

    val slotsByRank = remember(uiState.allSlots) {
        uiState.allSlots
            .groupBy { it.rank }
            .toSortedMap()
    }
    val spontaneousRankKeys = remember(slotsByRank, uiState.knownSpellCastingSummaries) {
        (slotsByRank.keys + uiState.knownSpellCastingSummaries.map { it.knownRank })
            .toSortedSet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.characterName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenSpellBrowser) {
                        Text("Browse")
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rest") },
                                onClick = {
                                    showOverflowMenu = false
                                    pendingAction = DayCycleAction.REST
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("New Day") },
                                onClick = {
                                    showOverflowMenu = false
                                    pendingAction = DayCycleAction.NEW_DAY_PREPARATION
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Prepare Random") },
                                enabled = uiState.selectedTrackCastingStyle == CastingStyle.PREPARED,
                                onClick = {
                                    showOverflowMenu = false
                                    showRandomPrepareDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Focus Max +") },
                                enabled = uiState.focusMaxPoints < 3,
                                onClick = {
                                    showOverflowMenu = false
                                    onIncreaseFocusMax()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Focus Max \u2212") },
                                enabled = uiState.focusMaxPoints > 0,
                                onClick = {
                                    showOverflowMenu = false
                                    onDecreaseFocusMax()
                                },
                            )
                            if (uiState.canUndoLastCast) {
                                DropdownMenuItem(
                                    text = { Text("Undo Last Cast") },
                                    onClick = {
                                        showOverflowMenu = false
                                        onUndoLastCast()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            CombatStatsBar(
                spellDc = uiState.spellDc,
                spellAttackModifier = uiState.spellAttackModifier,
                focusCurrent = uiState.focusCurrentPoints,
                focusMax = uiState.focusMaxPoints,
                hasBlessedOneDedication = uiState.hasBlessedOneDedication,
                onRefocus = { pendingAction = DayCycleAction.REFOCUS },
                onUseFocusPoint = onUseFocusPoint,
                onCastLayOnHands = onCastLayOnHands,
            )

            if (uiState.castingTracks.size > 1) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.castingTracks, key = { it.trackKey }) { track ->
                        FilterChip(
                            selected = uiState.selectedTrackKey == track.trackKey,
                            onClick = { onTrackChange(track.trackKey) },
                            label = { Text(track.displayName()) },
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (uiState.selectedTrackCastingStyle == CastingStyle.SPONTANEOUS) {
                    item(key = "spontaneous-repertoire-actions") {
                        SpontaneousRepertoireActions(
                            knownSpellCount = uiState.knownSpellCastingSummaries.size,
                            selectedTrackKey = uiState.selectedTrackKey,
                            selectedTrackPreferredTradition = uiState.selectedTrackPreferredTradition,
                            selectedTrackSourceId = uiState.selectedTrackSourceId,
                            onManageKnownSpells = onManageKnownSpells,
                        )
                    }
                    spontaneousRankKeys.forEach { rank ->
                        val slots = slotsByRank[rank].orEmpty()
                        val knownSpellsForRank = repertoireSpellsForKnownRank(
                            spells = uiState.knownSpellCastingSummaries,
                            rank = rank,
                        )
                        val firstExpendedSlot = slots.firstOrNull { it.isExpended }
                        val allowanceSummary = uiState.allowanceSummaries.firstOrNull { summary ->
                            summary.kind == SpellAllowanceKind.REPERTOIRE && summary.rank == rank
                        }
                        stickyHeader(key = "rank-header-$rank") {
                            RankSectionHeader(
                                rank = rank,
                                slots = slots,
                                effectiveCantripRank = uiState.effectiveCantripRank,
                                castingStyle = uiState.selectedTrackCastingStyle,
                                allowanceSummary = allowanceSummary,
                                onAddKnownSpells = {
                                    onManageKnownSpells(
                                        uiState.selectedTrackKey,
                                        uiState.selectedTrackPreferredTradition,
                                        uiState.selectedTrackSourceId,
                                        rank,
                                    )
                                },
                                onRestoreExpendedSlot = firstExpendedSlot?.let { expendedSlot ->
                                    {
                                        onUncastSlot(
                                            expendedSlot.rank,
                                            expendedSlot.slotIndex,
                                        )
                                    }
                                },
                            )
                        }
                        if (shouldShowSpontaneousEmptyRankRow(
                                rank = rank,
                                slots = slots,
                                knownSpellsForRank = knownSpellsForRank,
                            )
                        ) {
                            item(key = "spontaneous-empty-rank-$rank") {
                                SpontaneousEmptyRankRow(rank)
                            }
                        } else {
                            items(
                                knownSpellsForRank,
                                key = { spell -> "known-${spell.knownSpellId}-slot-rank-$rank" },
                            ) { spell ->
                                SpontaneousKnownSpellRow(
                                    spell = spell,
                                    slotRank = rank,
                                    effectiveCantripRank = uiState.effectiveCantripRank,
                                    hasAvailableSlot = rank == 0 || slots.any { !it.isExpended },
                                    signatureSlotRankOptions = signatureSlotRankOptions(
                                        spell = spell,
                                        slotsByRank = slotsByRank,
                                    ),
                                    onCast = { slotRank -> onCastKnownSpell(spell.spellId, slotRank) },
                                    onOpenSpellDetail = { spellId, heightenedAt ->
                                        onOpenPreparedSpell(spellId, heightenedAt)
                                    },
                                    onOpenKnownRankSpellDetail = { spellId ->
                                        val heightenedAt = if (rank == 0) uiState.effectiveCantripRank else rank
                                        onOpenPreparedSpell(spellId, heightenedAt)
                                    },
                                )
                            }
                        }
                    }
                } else {
                    item(key = "known-spells-section") {
                        KnownSpellsSection(
                            knownSpells = uiState.knownSpellSummaries,
                            allowanceSummaries = uiState.allowanceSummaries,
                            selectedTrackKey = uiState.selectedTrackKey,
                            selectedTrackPreferredTradition = uiState.selectedTrackPreferredTradition,
                            selectedTrackSourceId = uiState.selectedTrackSourceId,
                            onManageKnownSpells = onManageKnownSpells,
                        )
                    }

                    slotsByRank.forEach { (rank, slots) ->
                        val allowanceSummary = uiState.allowanceSummaries.firstOrNull { summary ->
                            summary.kind == SpellAllowanceKind.PREPARED_SLOTS && summary.rank == rank
                        }
                        stickyHeader(key = "rank-header-$rank") {
                            RankSectionHeader(
                                rank = rank,
                                slots = slots,
                                effectiveCantripRank = uiState.effectiveCantripRank,
                                castingStyle = uiState.selectedTrackCastingStyle,
                                allowanceSummary = allowanceSummary,
                                onAddKnownSpells = {
                                    onManageKnownSpells(
                                        uiState.selectedTrackKey,
                                        uiState.selectedTrackPreferredTradition,
                                        uiState.selectedTrackSourceId,
                                        rank,
                                    )
                                },
                            )
                        }
                        items(slots, key = { "${it.trackKey}-${it.rank}-${it.slotIndex}" }) { slot ->
                            CompactSlotRow(
                                slot = slot,
                                summary = slot.preparedSpellId?.let { uiState.spellSummaryById[it] },
                                effectiveCantripRank = uiState.effectiveCantripRank,
                                castingStyle = uiState.selectedTrackCastingStyle,
                                onCast = { onCastSlot(slot.rank, slot.slotIndex) },
                                onUncast = { onUncastSlot(slot.rank, slot.slotIndex) },
                                onChooseSpell = {
                                    onChooseSpell(
                                        slot.rank,
                                        slot.slotIndex,
                                        slot.trackKey,
                                        uiState.selectedTrackPreferredTradition,
                                    )
                                },
                                onClearSpell = { onClearSpell(slot.rank, slot.slotIndex) },
                                onOpenSpellDetail = { spellId ->
                                    val heightenedAt = if (slot.rank == 0) uiState.effectiveCantripRank else slot.rank
                                    onOpenPreparedSpell(spellId, heightenedAt)
                                },
                            )
                        }
                    }
                }

                if (uiState.recentEventLines.isNotEmpty()) {
                    item(key = "recent-actions-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Recent Actions",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            TextButton(onClick = { showRecentActions = !showRecentActions }) {
                                Text(if (showRecentActions) "Hide" else "Show")
                            }
                        }
                    }
                    if (showRecentActions) {
                        items(uiState.recentEventLines.size, key = { "event-$it" }) { index ->
                            Text(
                                text = uiState.recentEventLines[index],
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }

        pendingAction?.let { action ->
            AlertDialog(
                onDismissRequest = { pendingAction = null },
                title = { Text(action.title) },
                text = { Text(action.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            when (action) {
                                DayCycleAction.REFOCUS -> onRefocus()
                                DayCycleAction.REST -> onRest()
                                DayCycleAction.NEW_DAY_PREPARATION -> onNewDayPreparation()
                            }
                            pendingAction = null
                        },
                    ) {
                        Text(action.confirmLabel)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingAction = null }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (showRandomPrepareDialog) {
            AlertDialog(
                onDismissRequest = { showRandomPrepareDialog = false },
                title = { Text("Prepare Random") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Fill empty slots using random known spells on this track.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Only empty slots are filled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onPrepareRandom()
                            showRandomPrepareDialog = false
                        },
                    ) {
                        Text("Prepare")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRandomPrepareDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KnownSpellsSection(
    knownSpells: List<SpellSlotSummary>,
    allowanceSummaries: List<SpellAllowanceSummary>,
    selectedTrackKey: String,
    selectedTrackPreferredTradition: String?,
    selectedTrackSourceId: String?,
    onManageKnownSpells: (String, String?, String?, Int?) -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val guidanceSummaries = allowanceSummaries.filter { summary ->
                summary.kind != SpellAllowanceKind.PREPARED_SLOTS
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Known Spells",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (knownSpells.isEmpty()) {
                            "None known on this track."
                        } else {
                            "${knownSpells.size} available to prepare."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = {
                        onManageKnownSpells(
                            selectedTrackKey,
                            selectedTrackPreferredTradition,
                            selectedTrackSourceId,
                            null,
                        )
                    },
                ) {
                    Text("Manage")
                }
            }
            if (guidanceSummaries.isNotEmpty()) {
                AllowanceSummaryLines(guidanceSummaries)
            }
        }
    }
}

@Composable
private fun SpontaneousRepertoireActions(
    knownSpellCount: Int,
    selectedTrackKey: String,
    selectedTrackPreferredTradition: String?,
    selectedTrackSourceId: String?,
    onManageKnownSpells: (String, String?, String?, Int?) -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Repertoire: $knownSpellCount known",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    onManageKnownSpells(
                        selectedTrackKey,
                        selectedTrackPreferredTradition,
                        selectedTrackSourceId,
                        null,
                    )
                },
                modifier = Modifier.height(28.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    text = "Manage",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SignatureCastSheet(
    spell: KnownSpellCastingSummary,
    rankPresentations: List<SignatureCastRankPresentation>,
    onCastKnownSpell: (Int) -> Unit,
    onOpenSpellDetail: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SignatureCastHeader(spell = spell)
            HorizontalDivider()
            Text(
                text = "Cast with slot",
                style = MaterialTheme.typography.titleSmall,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                rankPresentations.forEach { presentation ->
                    SignatureCastRankRow(
                        presentation = presentation,
                        onCastKnownSpell = onCastKnownSpell,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onOpenSpellDetail(spell.knownRank) },
                ) {
                    Text("Full details")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignatureCastHeader(spell: KnownSpellCastingSummary) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = spell.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = signatureRankSummary(spell),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            spell.signatureLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            signatureDetailFacts(spell).forEach { fact ->
                SignatureDetailFact(fact)
            }
        }

        if (spell.traits.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                spell.traits.forEach { trait ->
                    Text(
                        text = trait,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val preview = compactDescriptionPreview(spell.description)
        if (preview.isNotBlank()) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SignatureDetailFact(fact: SignatureSpellFact) {
    Text(
        text = "${fact.label}: ${fact.value}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SignatureCastRankRow(
    presentation: SignatureCastRankPresentation,
    onCastKnownSpell: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Rank ${presentation.rank}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${presentation.availableSlots}/${presentation.totalSlots} available",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (presentation.isAvailable) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            presentation.heighteningLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = { onCastKnownSpell(presentation.rank) },
            enabled = presentation.isAvailable,
        ) {
            Text("Cast", style = MaterialTheme.typography.labelMedium)
        }
    }
    HorizontalDivider()
}

@Composable
private fun AllowanceSummaryLines(
    summaries: List<SpellAllowanceSummary>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        summaries
            .filter { summary -> summary.expected != 0 || summary.policy == SpellAllowancePolicy.ALL_KNOWN }
            .take(8)
            .forEach { summary ->
                val warning = summary.warning
                Text(
                    text = warning ?: formatAllowanceSummary(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SpontaneousKnownSpellRow(
    spell: KnownSpellCastingSummary,
    slotRank: Int,
    effectiveCantripRank: Int,
    hasAvailableSlot: Boolean,
    signatureSlotRankOptions: List<SignatureSlotRankOption>,
    onCast: (Int) -> Unit,
    onOpenSpellDetail: (String, Int) -> Unit,
    onOpenKnownRankSpellDetail: (String) -> Unit,
) {
    var showCastSheet by remember { mutableStateOf(false) }
    val hasSignatureSlotChoices = spell.isSignature && spell.baseRank > 0
    val rankPresentations = remember(spell, signatureSlotRankOptions) {
        signatureCastRankPresentations(
            spell = spell,
            slotRankOptions = signatureSlotRankOptions,
        )
    }
    val hasAvailableSignatureSlot = rankPresentations.any { presentation -> presentation.isAvailable }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = { onOpenKnownRankSpellDetail(spell.spellId) },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = spell.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (spell.castTime.isNotBlank()) {
                    Text(
                        text = formatActionSymbols(spell.castTime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val rankLabel = spontaneousCastRankLabel(
                    spell = spell,
                    slotRank = slotRank,
                    effectiveCantripRank = effectiveCantripRank,
                )
                if (rankLabel != null) {
                    Text(
                        text = rankLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (spell.range.isNotBlank()) {
                    Text(
                        text = spell.range,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (hasSignatureSlotChoices) {
                        showCastSheet = true
                    } else {
                        onCast(slotRank)
                    }
                },
                enabled = if (hasSignatureSlotChoices) {
                    hasAvailableSignatureSlot
                } else {
                    hasAvailableSlot
                },
            ) {
                Text("Cast", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (spell.traits.isNotEmpty() || spell.signatureLabel != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                spell.signatureLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                spell.traits.forEach { trait ->
                    Text(
                        text = trait,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
    if (showCastSheet) {
        SignatureCastSheet(
            spell = spell,
            rankPresentations = rankPresentations,
            onCastKnownSpell = { castRank ->
                showCastSheet = false
                onCast(castRank)
            },
            onOpenSpellDetail = { detailRank ->
                showCastSheet = false
                onOpenSpellDetail(spell.spellId, detailRank)
            },
            onDismiss = { showCastSheet = false },
        )
    }
}

@Composable
private fun SpontaneousEmptyRankRow(rank: Int) {
    val label = if (rank == 0) {
        "No cantrips known."
    } else {
        "No known rank $rank spells."
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
}

@Composable
private fun CombatStatsBar(
    spellDc: Int,
    spellAttackModifier: Int,
    focusCurrent: Int,
    focusMax: Int,
    hasBlessedOneDedication: Boolean,
    onRefocus: () -> Unit,
    onUseFocusPoint: () -> Unit,
    onCastLayOnHands: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DC",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = "$spellDc",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Atk",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = spellAttackModifier.withSign(),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
            }

            if (focusMax > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = "Focus $focusCurrent of $focusMax points"
                        },
                    ) {
                        Text(
                            text = "Focus:",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        repeat(focusMax) { index ->
                            val isFilled = index < focusCurrent
                            Text(
                                text = if (isFilled) "\u25CF" else "\u25CB",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isFilled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                modifier = Modifier.clearAndSetSemantics { },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    if (focusCurrent > 0) {
                        TextButton(onClick = onUseFocusPoint) {
                            Text("Use")
                        }
                    }
                    if (hasBlessedOneDedication) {
                        TextButton(
                            onClick = onCastLayOnHands,
                            enabled = focusCurrent > 0,
                        ) {
                            Text("Lay on Hands")
                        }
                    }
                    TextButton(onClick = onRefocus) {
                        Text("Refocus")
                    }
                }
            }
        }
    }
}

@Composable
private fun RankSectionHeader(
    rank: Int,
    slots: List<PreparedSlot>,
    effectiveCantripRank: Int,
    castingStyle: CastingStyle,
    allowanceSummary: SpellAllowanceSummary? = null,
    onAddKnownSpells: (() -> Unit)? = null,
    onRestoreExpendedSlot: (() -> Unit)? = null,
) {
    val rankLabel = if (rank == 0) "Cantrips" else "Rank $rank"
    val slotSubtitle = if (rank == 0) {
        "heightened to rank $effectiveCantripRank"
    } else {
        val unexpended = if (castingStyle == CastingStyle.SPONTANEOUS) {
            slots.count { !it.isExpended }
        } else {
            slots.count { it.preparedSpellId != null && !it.isExpended }
        }
        "$unexpended/${slots.size} remaining"
    }
    val allowanceSubtitle = allowanceSummary?.let(::formatCompactAllowanceSummary)
    val subtitle = listOfNotNull(allowanceSubtitle, slotSubtitle)
        .joinToString(" - ")

    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = rankLabel,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onAddKnownSpells != null || onRestoreExpendedSlot != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onAddKnownSpells != null) {
                            TextButton(
                                onClick = onAddKnownSpells,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(
                                    text = "Add Spells",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        if (onRestoreExpendedSlot != null) {
                            TextButton(
                                onClick = onRestoreExpendedSlot,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Text(
                                    text = "Restore Slot",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
            allowanceSummary?.warning?.let { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CompactSlotRow(
    slot: PreparedSlot,
    summary: SpellSlotSummary?,
    effectiveCantripRank: Int,
    castingStyle: CastingStyle,
    onCast: () -> Unit,
    onUncast: () -> Unit,
    onChooseSpell: () -> Unit,
    onClearSpell: () -> Unit,
    onOpenSpellDetail: (String) -> Unit,
) {
    val isPrepared = slot.preparedSpellId != null
    val isExpended = slot.isExpended
    val rowAlpha = if (isExpended) 0.4f else 1f

    var showContextMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .semantics {
                if (isExpended) stateDescription = "expended"
            },
    ) {
        if (isPrepared && summary != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { onOpenSpellDetail(slot.preparedSpellId!!) },
                        onLongClick = { showContextMenu = true },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = summary.name,
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (isExpended) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (summary.castTime.isNotBlank()) {
                            Text(
                                text = formatActionSymbols(summary.castTime),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val rankLabel = when {
                            slot.rank == 0 -> "R$effectiveCantripRank"
                            slot.rank > summary.rank -> "${ordinalRank(slot.rank)} (+${slot.rank - summary.rank})"
                            else -> null
                        }
                        if (rankLabel != null) {
                            Text(
                                text = rankLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (summary.range.isNotBlank()) {
                            Text(
                                text = summary.range,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isExpended) {
                        OutlinedButton(onClick = onUncast) {
                            Text("Restore", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(onClick = onCast) {
                            Text("Cast", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (summary.traits.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        summary.traits.forEach { trait ->
                            Text(
                                text = trait,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Swap") },
                    onClick = {
                        showContextMenu = false
                        onChooseSpell()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Clear") },
                    onClick = {
                        showContextMenu = false
                        onClearSpell()
                    },
                )
            }
        } else if (castingStyle == CastingStyle.SPONTANEOUS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isExpended) "Expended spell slot" else "Available spell slot",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isExpended) {
                    OutlinedButton(onClick = onUncast) {
                        Text("Restore", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        role = Role.Button,
                        onClick = onChooseSpell,
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Empty \u2014 tap to prepare",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
}

private fun formatActionSymbols(castTime: String): String {
    val trimmed = castTime.trim().lowercase()
    return when {
        trimmed == "1" || trimmed == "1 action" -> "\u25C6"
        trimmed == "2" || trimmed == "2 actions" -> "\u25C6\u25C6"
        trimmed == "3" || trimmed == "3 actions" -> "\u25C6\u25C6\u25C6"
        trimmed == "free" || trimmed == "free action" -> "\u25C7"
        trimmed == "reaction" -> "\u21BA"
        else -> castTime
    }
}

private fun spontaneousCastRankLabel(
    spell: KnownSpellCastingSummary,
    slotRank: Int,
    effectiveCantripRank: Int,
): String? {
    return when {
        slotRank == 0 -> "R$effectiveCantripRank"
        spell.isSignature && slotRank > spell.knownRank -> "${ordinalRank(slotRank)} (+${slotRank - spell.knownRank})"
        spell.knownRank != spell.baseRank -> "Known ${ordinalRank(spell.knownRank)}"
        else -> null
    }
}

internal data class SignatureSlotRankOption(
    val rank: Int,
    val availableSlots: Int,
    val totalSlots: Int,
) {
    val hasAvailableSlot: Boolean
        get() = availableSlots > 0
}

internal data class SignatureCastRankPresentation(
    val rank: Int,
    val availableSlots: Int,
    val totalSlots: Int,
    val isAvailable: Boolean,
    val heighteningLines: List<String>,
)

private data class SignatureSpellFact(
    val label: String,
    val value: String,
)

internal fun repertoireSpellsForKnownRank(
    spells: List<KnownSpellCastingSummary>,
    rank: Int,
): List<KnownSpellCastingSummary> {
    return spells
        .filter { spell -> spell.knownRank == rank }
        .sortedWith(compareBy<KnownSpellCastingSummary> { it.knownRank }.thenBy { it.name })
}

internal fun signatureSlotRankOptions(
    spell: KnownSpellCastingSummary,
    slotsByRank: Map<Int, List<PreparedSlot>>,
): List<SignatureSlotRankOption> {
    return slotsByRank
        .filterKeys { rank -> rank > 0 && spell.canUseSlotRank(rank) }
        .toSortedMap()
        .map { (rank, slots) ->
            val availableSlots = slots.count { slot -> !slot.isExpended }
            SignatureSlotRankOption(
                rank = rank,
                availableSlots = availableSlots,
                totalSlots = slots.size,
            )
        }
}

internal fun signatureCastRankPresentations(
    spell: KnownSpellCastingSummary,
    slotsByRank: Map<Int, List<PreparedSlot>>,
): List<SignatureCastRankPresentation> {
    return signatureCastRankPresentations(
        spell = spell,
        slotRankOptions = signatureSlotRankOptions(
            spell = spell,
            slotsByRank = slotsByRank,
        ),
    )
}

internal fun signatureCastRankPresentations(
    spell: KnownSpellCastingSummary,
    slotRankOptions: List<SignatureSlotRankOption>,
): List<SignatureCastRankPresentation> {
    return slotRankOptions.map { option ->
        SignatureCastRankPresentation(
            rank = option.rank,
            availableSlots = option.availableSlots,
            totalSlots = option.totalSlots,
            isAvailable = option.hasAvailableSlot,
            heighteningLines = signatureHeighteningLines(
                spell = spell,
                slotRank = option.rank,
            ),
        )
    }
}

internal fun signatureHeighteningLines(
    spell: KnownSpellCastingSummary,
    slotRank: Int,
): List<String> {
    if (slotRank <= 0) {
        return emptyList()
    }
    val baseRank = if (spell.baseRank == 0) 1 else spell.baseRank
    val lines = mutableListOf<String>()
    when {
        slotRank == spell.baseRank -> lines += "Base casting"
        slotRank == spell.knownRank -> lines += "Known-rank casting"
    }
    heightenBonusDice(
        entries = spell.heightenedEntries,
        baseRank = baseRank,
        heightenedAt = slotRank,
    )?.let { bonusDice ->
        lines += "At ${ordinalRank(slotRank)}: $bonusDice"
    }
    val activeEntries = spell.heightenedEntries.filter { entry ->
        isHeightenedEntryActiveAtRank(
            entry = entry,
            baseRank = baseRank,
            heightenedAt = slotRank,
        )
    }
    activeEntries.forEach { entry ->
        val text = entry.text.trim()
        if (text.isNotBlank()) {
            lines += "${formatHeightenedTrigger(entry.trigger)}: $text"
        }
    }
    if (slotRank > spell.knownRank && activeEntries.isEmpty()) {
        lines += "No listed heightened entry"
    }
    return lines.distinct()
}

private fun isHeightenedEntryActiveAtRank(
    entry: HeightenedEntry,
    baseRank: Int,
    heightenedAt: Int,
): Boolean {
    return when (val trigger = entry.trigger) {
        is HeightenTrigger.Absolute -> heightenedAt >= trigger.rank
        is HeightenTrigger.Step -> heightenedAt >= baseRank + trigger.increment
    }
}

private fun formatHeightenedTrigger(trigger: HeightenTrigger): String {
    return when (trigger) {
        is HeightenTrigger.Absolute -> "Heightened (${ordinalRank(trigger.rank)})"
        is HeightenTrigger.Step -> "Heightened (+${trigger.increment})"
    }
}

private fun signatureRankSummary(spell: KnownSpellCastingSummary): String {
    val knownRank = if (spell.knownRank == 0) {
        "Cantrip"
    } else {
        "Known ${ordinalRank(spell.knownRank)}"
    }
    val baseRank = if (spell.baseRank == 0) {
        "base cantrip"
    } else {
        "base ${ordinalRank(spell.baseRank)}"
    }
    return if (spell.knownRank == spell.baseRank) {
        if (spell.knownRank == 0) "Cantrip" else "Rank ${spell.knownRank}"
    } else {
        "$knownRank - $baseRank"
    }
}

private fun signatureDetailFacts(spell: KnownSpellCastingSummary): List<SignatureSpellFact> {
    return buildList {
        if (spell.castTime.isNotBlank()) {
            add(SignatureSpellFact("Cast", formatActionSymbols(spell.castTime)))
        }
        if (spell.range.isNotBlank()) {
            add(SignatureSpellFact("Range", spell.range))
        }
        if (spell.area.isNotBlank()) {
            add(SignatureSpellFact("Area", spell.area))
        }
        if (spell.target.isNotBlank()) {
            add(SignatureSpellFact("Target", spell.target))
        }
        if (spell.defense.isNotBlank()) {
            add(SignatureSpellFact("Defense", spell.defense))
        }
        if (spell.duration.isNotBlank()) {
            add(SignatureSpellFact("Duration", spell.duration))
        }
    }
}

internal fun compactDescriptionPreview(
    description: String,
    maxCharacters: Int = 360,
): String {
    val compact = description
        .trim()
        .replace(Regex("\\s+"), " ")
    return if (compact.length <= maxCharacters) {
        compact
    } else {
        compact.take(maxCharacters).trimEnd() + "..."
    }
}

internal fun shouldShowSpontaneousEmptyRankRow(
    rank: Int,
    slots: List<PreparedSlot>,
    knownSpellsForRank: List<KnownSpellCastingSummary>,
): Boolean {
    return knownSpellsForRank.isEmpty() && (rank == 0 || slots.isEmpty())
}

private fun formatCompactAllowanceSummary(summary: SpellAllowanceSummary): String {
    val expected = summary.expected ?: return when (summary.policy) {
        SpellAllowancePolicy.ALL_KNOWN -> "all known spells are signature"
        else -> "${summary.label}: ${summary.actual}"
    }
    return when (summary.policy) {
        SpellAllowancePolicy.MINIMUM -> "${summary.label}: ${summary.actual} known, minimum $expected"
        SpellAllowancePolicy.ALL_KNOWN -> "all known spells are signature"
        else -> "${summary.label}: ${summary.actual}/$expected"
    }
}

private fun formatAllowanceSummary(summary: SpellAllowanceSummary): String {
    val expected = summary.expected
    return when {
        summary.policy == SpellAllowancePolicy.ALL_KNOWN -> summary.note ?: "All known spells are signature."
        expected == null -> "${summary.label}: ${summary.actual}"
        summary.policy == SpellAllowancePolicy.MINIMUM -> "${summary.label}: ${summary.actual} known, minimum $expected"
        summary.rank == null -> "${summary.label}: ${summary.actual}/$expected"
        summary.rank == 0 -> "${summary.label} cantrips: ${summary.actual}/$expected"
        else -> "${summary.label} rank ${summary.rank}: ${summary.actual}/$expected"
    }
}

private enum class DayCycleAction(
    val title: String,
    val message: String,
    val confirmLabel: String,
) {
    REFOCUS(
        title = "Refocus",
        message = "Recover 1 Focus Point, up to your Focus Pool maximum?",
        confirmLabel = "Refocus",
    ),
    REST(
        title = "Rest",
        message = "Set Focus Points to maximum for this character?",
        confirmLabel = "Rest",
    ),
    NEW_DAY_PREPARATION(
        title = "New Day Preparation",
        message = "Clear prepared spells for this track and restore focus to max?",
        confirmLabel = "Start New Day",
    ),
}

private fun CastingTrack.displayName(): String {
    if (displayName.isNotBlank()) {
        return displayName
    }
    return when (sourceType) {
        CastingTrackSourceType.PRIMARY_CLASS -> "Primary"
        CastingTrackSourceType.ARCHETYPE -> sourceId.ifBlank { trackKey }
    }
}

private fun Int.withSign(): String {
    return if (this >= 0) "+$this" else toString()
}
