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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spellapp.core.model.HeightenTrigger
import com.spellapp.core.model.HeightenedEntry
import com.spellapp.core.model.RulesReferenceKey
import com.spellapp.core.model.RulesTextDocument
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem
import com.spellapp.core.model.heightenBonusDice
import com.spellapp.core.model.ordinalRank

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
    onLearnAllKnownSpells: () -> Unit,
    onUnlearnAllKnownSpells: () -> Unit,
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
    val addableSpellCount = if (isManageKnownSpells) {
        spells.count { it.id !in knownSpellIds }
    } else {
        0
    }
    val removableSpellCount = if (isManageKnownSpells) {
        spells.count { it.id in knownSpellIds }
    } else {
        0
    }

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
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (hasActiveSearch || hasActiveFilters) "Filtered: ${spells.size}" else "Spells: ${spells.size}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (isManageKnownSpells && (addableSpellCount > 0 || removableSpellCount > 0)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (addableSpellCount > 0) {
                                TextButton(onClick = onLearnAllKnownSpells) {
                                    Text("Learn All ($addableSpellCount)")
                                }
                            }
                            if (removableSpellCount > 0) {
                                TextButton(onClick = onUnlearnAllKnownSpells) {
                                    Text("Unlearn All ($removableSpellCount)")
                                }
                            }
                        }
                    }
                }
            }
            if (isManageKnownSpells || isAssignPreparedSlot) {
                item {
                    StatusPanel {
                        Text(
                            text = if (isManageKnownSpells) {
                                "Mark spells known on this track."
                            } else {
                                "Showing known spells for this track."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    val isFiltered = hasActiveFilters || hasActiveSearch
                    val primary = when {
                        isFiltered && isAssignPreparedSlot -> "No known spells match."
                        isFiltered -> "No spells match."
                        isAssignPreparedSlot -> "No known spells yet."
                        else -> "Dataset is empty."
                    }
                    val secondary = when {
                        isFiltered -> null
                        isAssignPreparedSlot -> "Mark spells known to prepare them."
                        else -> "Reload to refresh the dataset."
                    }
                    StatusPanel {
                        Text(
                            text = primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (secondary != null) {
                            Text(
                                text = secondary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                val subtitle = remember(spell.id, spell.rank, spell.tradition) {
                    formatSpellSubtitle(spell.rank, spell.tradition)
                }
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
                                .clickable(role = Role.Button) { onSpellClick(spell.id) },
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = spell.name,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    traitLookups: List<SpellTraitLookupUiState>,
    rulesDocument: RulesTextDocument,
    referenceLookups: Map<RulesReferenceKey, SpellReferenceLookupUiState>,
    heightenedAt: Int? = null,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var activeLookup by remember { mutableStateOf<SpellLookupDialogState?>(null) }
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
                DetailField(label = "Area", value = spell.area)
                DetailField(label = "Target", value = spell.target)
                DetailField(label = "Defense", value = spell.defense)
                DetailField(label = "Duration", value = spell.duration)
                if (traitLookups.isNotEmpty()) {
                    TraitLookupField(
                        traitLookups = traitLookups,
                        onLookupClick = { lookup ->
                            lookup.document?.let { document ->
                                activeLookup = SpellLookupDialogState(
                                    title = lookup.label,
                                    document = document,
                                )
                            }
                        },
                    )
                }
                Text(
                    text = "Rules Text",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .semantics { heading() },
                )
                RulesTextDocumentText(
                    document = rulesDocument,
                    referenceLookups = referenceLookups,
                    onLookupClick = { lookup -> activeLookup = lookup },
                )
                if (spell.heightenedEntries.isNotEmpty()) {
                    HeightenSection(
                        entries = spell.heightenedEntries,
                        baseRank = if (spell.rank == 0) 1 else spell.rank,
                        heightenedAt = heightenedAt,
                    )
                }
                MarginNote(label = "License", value = spell.license)
                MarginNote(
                    label = "Source",
                    value = "${spell.sourceBook} ${spell.sourcePage ?: ""}".trim(),
                )
            }
        }
    }
    activeLookup?.let { lookup ->
        SpellLookupDialog(
            lookup = lookup,
            onDismiss = { activeLookup = null },
        )
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier
                .widthIn(min = 88.dp)
                .wrapContentWidth(Alignment.End),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TraitLookupField(
    traitLookups: List<SpellTraitLookupUiState>,
    onLookupClick: (SpellTraitLookupUiState) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Traits",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            traitLookups.forEach { lookup ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 1.dp,
                    modifier = Modifier.then(
                        if (lookup.document != null) {
                            Modifier.clickable { onLookupClick(lookup) }
                        } else {
                            Modifier
                        }
                    ),
                ) {
                    Text(
                        text = lookup.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (lookup.document != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpellLookupDialog(
    lookup: SpellLookupDialogState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lookup.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                RulesTextDocumentText(
                    document = lookup.document,
                    referenceLookups = emptyMap(),
                    enableLookups = false,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun MarginNote(
    label: String,
    value: String,
) {
    if (value.isBlank()) {
        return
    }
    Text(
        text = "$label - $value",
        style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 2.dp)
            .semantics { contentDescription = "$label: $value" },
    )
}

@Composable
private fun HeightenSection(
    entries: List<HeightenedEntry>,
    baseRank: Int,
    heightenedAt: Int?,
) {
    Text(
        text = "Heightened",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
    if (heightenedAt != null) {
        val bonusDice = heightenBonusDice(entries, baseRank, heightenedAt)
        if (bonusDice != null) {
            Text(
                text = "At ${ordinalRank(heightenedAt)}: $bonusDice",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { entry ->
            val active = heightenedAt != null && isEntryActive(entry, baseRank, heightenedAt)
            HeightenBlock(entry = entry, active = active)
        }
    }
}

@Composable
private fun HeightenBlock(
    entry: HeightenedEntry,
    active: Boolean,
) {
    val bg = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (active) stateDescription = "applied"
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = formatHeightenLabel(entry.trigger, active),
                style = MaterialTheme.typography.labelLarge,
                color = fg,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
            )
        }
    }
}

private fun formatHeightenLabel(trigger: HeightenTrigger, active: Boolean): String {
    val prefix = if (active) "\u2713 " else ""
    return when (trigger) {
        is HeightenTrigger.Step -> "${prefix}Heightened (+${trigger.increment})"
        is HeightenTrigger.Absolute -> "${prefix}Heightened (${ordinalRank(trigger.rank)})"
    }
}

private fun isEntryActive(
    entry: HeightenedEntry,
    baseRank: Int,
    heightenedAt: Int,
): Boolean {
    return when (val t = entry.trigger) {
        is HeightenTrigger.Absolute -> heightenedAt >= t.rank
        is HeightenTrigger.Step -> heightenedAt >= baseRank + t.increment
    }
}

private fun formatSpellSubtitle(rank: Int, tradition: String): String {
    val rankLabel = if (rank == 0) "Cantrip" else "Rank $rank"
    val traditions = tradition
        .splitToSequence(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ") { it.replaceFirstChar { ch -> ch.uppercase() } }
    return if (traditions.isEmpty()) rankLabel else "$rankLabel | $traditions"
}

internal data class SpellLookupDialogState(
    val title: String,
    val document: RulesTextDocument,
)

internal const val SPELL_LOOKUP_ANNOTATION_TAG = "spell-lookup"


