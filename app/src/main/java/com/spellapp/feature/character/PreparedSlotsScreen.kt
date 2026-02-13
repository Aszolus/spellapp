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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spellapp.core.model.PreparedSlot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreparedSlotsRoute(
    characterName: String,
    selectedRank: Int,
    slotsForRank: List<PreparedSlot>,
    spellNameById: Map<String, String>,
    onRankChange: (Int) -> Unit,
    onAddSlot: (Int) -> Unit,
    onRemoveSlot: (Int, Int) -> Unit,
    onChooseSpell: (Int, Int) -> Unit,
    onClearSpell: (Int, Int) -> Unit,
    onCastSlot: (Int, Int) -> Unit,
    canUndoLastCast: Boolean,
    onUndoLastCast: () -> Unit,
    onOpenSpellBrowser: () -> Unit,
    recentEvents: List<String>,
    onBack: () -> Unit,
) {
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Preparation Rank",
                style = MaterialTheme.typography.labelLarge,
            )
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$rankLabel slots: ${slotsForRank.size}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = { onAddSlot(selectedRank) }) {
                    Text(if (selectedRank == 0) "Add Cantrip" else "Add Slot")
                }
            }

            if (slotsForRank.isEmpty()) {
                Text(
                    text = if (selectedRank == 0) {
                        "No cantrips prepared. Add one to assign a cantrip spell."
                    } else {
                        "No slots configured for this rank."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(slotsForRank, key = { "${it.rank}-${it.slotIndex}" }) { slot ->
                        val slotTitle = if (slot.rank == 0) {
                            "Cantrip ${slot.slotIndex + 1}"
                        } else {
                            "Rank ${slot.rank} Slot ${slot.slotIndex + 1}"
                        }
                        val preparedName = slot.preparedSpellId?.let { spellId ->
                            spellNameById[spellId] ?: spellId
                        }
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = slotTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = when {
                                        preparedName == null -> "Empty"
                                        slot.isExpended -> "$preparedName (Expended)"
                                        else -> preparedName
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(onClick = { onRemoveSlot(slot.rank, slot.slotIndex) }) {
                                        Text("Remove")
                                    }
                                    TextButton(
                                        onClick = { onClearSpell(slot.rank, slot.slotIndex) },
                                        enabled = slot.preparedSpellId != null,
                                    ) {
                                        Text("Clear")
                                    }
                                    Button(
                                        onClick = { onCastSlot(slot.rank, slot.slotIndex) },
                                        enabled = slot.preparedSpellId != null && !slot.isExpended,
                                    ) {
                                        Text("Cast")
                                    }
                                    Button(onClick = { onChooseSpell(slot.rank, slot.slotIndex) }) {
                                        Text("Choose")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (recentEvents.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Recent Actions",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        onClick = onUndoLastCast,
                        enabled = canUndoLastCast,
                    ) {
                        Text("Undo Last Cast")
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recentEvents.forEach { line ->
                        Text(text = line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
