package com.spellapp.feature.spells

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spellapp.core.model.SpellDetail
import com.spellapp.core.model.SpellListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellListRoute(
    spells: List<SpellListItem>,
    onSpellClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Spell List") },
            )
        },
    ) { innerPadding ->
        if (spells.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "No spells loaded yet.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "If this persists, verify local dataset seeding succeeded.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = innerPadding.calculateTopPadding(),
                end = 12.dp,
                bottom = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = spells, key = { it.id }) { spell ->
                ListItem(
                    headlineContent = { Text(text = spell.name) },
                    supportingContent = {
                        Text(text = "Rank ${spell.rank} - ${spell.tradition}")
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
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Spell Detail") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (spell == null) {
                Text(
                    text = "Spell not found.",
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                Text(
                    text = spell.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "Rank ${spell.rank} | ${spell.tradition}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Cast: ${spell.castTime}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Range: ${spell.range}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Target: ${spell.target}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Duration: ${spell.duration}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (spell.traits.isNotEmpty()) {
                    Text(
                        text = "Traits: ${spell.traits.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = spell.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "License: ${spell.license}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = "Source: ${spell.sourceBook} ${spell.sourcePage ?: ""}".trim(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Text(
                text = "Back",
                modifier = Modifier.clickable(onClick = onBack),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
