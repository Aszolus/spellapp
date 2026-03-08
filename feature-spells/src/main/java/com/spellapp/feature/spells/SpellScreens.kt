package com.spellapp.feature.spells

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem

private val filterTraditions = listOf("arcane", "divine", "occult", "primal")
private val filterRarities = listOf("common", "uncommon", "rare", "unique")
private val filterRanks = (0..10).toList()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpellListRoute(
    spells: List<SpellListItem>,
    title: String = "Spell List",
    browserMode: SpellBrowserMode,
    knownSpellIds: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    traitQuery: String,
    availableTraits: List<String>,
    onTraitQueryChange: (String) -> Unit,
    selectedRank: Int?,
    onRankChange: (Int) -> Unit,
    selectedTradition: String?,
    onTraditionChange: (String) -> Unit,
    selectedRarities: Set<String>,
    onRarityChange: (String) -> Unit,
    onClearTraitFilter: () -> Unit,
    onClearRankFilter: () -> Unit,
    onClearTraditionFilter: () -> Unit,
    onClearRarityFilter: (String) -> Unit,
    pendingKnownSpellWarning: PendingKnownSpellWarning?,
    onConfirmKnownSpellWarning: () -> Unit,
    onDismissKnownSpellWarning: () -> Unit,
    isLoading: Boolean,
    loadError: String?,
    onRetryLoad: () -> Unit,
    onClearFilters: () -> Unit,
    onSpellClick: (String) -> Unit,
    onKnownSpellToggle: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    var showFiltersDialog by rememberSaveable { mutableStateOf(false) }
    var isTraitSuggestionExpanded by rememberSaveable { mutableStateOf(false) }
    val isManageKnownSpells = browserMode is SpellBrowserMode.ManageKnownSpells
    val isAssignPreparedSlot = browserMode is SpellBrowserMode.AssignPreparedSlot
    val supportsRankFilter = browserMode !is SpellBrowserMode.AssignPreparedSlot
    val normalizedTraitQuery = traitQuery.trim()
    val traitSuggestions = availableTraits.matchingTraitSuggestions(normalizedTraitQuery)
    val showTraitSuggestions = isTraitSuggestionExpanded && traitSuggestions.isNotEmpty()
    val activeFilters = buildList {
        if (supportsRankFilter && selectedRank != null) {
            add(
                ActiveSpellFilter(
                    label = if (selectedRank == 0) "Cantrip" else "Rank $selectedRank",
                    onRemove = onClearRankFilter,
                ),
            )
        }
        selectedTradition?.let { tradition ->
            add(
                ActiveSpellFilter(
                    label = tradition.replaceFirstChar { it.uppercase() },
                    onRemove = onClearTraditionFilter,
                ),
            )
        }
        if (normalizedTraitQuery.isNotEmpty()) {
            add(
                ActiveSpellFilter(
                    label = "Trait: $normalizedTraitQuery",
                    onRemove = onClearTraitFilter,
                ),
            )
        }
        selectedRarities.sorted().forEach { rarity ->
            add(
                ActiveSpellFilter(
                    label = rarity.replaceFirstChar { it.uppercase() },
                    onRemove = { onClearRarityFilter(rarity) },
                ),
            )
        }
    }
    val activeFilterCount = activeFilters.size
    val hasActiveSearch = query.isNotBlank()
    val hasActiveFilters = activeFilterCount > 0

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
                start = 8.dp,
                top = 8.dp,
                end = 8.dp,
                bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("Search spells") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showFiltersDialog = true }) {
                        Text(if (activeFilterCount == 0) "Filters" else "Filters ($activeFilterCount)")
                    }
                    if (hasActiveFilters) {
                        TextButton(onClick = onClearFilters) {
                            Text("Clear Filters")
                        }
                    }
                }
            }
            if (hasActiveFilters) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        activeFilters.forEach { filter ->
                            FilterChip(
                                selected = true,
                                onClick = filter.onRemove,
                                label = { Text("${filter.label} x") },
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (hasActiveSearch || hasActiveFilters) "Filtered: ${spells.size}" else "Spells: ${spells.size}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (isManageKnownSpells || isAssignPreparedSlot) {
                item {
                    StatusPanel {
                        Text(
                            text = if (isManageKnownSpells) {
                                "Manage the spell pool used for preparation on this track."
                            } else {
                                "Choose from the known spells on this track."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
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
                    val primary = if ((hasActiveFilters || hasActiveSearch) && isAssignPreparedSlot) {
                        "No known spells matched this filter set."
                    } else if (hasActiveFilters || hasActiveSearch) {
                        "No spells matched this filter set."
                    } else if (isAssignPreparedSlot) {
                        "No known spells available."
                    } else if (isManageKnownSpells) {
                        "No spells available to manage."
                    } else {
                        "No spells available."
                    }
                    val secondary = if (isAssignPreparedSlot && !hasActiveFilters && !hasActiveSearch) {
                        "Add known spells first, then prepare from this list."
                    } else if (hasActiveFilters || hasActiveSearch) {
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSpellClick(spell.id) },
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = spell.name,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "$rankLabel | $traditions",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (isManageKnownSpells) {
                            val isKnown = spell.id in knownSpellIds
                            TextButton(
                                onClick = { onKnownSpellToggle(spell.id) },
                            ) {
                                Text(if (isKnown) "Remove" else "Add")
                            }
                        }
                    }
                    if (isManageKnownSpells && spell.id in knownSpellIds) {
                        Text(
                            text = "Known on this track",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (showFiltersDialog) {
        AlertDialog(
            onDismissRequest = { showFiltersDialog = false },
            title = { Text("Filters") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (supportsRankFilter) {
                        FilterSection(title = "Spell Level") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                filterRanks.forEach { rank ->
                                    val rankLabel = if (rank == 0) "Cantrip" else "Rank $rank"
                                    FilterChip(
                                        selected = selectedRank == rank,
                                        onClick = { onRankChange(rank) },
                                        label = { Text(rankLabel) },
                                    )
                                }
                            }
                        }
                    }

                    FilterSection(title = "Tradition") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            filterTraditions.forEach { tradition ->
                                FilterChip(
                                    selected = selectedTradition == tradition,
                                    onClick = { onTraditionChange(tradition) },
                                    label = { Text(tradition.replaceFirstChar { it.uppercase() }) },
                                )
                            }
                        }
                    }

                    FilterSection(title = "Rarity") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            filterRarities.forEach { rarity ->
                                FilterChip(
                                    selected = rarity in selectedRarities,
                                    onClick = { onRarityChange(rarity) },
                                    label = { Text(rarity.replaceFirstChar { it.uppercase() }) },
                                )
                            }
                        }
                    }

                    FilterSection(title = "Trait") {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = traitQuery,
                                    onValueChange = { value ->
                                        isTraitSuggestionExpanded = true
                                        onTraitQueryChange(value)
                                    },
                                    label = { Text("Trait") },
                                    placeholder = { Text("Start typing a trait") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (showTraitSuggestions) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .heightIn(max = 240.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        tonalElevation = 3.dp,
                                        shadowElevation = 6.dp,
                                    ) {
                                        Column {
                                            traitSuggestions.forEachIndexed { index, suggestion ->
                                                DropdownMenuItem(
                                                    text = { Text(suggestion) },
                                                    onClick = {
                                                        isTraitSuggestionExpanded = false
                                                        onTraitQueryChange(suggestion)
                                                    },
                                                )
                                                if (index != traitSuggestions.lastIndex) {
                                                    HorizontalDivider()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFiltersDialog = false }) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(onClick = onClearFilters) {
                    Text("Reset")
                }
            },
        )
    }

    pendingKnownSpellWarning?.let { pendingWarning ->
        AlertDialog(
            onDismissRequest = onDismissKnownSpellWarning,
            title = { Text(pendingWarning.title) },
            text = {
                Text(pendingWarning.message)
            },
            confirmButton = {
                TextButton(onClick = onConfirmKnownSpellWarning) {
                    Text(pendingWarning.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissKnownSpellWarning) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun List<String>.matchingTraitSuggestions(query: String): List<String> {
    if (query.isBlank()) {
        return emptyList()
    }
    val prefixMatches = filter { trait ->
        trait.startsWith(query, ignoreCase = true) && !trait.equals(query, ignoreCase = true)
    }
    if (prefixMatches.isNotEmpty()) {
        return prefixMatches.take(6)
    }
    return filter { trait ->
        trait.contains(query, ignoreCase = true) && !trait.equals(query, ignoreCase = true)
    }.take(6)
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
        content()
    }
}

private data class ActiveSpellFilter(
    val label: String,
    val onRemove: () -> Unit,
)

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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
