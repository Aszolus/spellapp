package com.spellapp.feature.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.spellapp.core.model.CastingTrack
import com.spellapp.core.model.CastingTrackSourceType
import com.spellapp.core.model.PreparedSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparedSlotsRoute(
    characterName: String,
    selectedRank: Int,
    selectedTrackKey: String,
    castingTracks: List<CastingTrack>,
    allSlots: List<PreparedSlot>,
    slotsForRank: List<PreparedSlot>,
    spellNameById: Map<String, String>,
    onTrackChange: (String) -> Unit,
    onRankChange: (Int) -> Unit,
    onChooseSpell: (Int, Int, String) -> Unit,
    onClearSpell: (Int, Int) -> Unit,
    onCastSlot: (Int, Int) -> Unit,
    focusCurrentPoints: Int,
    focusMaxPoints: Int,
    onUseFocusPoint: () -> Unit,
    onIncreaseFocusMax: () -> Unit,
    onDecreaseFocusMax: () -> Unit,
    onRefocus: () -> Unit,
    onRest: () -> Unit,
    onNewDayPreparation: () -> Unit,
    canUndoLastCast: Boolean,
    onUndoLastCast: () -> Unit,
    onOpenSpellBrowser: () -> Unit,
    onOpenPreparedSpell: (String) -> Unit,
    recentEvents: List<String>,
    onBack: () -> Unit,
) {
    var pendingAction by remember { mutableStateOf<DayCycleAction?>(null) }
    var showAllRanks by rememberSaveable { mutableStateOf(true) }
    var showOnlyPrepared by rememberSaveable { mutableStateOf(true) }
    var showSessionTools by rememberSaveable { mutableStateOf(false) }
    var showRecentActions by rememberSaveable { mutableStateOf(false) }
    val baseSlots = if (showAllRanks) allSlots else slotsForRank
    val visibleSlots = if (showOnlyPrepared) {
        baseSlots.filter { slot -> slot.preparedSpellId != null }
    } else {
        baseSlots
    }
    val preparedCount = baseSlots.count { slot -> slot.preparedSpellId != null }
    val rankLabel = if (selectedRank == 0) "Cantrips" else "Rank $selectedRank"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$characterName Preparation") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(onClick = onOpenSpellBrowser) {
                        Text("Browse")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (castingTracks.isNotEmpty()) {
                Text(
                    text = "Track",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(castingTracks, key = { it.trackKey }) { track ->
                        FilterChip(
                            selected = selectedTrackKey == track.trackKey,
                            onClick = { onTrackChange(track.trackKey) },
                            label = { Text(track.displayName()) },
                        )
                    }
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = showAllRanks,
                        onClick = { showAllRanks = true },
                        label = { Text("All Ranks") },
                    )
                }
                item {
                    FilterChip(
                        selected = !showAllRanks,
                        onClick = { showAllRanks = false },
                        label = { Text("Selected Rank") },
                    )
                }
                item {
                    FilterChip(
                        selected = showOnlyPrepared,
                        onClick = { showOnlyPrepared = true },
                        label = { Text("Prepared Only") },
                    )
                }
                item {
                    FilterChip(
                        selected = !showOnlyPrepared,
                        onClick = { showOnlyPrepared = false },
                        label = { Text("All Slots") },
                    )
                }
            }

            if (!showAllRanks) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items((0..10).toList(), key = { it }) { rank ->
                        FilterChip(
                            selected = selectedRank == rank,
                            onClick = { onRankChange(rank) },
                            label = {
                                Text(if (rank == 0) "Cantrip" else "Rank $rank")
                            },
                        )
                    }
                }
            }

            Text(
                text = if (showAllRanks) {
                    "Prepared $preparedCount / ${baseSlots.size}"
                } else {
                    "$rankLabel prepared $preparedCount / ${baseSlots.size}"
                },
                style = MaterialTheme.typography.bodyLarge,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Focus: $focusCurrentPoints / $focusMaxPoints",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AssistChip(
                    onClick = { showSessionTools = !showSessionTools },
                    label = { Text(if (showSessionTools) "Hide Session Tools" else "Session Tools") },
                )
            }

            if (showSessionTools) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = onDecreaseFocusMax,
                                enabled = focusMaxPoints > 0,
                            ) {
                                Text("Max -")
                            }
                            TextButton(
                                onClick = onIncreaseFocusMax,
                                enabled = focusMaxPoints < 3,
                            ) {
                                Text("Max +")
                            }
                            Button(
                                onClick = onUseFocusPoint,
                                enabled = focusCurrentPoints > 0,
                            ) {
                                Text("Use Focus")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Button(onClick = { pendingAction = DayCycleAction.REFOCUS }) {
                                Text("Refocus")
                            }
                            Button(onClick = { pendingAction = DayCycleAction.REST }) {
                                Text("Rest")
                            }
                            Button(onClick = { pendingAction = DayCycleAction.NEW_DAY_PREPARATION }) {
                                Text("New Day")
                            }
                        }
                    }
                }
            }

            if (visibleSlots.isEmpty()) {
                Text(
                    text = if (showOnlyPrepared) {
                        "No prepared spells in this view."
                    } else {
                        "No slots available at this level for this track."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(visibleSlots, key = { "${it.trackKey}-${it.rank}-${it.slotIndex}" }) { slot ->
                        val slotTitle = if (slot.rank == 0) {
                            "Cantrip ${slot.slotIndex + 1}"
                        } else {
                            "Rank ${slot.rank} Slot ${slot.slotIndex + 1}"
                        }
                        val preparedName = slot.preparedSpellId?.let { spellId ->
                            spellNameById[spellId] ?: spellId
                        }
                        val preparedSpellId = slot.preparedSpellId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        text = slotTitle,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    if (preparedSpellId != null && preparedName != null) {
                                        TextButton(
                                            onClick = { onOpenPreparedSpell(preparedSpellId) },
                                            modifier = Modifier.padding(0.dp),
                                        ) {
                                            Text(text = preparedName)
                                        }
                                    } else {
                                        Text(
                                            text = "Empty",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                if (slot.isExpended) {
                                    Text(
                                        text = "Expended",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (slot.preparedSpellId == null) {
                                    TextButton(
                                        onClick = {
                                            onChooseSpell(slot.rank, slot.slotIndex, slot.trackKey)
                                        },
                                    ) {
                                        Text("Choose")
                                    }
                                } else {
                                    TextButton(
                                        onClick = { onClearSpell(slot.rank, slot.slotIndex) },
                                    ) {
                                        Text("Clear")
                                    }
                                    TextButton(
                                        onClick = {
                                            onChooseSpell(slot.rank, slot.slotIndex, slot.trackKey)
                                        },
                                    ) {
                                        Text("Swap")
                                    }
                                    Button(
                                        onClick = { onCastSlot(slot.rank, slot.slotIndex) },
                                        enabled = !slot.isExpended,
                                    ) {
                                        Text("Cast")
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (recentEvents.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent Actions (${recentEvents.size})",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showRecentActions = !showRecentActions }) {
                            Text(if (showRecentActions) "Hide" else "Show")
                        }
                        TextButton(
                            onClick = onUndoLastCast,
                            enabled = canUndoLastCast,
                        ) {
                            Text("Undo")
                        }
                    }
                }
                if (showRecentActions) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        recentEvents.forEach { line ->
                            Text(text = line, style = MaterialTheme.typography.bodySmall)
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
