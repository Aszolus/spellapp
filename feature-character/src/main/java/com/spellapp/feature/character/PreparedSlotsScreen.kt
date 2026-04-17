package com.spellapp.feature.character

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.PreparedSlot
import com.spellapp.core.model.SpellSlotSummary
import com.spellapp.core.model.ordinalRank

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PreparedSlotsRoute(
    uiState: PreparedSlotsUiState,
    onTrackChange: (String) -> Unit,
    onChooseSpell: (Int, Int, String, String?) -> Unit,
    onClearSpell: (Int, Int) -> Unit,
    onCastSlot: (Int, Int) -> Unit,
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
    onManageKnownSpells: (String, String?, String?) -> Unit,
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
                item(key = "known-spells-section") {
                    KnownSpellsSection(
                        knownSpells = uiState.knownSpellSummaries,
                        selectedTrackKey = uiState.selectedTrackKey,
                        selectedTrackPreferredTradition = uiState.selectedTrackPreferredTradition,
                        selectedTrackSourceId = uiState.selectedTrackSourceId,
                        onManageKnownSpells = onManageKnownSpells,
                    )
                }

                slotsByRank.forEach { (rank, slots) ->
                    stickyHeader(key = "rank-header-$rank") {
                        RankSectionHeader(
                            rank = rank,
                            slots = slots,
                            effectiveCantripRank = uiState.effectiveCantripRank,
                        )
                    }
                    items(slots, key = { "${it.trackKey}-${it.rank}-${it.slotIndex}" }) { slot ->
                        CompactSlotRow(
                            slot = slot,
                            summary = slot.preparedSpellId?.let { uiState.spellSummaryById[it] },
                            effectiveCantripRank = uiState.effectiveCantripRank,
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
    selectedTrackKey: String,
    selectedTrackPreferredTradition: String?,
    selectedTrackSourceId: String?,
    onManageKnownSpells: (String, String?, String?) -> Unit,
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
                        )
                    },
                ) {
                    Text("Manage")
                }
            }
        }
    }
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
) {
    val rankLabel = if (rank == 0) "Cantrips" else "Rank $rank"
    val subtitle = if (rank == 0) {
        "Heightened to rank $effectiveCantripRank"
    } else {
        val unexpended = slots.count { it.preparedSpellId != null && !it.isExpended }
        "$unexpended/${slots.size} remaining"
    }

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
                Text(
                    text = rankLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    return when (sourceType) {
        CastingTrackSourceType.PRIMARY_CLASS -> "Primary"
        CastingTrackSourceType.ARCHETYPE -> sourceId.ifBlank { trackKey }
    }
}
