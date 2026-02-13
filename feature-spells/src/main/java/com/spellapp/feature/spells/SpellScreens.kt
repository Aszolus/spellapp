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
import com.spellapp.core.model.SpellListItem

private val sampleSpells = listOf(
    SpellListItem(
        id = "magic-missile",
        name = "Magic Missile",
        rank = 1,
        tradition = "Arcane",
    ),
    SpellListItem(
        id = "heal",
        name = "Heal",
        rank = 1,
        tradition = "Divine",
    ),
    SpellListItem(
        id = "fireball",
        name = "Fireball",
        rank = 3,
        tradition = "Arcane",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellListRoute(
    onSpellClick: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Spell List") },
            )
        },
    ) { innerPadding ->
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
            items(items = sampleSpells, key = { it.id }) { spell ->
                ListItem(
                    headlineContent = { Text(text = spell.name) },
                    supportingContent = {
                        Text(text = "Rank ${spell.rank} • ${spell.tradition}")
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
    spellId: String,
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
            Text(
                text = spellId,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Placeholder detail screen. Room-backed spell data wiring is part of M2/M3.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Tap back to return to list.",
                modifier = Modifier.clickable(onClick = onBack),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
