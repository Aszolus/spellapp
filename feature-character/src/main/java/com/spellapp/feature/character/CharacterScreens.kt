package com.spellapp.feature.character

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterListRoute(
    characters: List<CharacterProfile>,
    classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition>,
    onAddCharacter: () -> Unit,
    onEditCharacter: (CharacterProfile) -> Unit,
    onDeleteCharacter: (CharacterProfile) -> Unit,
    onOpenPreparedSlots: (CharacterProfile) -> Unit,
    onOpenSpells: (CharacterProfile) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Characters") },
                actions = {
                    TextButton(onClick = onAddCharacter) {
                        Text("Add")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (characters.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "No characters yet.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Add a prepared caster to start tracking spell slots.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onAddCharacter) {
                    Text("Create Character")
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items = characters, key = { it.id }) { character ->
                CharacterRow(
                    character = character,
                    classDefinitionsByClass = classDefinitionsByClass,
                    onEdit = { onEditCharacter(character) },
                    onDelete = { onDeleteCharacter(character) },
                    onOpenPreparedSlots = { onOpenPreparedSlots(character) },
                    onOpenSpells = { onOpenSpells(character) },
                )
            }
        }
    }
}

@Composable
private fun CharacterRow(
    character: CharacterProfile,
    classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenPreparedSlots: () -> Unit,
    onOpenSpells: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = character.name,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Level ${character.level} ${character.characterClass.label(classDefinitionsByClass)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "DC ${character.spellDc} | Attack ${character.spellAttackModifier.withSign()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
                TextButton(onClick = onOpenPreparedSlots) {
                    Text("Prepare")
                }
                TextButton(onClick = onOpenSpells) {
                    Text("Spells")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterEditorDialog(
    initialCharacter: CharacterProfile?,
    initialSelectedBuildOptionIds: Set<String>,
    initialAcceptedSourceBooks: Set<String>,
    availableSpellSources: List<String>,
    availableClasses: List<CharacterClassDefinition>,
    classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition>,
    archetypeSpellcastingPackages: List<ArchetypeSpellcastingPackage>,
    onDismiss: () -> Unit,
    onSave: (CharacterProfile, Set<String>, Set<String>) -> Unit,
) {
    var name by remember(initialCharacter) { mutableStateOf(initialCharacter?.name.orEmpty()) }
    var levelText by remember(initialCharacter) {
        mutableStateOf((initialCharacter?.level ?: 1).toString())
    }
    var selectedClass by remember(initialCharacter, availableClasses) {
        mutableStateOf(
            initialCharacter?.characterClass
                ?: availableClasses.firstOrNull()?.characterClass
                ?: CharacterClass.WIZARD,
        )
    }
    var keyAbility by remember(initialCharacter) {
        mutableStateOf(
            initialCharacter?.keyAbility ?: defaultKeyAbility(
                characterClass = selectedClass,
                classDefinitions = classDefinitionsByClass,
            ),
        )
    }
    var spellDcText by remember(initialCharacter) {
        mutableStateOf((initialCharacter?.spellDc ?: 10).toString())
    }
    var spellAttackText by remember(initialCharacter) {
        mutableStateOf((initialCharacter?.spellAttackModifier ?: 0).toString())
    }
    var legacyEnabled by remember(initialCharacter) {
        mutableStateOf(initialCharacter?.legacyTerminologyEnabled ?: false)
    }
    var showSourcePicker by remember { mutableStateOf(false) }
    var sourceSearchQuery by remember { mutableStateOf("") }
    var selectedBuildOptionIds by remember(initialCharacter, initialSelectedBuildOptionIds) {
        mutableStateOf(initialSelectedBuildOptionIds.toSet())
    }
    var acceptedSourceBooks by remember(initialCharacter, initialAcceptedSourceBooks, availableSpellSources) {
        mutableStateOf(
            initialAcceptedSourceBooks.ifEmpty { availableSpellSources.toSet() },
        )
    }

    val level = levelText.toIntOrNull()?.coerceIn(1, 20)
    val spellDc = spellDcText.toIntOrNull()?.coerceIn(0, 99)
    val spellAttack = spellAttackText.toIntOrNull()?.coerceIn(-99, 99)
    val filteredSpellSources = availableSpellSources.filter { sourceBook ->
        sourceSearchQuery.isBlank() || sourceBook.contains(sourceSearchQuery.trim(), ignoreCase = true)
    }
    val canSave = name.isNotBlank() && level != null && spellDc != null && spellAttack != null
    val contentScroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCharacter == null) {
                    "Create Character"
                } else {
                    "Edit Character"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(contentScroll),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Level (1-20)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Class",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableClasses.forEach { definition ->
                        val klass = definition.characterClass
                        FilterChip(
                            selected = selectedClass == klass,
                            onClick = {
                                selectedClass = klass
                                keyAbility = defaultKeyAbility(
                                    characterClass = klass,
                                    classDefinitions = classDefinitionsByClass,
                                )
                            },
                            label = { Text(definition.label) },
                        )
                    }
                }
                Text(
                    text = "Key Ability",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keyAbilityOptions(
                        characterClass = selectedClass,
                        classDefinitions = classDefinitionsByClass,
                    ).forEach { ability ->
                        FilterChip(
                            selected = keyAbility == ability,
                            onClick = { keyAbility = ability },
                            label = { Text(ability.label()) },
                        )
                    }
                }
                OutlinedTextField(
                    value = spellDcText,
                    onValueChange = { spellDcText = sanitizeSignedNumber(it, maxLength = 2) },
                    label = { Text("Spell DC") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = spellAttackText,
                    onValueChange = { spellAttackText = sanitizeSignedNumber(it, maxLength = 3) },
                    label = { Text("Spell Attack Modifier") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Accepted Sources",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (availableSpellSources.isEmpty()) {
                    Text(
                        text = "No spell sources are available yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${acceptedSourceBooks.size} of ${availableSpellSources.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { showSourcePicker = true }) {
                            Text("Choose")
                        }
                    }
                }
                Text(
                    text = "Archetype spellcasting",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = "Slot unlocks: Basic 4/6/8, Expert 12/14/16, Master 18/20.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (archetypeSpellcastingPackages.isEmpty()) {
                    Text(
                        text = "No phase-one archetype spellcasting packages available.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    archetypeSpellcastingPackages.forEach { packageDef ->
                        val dedicationSelected = packageDef.dedicationOptionId in selectedBuildOptionIds
                        val basicSelected = packageDef.basicSpellcastingOptionId
                            ?.let { optionId -> optionId in selectedBuildOptionIds }
                            ?: false
                        val expertSelected = packageDef.expertSpellcastingOptionId
                            ?.let { optionId -> optionId in selectedBuildOptionIds }
                            ?: false
                        val masterSelected = packageDef.masterSpellcastingOptionId
                            ?.let { optionId -> optionId in selectedBuildOptionIds }
                            ?: false

                        Text(
                            text = packageDef.label,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        val slotSummary = summarizeArchetypeSlotsForLevel(
                            level = level ?: 1,
                            hasBasic = basicSelected,
                            hasExpert = expertSelected,
                            hasMaster = masterSelected,
                        )
                        Text(
                            text = slotSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterChip(
                                selected = dedicationSelected,
                                onClick = {
                                    val next = selectedBuildOptionIds.toMutableSet()
                                    if (dedicationSelected) {
                                        next -= packageDef.dedicationOptionId
                                        listOfNotNull(
                                            packageDef.basicSpellcastingOptionId,
                                            packageDef.expertSpellcastingOptionId,
                                            packageDef.masterSpellcastingOptionId,
                                        ).forEach { optionId -> next -= optionId }
                                    } else {
                                        next += packageDef.dedicationOptionId
                                    }
                                    selectedBuildOptionIds = next
                                },
                                label = { Text("Dedication") },
                            )
                            packageDef.basicSpellcastingOptionId?.let { basicOptionId ->
                                FilterChip(
                                    selected = basicSelected,
                                    onClick = {
                                        val next = selectedBuildOptionIds.toMutableSet()
                                        if (basicSelected) {
                                            next -= basicOptionId
                                            packageDef.expertSpellcastingOptionId?.let { next -= it }
                                            packageDef.masterSpellcastingOptionId?.let { next -= it }
                                        } else {
                                            next += packageDef.dedicationOptionId
                                            next += basicOptionId
                                        }
                                        selectedBuildOptionIds = next
                                    },
                                    label = { Text("Basic") },
                                )
                            }
                            packageDef.expertSpellcastingOptionId?.let { expertOptionId ->
                                FilterChip(
                                    selected = expertSelected,
                                    onClick = {
                                        val basicOptionId = packageDef.basicSpellcastingOptionId
                                        val next = selectedBuildOptionIds.toMutableSet()
                                        if (expertSelected) {
                                            next -= expertOptionId
                                            packageDef.masterSpellcastingOptionId?.let { next -= it }
                                        } else {
                                            next += packageDef.dedicationOptionId
                                            if (basicOptionId != null) {
                                                next += basicOptionId
                                            }
                                            next += expertOptionId
                                        }
                                        selectedBuildOptionIds = next
                                    },
                                    label = { Text("Expert") },
                                )
                            }
                            packageDef.masterSpellcastingOptionId?.let { masterOptionId ->
                                FilterChip(
                                    selected = masterSelected,
                                    onClick = {
                                        val basicOptionId = packageDef.basicSpellcastingOptionId
                                        val expertOptionId = packageDef.expertSpellcastingOptionId
                                        val next = selectedBuildOptionIds.toMutableSet()
                                        if (masterSelected) {
                                            next -= masterOptionId
                                        } else {
                                            next += packageDef.dedicationOptionId
                                            if (basicOptionId != null) {
                                                next += basicOptionId
                                            }
                                            if (expertOptionId != null) {
                                                next += expertOptionId
                                            }
                                            next += masterOptionId
                                        }
                                        selectedBuildOptionIds = next
                                    },
                                    label = { Text("Master") },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Legacy terms",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    FilterChip(
                        selected = legacyEnabled,
                        onClick = { legacyEnabled = !legacyEnabled },
                        label = {
                            Text(if (legacyEnabled) "On" else "Off")
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!canSave) {
                        return@TextButton
                    }
                    onSave(
                        CharacterProfile(
                            id = initialCharacter?.id ?: 0L,
                            name = name.trim(),
                            level = level ?: 1,
                            characterClass = selectedClass,
                            keyAbility = keyAbility,
                            spellDc = spellDc ?: 10,
                            spellAttackModifier = spellAttack ?: 0,
                            legacyTerminologyEnabled = legacyEnabled,
                        ),
                        selectedBuildOptionIds,
                        acceptedSourceBooks,
                    )
                },
                enabled = canSave,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = {
                sourceSearchQuery = ""
                showSourcePicker = false
            },
            title = { Text("Accepted Sources") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = { acceptedSourceBooks = availableSpellSources.toSet() }) {
                            Text("All")
                        }
                        TextButton(onClick = { acceptedSourceBooks = emptySet() }) {
                            Text("None")
                        }
                    }
                    OutlinedTextField(
                        value = sourceSearchQuery,
                        onValueChange = { sourceSearchQuery = it },
                        label = { Text("Search sources") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Showing ${filteredSpellSources.size} of ${availableSpellSources.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (filteredSpellSources.isEmpty()) {
                            item("empty-search") {
                                Text(
                                    text = "No sources matched that search.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(filteredSpellSources, key = { it }) { sourceBook ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = sourceBook,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Checkbox(
                                    checked = sourceBook in acceptedSourceBooks,
                                    onCheckedChange = { checked ->
                                        acceptedSourceBooks = acceptedSourceBooks.toMutableSet().apply {
                                            if (checked) {
                                                add(sourceBook)
                                            } else {
                                                remove(sourceBook)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    sourceSearchQuery = ""
                    showSourcePicker = false
                }) {
                    Text("Done")
                }
            },
        )
    }
}

private fun summarizeArchetypeSlotsForLevel(
    level: Int,
    hasBasic: Boolean,
    hasExpert: Boolean,
    hasMaster: Boolean,
): String {
    val unlockedRanks = mutableListOf<Int>()
    if (hasBasic) {
        if (level >= 4) unlockedRanks += 1
        if (level >= 6) unlockedRanks += 2
        if (level >= 8) unlockedRanks += 3
    }
    if (hasExpert) {
        if (level >= 12) unlockedRanks += 4
        if (level >= 14) unlockedRanks += 5
        if (level >= 16) unlockedRanks += 6
    }
    if (hasMaster) {
        if (level >= 18) unlockedRanks += 7
        if (level >= 20) unlockedRanks += 8
    }
    if (unlockedRanks.isEmpty()) {
        return "At level $level: no archetype spell slots unlocked."
    }
    val rankText = unlockedRanks
        .sorted()
        .joinToString(", ") { rank -> "R$rank" }
    return "At level $level: $rankText (1 slot each)."
}
