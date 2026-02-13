package com.spellapp.feature.spells

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem

private val filterTraditions = listOf("arcane", "divine", "occult", "primal")
private val filterRarities = listOf("common", "uncommon", "rare", "unique")
private val filterRanks = (0..10).toList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellListRoute(
    spells: List<SpellListItem>,
    title: String = "Spell List",
    query: String,
    onQueryChange: (String) -> Unit,
    traitQuery: String,
    onTraitQueryChange: (String) -> Unit,
    selectedRank: Int?,
    onRankChange: (Int) -> Unit,
    selectedTradition: String?,
    onTraditionChange: (String) -> Unit,
    selectedRarity: String?,
    onRarityChange: (String) -> Unit,
    isLoading: Boolean,
    loadError: String?,
    onRetryLoad: () -> Unit,
    onClearFilters: () -> Unit,
    onSpellClick: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val hasActiveFilters = query.isNotBlank() ||
        traitQuery.isNotBlank() ||
        selectedRank != null ||
        selectedTradition != null ||
        selectedRarity != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) {
                            Text("Back")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Search by spell name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = traitQuery,
                    onValueChange = onTraitQueryChange,
                    label = { Text("Filter by trait text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Rank",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filterRanks, key = { "rank-$it" }) { rank ->
                        val rankLabel = if (rank == 0) "Cantrip" else "Rank $rank"
                        FilterChip(
                            selected = selectedRank == rank,
                            onClick = { onRankChange(rank) },
                            label = { Text(rankLabel) },
                        )
                    }
                }
            }
            item {
                Text(
                    text = "Tradition",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filterTraditions, key = { "trad-$it" }) { tradition ->
                        FilterChip(
                            selected = selectedTradition == tradition,
                            onClick = { onTraditionChange(tradition) },
                            label = { Text(tradition.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
            item {
                Text(
                    text = "Rarity",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filterRarities, key = { "rarity-$it" }) { rarity ->
                        FilterChip(
                            selected = selectedRarity == rarity,
                            onClick = { onRarityChange(rarity) },
                            label = { Text(rarity.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (hasActiveFilters) "Filtered: ${spells.size}" else "Spells: ${spells.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (hasActiveFilters) {
                        TextButton(onClick = onClearFilters) {
                            Text("Clear Filters")
                        }
                    }
                }
            }
            item {
                if (isLoading) {
                    StatusPanel {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Loading local spell dataset...",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                } else if (loadError != null && spells.isEmpty()) {
                    StatusPanel {
                        Text(
                            text = "Spell load failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = loadError,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onRetryLoad) {
                            Text("Retry Load")
                        }
                    }
                } else if (spells.isEmpty()) {
                    val primary = if (hasActiveFilters) {
                        "No spells matched this filter set."
                    } else {
                        "No spells available."
                    }
                    val secondary = if (hasActiveFilters) {
                        "Adjust filters or clear them."
                    } else {
                        "Try reloading the app data."
                    }
                    StatusPanel {
                        Text(
                            text = primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = secondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (hasActiveFilters) {
                            TextButton(onClick = onClearFilters) {
                                Text("Clear Filters")
                            }
                        }
                    }
                }
            }
            if (loadError != null && spells.isNotEmpty()) {
                item {
                    StatusPanel {
                        Text(
                            text = "Dataset refresh failed. Showing existing local data.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetryLoad) {
                            Text("Retry")
                        }
                    }
                }
            }
            items(items = spells, key = { it.id }) { spell ->
                val rankLabel = if (spell.rank == 0) "Cantrip" else "Rank ${spell.rank}"
                val traditions = spell.tradition
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(", ") { it.replaceFirstChar { ch -> ch.uppercase() } }
                ListItem(
                    headlineContent = { Text(text = spell.name) },
                    supportingContent = {
                        Text(text = "$rankLabel | $traditions")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSpellClick(spell.id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellDetailRoute(
    spell: SpellDetail?,
    isLoading: Boolean,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = spell?.name ?: "Spell Detail") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading spell details...",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else if (spell == null) {
                StatusPanel {
                    Text(
                        text = "Spell not found.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Return to the list and choose another spell.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onBack) {
                        Text("Back to List")
                    }
                }
            } else {
                val rankLabel = if (spell.rank == 0) "Cantrip" else "Rank ${spell.rank}"
                DetailField(label = "Rank", value = rankLabel)
                DetailField(label = "Tradition", value = spell.tradition)
                DetailField(label = "Rarity", value = spell.rarity)
                DetailField(label = "Cast", value = spell.castTime)
                DetailField(label = "Range", value = spell.range)
                DetailField(label = "Target", value = spell.target)
                DetailField(label = "Duration", value = spell.duration)
                if (spell.traits.isNotEmpty()) {
                    DetailField(label = "Traits", value = spell.traits.joinToString(", "))
                }
                Text(
                    text = "Rules Text",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = spell.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                DetailField(label = "License", value = spell.license)
                DetailField(
                    label = "Source",
                    value = "${spell.sourceBook} ${spell.sourcePage ?: ""}".trim(),
                )
            }
        }
    }
}

@Composable
private fun StatusPanel(
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun DetailField(
    label: String,
    value: String,
) {
    if (value.isBlank()) {
        return
    }
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
    )
}
