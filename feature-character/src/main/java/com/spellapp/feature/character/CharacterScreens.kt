package com.spellapp.feature.character

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spellapp.core.model.CharacterClass
import com.spellapp.core.model.CharacterProfile

private val coreSpellSourceBooks = setOf(
    "Pathfinder Core Rulebook",
    "Pathfinder Advanced Player's Guide",
    "Pathfinder Secrets of Magic",
    "Pathfinder Player Core",
    "Pathfinder Player Core 2",
    "Pathfinder GM Core",
    "Pathfinder Rage of Elements",
)

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
    var pendingDeleteCharacter by remember { mutableStateOf<CharacterProfile?>(null) }
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
                    text = "Add a caster to prepare slots.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .padding(innerPadding),
        ) {
            itemsIndexed(items = characters, key = { _, character -> character.id }) { index, character ->
                if (index > 0) {
                    HorizontalDivider()
                }
                CharacterRow(
                    character = character,
                    classDefinitionsByClass = classDefinitionsByClass,
                    onEdit = { onEditCharacter(character) },
                    onDelete = { pendingDeleteCharacter = character },
                    onOpenPreparedSlots = { onOpenPreparedSlots(character) },
                    onOpenSpells = { onOpenSpells(character) },
                )
            }
        }
    }

    pendingDeleteCharacter?.let { character ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCharacter = null },
            title = {
                Text("Delete Character Permanently?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "\"${character.name}\" will be deleted permanently.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "This cannot be undone. Known spells, prepared slots, casting tracks, session history, and focus state for this character will all be removed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCharacter(character)
                        pendingDeleteCharacter = null
                    },
                ) {
                    Text(
                        text = "Delete Forever",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCharacter = null }) {
                    Text("Cancel")
                }
            },
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpenPreparedSlots)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = character.name,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Level ${character.level} ${character.characterClass.label(classDefinitionsByClass)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "DC ${character.spellDc} · Attack ${character.spellAttackModifier.withSign()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) {
                Text("Edit")
            }
            TextButton(onClick = onOpenSpells) {
                Text("Spells")
            }
            TextButton(onClick = onDelete) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Stable
internal class CharacterEditorState(
    private val initialCharacter: CharacterProfile?,
    availableClasses: List<CharacterClassDefinition>,
    private val classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition>,
    availableSpellSources: List<String>,
    initialSelectedBuildOptionIds: Set<String>,
    initialAcceptedSourceBooks: Set<String>,
) {
    var name by mutableStateOf(initialCharacter?.name.orEmpty())
    var levelText by mutableStateOf((initialCharacter?.level ?: 1).toString())
    var selectedClass by mutableStateOf(
        initialCharacter?.characterClass
            ?: availableClasses.firstOrNull()?.characterClass
            ?: CharacterClass.WIZARD,
    )
    var keyAbility by mutableStateOf(
        initialCharacter?.keyAbility
            ?: defaultKeyAbility(selectedClass, classDefinitionsByClass),
    )
    var spellDcText by mutableStateOf((initialCharacter?.spellDc ?: 10).toString())
    var spellAttackText by mutableStateOf((initialCharacter?.spellAttackModifier ?: 0).toString())
    var legacyEnabled by mutableStateOf(initialCharacter?.legacyTerminologyEnabled ?: false)
    var saveAttempted by mutableStateOf(false)
        private set
    var selectedBuildOptionIds by mutableStateOf(initialSelectedBuildOptionIds.toSet())
        private set
    var acceptedSourceBooks by mutableStateOf(
        initialAcceptedSourceBooks.ifEmpty { availableSpellSources.toSet() },
    )

    val level: Int? get() = levelText.toIntOrNull()?.coerceIn(1, 20)
    val spellDc: Int? get() = spellDcText.toIntOrNull()?.coerceIn(0, 99)
    val spellAttack: Int? get() = spellAttackText.toIntOrNull()?.coerceIn(-99, 99)
    val nameInvalid: Boolean get() = name.isBlank()
    val levelInvalid: Boolean get() = level == null
    val spellDcInvalid: Boolean get() = spellDc == null
    val spellAttackInvalid: Boolean get() = spellAttack == null
    val canSave: Boolean
        get() = !nameInvalid && !levelInvalid && !spellDcInvalid && !spellAttackInvalid

    fun selectClass(klass: CharacterClass) {
        selectedClass = klass
        keyAbility = defaultKeyAbility(klass, classDefinitionsByClass)
    }

    fun toggleBuildOption(
        packageDef: ArchetypeSpellcastingPackage,
        tier: ArchetypeTier,
        turnOn: Boolean,
    ) {
        selectedBuildOptionIds = toggleArchetypeTier(
            packageDef,
            tier,
            selectedBuildOptionIds,
            turnOn = turnOn,
        )
    }

    fun attemptSave(): CharacterProfile? {
        if (!canSave) {
            saveAttempted = true
            return null
        }
        return CharacterProfile(
            id = initialCharacter?.id ?: 0L,
            name = name.trim(),
            level = level ?: 1,
            characterClass = selectedClass,
            keyAbility = keyAbility,
            spellDc = spellDc ?: 10,
            spellAttackModifier = spellAttack ?: 0,
            legacyTerminologyEnabled = legacyEnabled,
        )
    }
}

@Composable
private fun rememberCharacterEditorState(
    initialCharacter: CharacterProfile?,
    availableClasses: List<CharacterClassDefinition>,
    classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition>,
    availableSpellSources: List<String>,
    initialSelectedBuildOptionIds: Set<String>,
    initialAcceptedSourceBooks: Set<String>,
): CharacterEditorState = remember(
    initialCharacter,
    availableClasses,
    availableSpellSources,
    initialSelectedBuildOptionIds,
    initialAcceptedSourceBooks,
) {
    CharacterEditorState(
        initialCharacter = initialCharacter,
        availableClasses = availableClasses,
        classDefinitionsByClass = classDefinitionsByClass,
        availableSpellSources = availableSpellSources,
        initialSelectedBuildOptionIds = initialSelectedBuildOptionIds,
        initialAcceptedSourceBooks = initialAcceptedSourceBooks,
    )
}

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
    val state = rememberCharacterEditorState(
        initialCharacter = initialCharacter,
        availableClasses = availableClasses,
        classDefinitionsByClass = classDefinitionsByClass,
        availableSpellSources = availableSpellSources,
        initialSelectedBuildOptionIds = initialSelectedBuildOptionIds,
        initialAcceptedSourceBooks = initialAcceptedSourceBooks,
    )
    var showSourcePicker by remember { mutableStateOf(false) }
    val contentScroll = rememberScrollState()
    val editorMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.70f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialCharacter == null) "Create Character" else "Edit Character",
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = editorMaxHeight)
                    .verticalScroll(contentScroll),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IdentityFields(state)
                ClassSelector(state, availableClasses)
                KeyAbilitySelector(state, classDefinitionsByClass)
                SpellStatFields(state)
                AcceptedSourcesRow(state, availableSpellSources) { showSourcePicker = true }
                ArchetypeSpellcastingSection(state, archetypeSpellcastingPackages)
                PreRemasterToggle(state)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.attemptSave()?.let { profile ->
                        onSave(profile, state.selectedBuildOptionIds, state.acceptedSourceBooks)
                    }
                },
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
        SourcePickerDialog(
            state = state,
            availableSpellSources = availableSpellSources,
            onDismiss = { showSourcePicker = false },
        )
    }
}

@Composable
private fun IdentityFields(state: CharacterEditorState) {
    OutlinedTextField(
        value = state.name,
        onValueChange = { state.name = it },
        label = { Text("Name") },
        placeholder = { Text("e.g. Elara Nightweave") },
        singleLine = true,
        isError = state.saveAttempted && state.nameInvalid,
        supportingText = if (state.saveAttempted && state.nameInvalid) {
            { Text("Name is required.") }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.levelText,
        onValueChange = { state.levelText = it.filter(Char::isDigit).take(2) },
        label = { Text("Level (1-20)") },
        singleLine = true,
        isError = state.saveAttempted && state.levelInvalid,
        supportingText = if (state.saveAttempted && state.levelInvalid) {
            { Text("Enter a level between 1 and 20.") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassSelector(
    state: CharacterEditorState,
    availableClasses: List<CharacterClassDefinition>,
) {
    SectionLabel("Class")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        availableClasses.forEach { definition ->
            val klass = definition.characterClass
            FilterChip(
                selected = state.selectedClass == klass,
                onClick = { state.selectClass(klass) },
                label = { Text(definition.label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyAbilitySelector(
    state: CharacterEditorState,
    classDefinitionsByClass: Map<CharacterClass, CharacterClassDefinition>,
) {
    SectionLabel("Key Ability")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keyAbilityOptions(
            characterClass = state.selectedClass,
            classDefinitions = classDefinitionsByClass,
        ).forEach { ability ->
            FilterChip(
                selected = state.keyAbility == ability,
                onClick = { state.keyAbility = ability },
                label = { Text(ability.label()) },
            )
        }
    }
}

@Composable
private fun SpellStatFields(state: CharacterEditorState) {
    OutlinedTextField(
        value = state.spellDcText,
        onValueChange = { state.spellDcText = sanitizeSignedNumber(it, maxLength = 2) },
        label = { Text("Spell DC") },
        singleLine = true,
        isError = state.saveAttempted && state.spellDcInvalid,
        supportingText = if (state.saveAttempted && state.spellDcInvalid) {
            { Text("Enter a DC between 0 and 99.") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.spellAttackText,
        onValueChange = { state.spellAttackText = sanitizeSignedNumber(it, maxLength = 3) },
        label = { Text("Spell Attack Modifier") },
        singleLine = true,
        isError = state.saveAttempted && state.spellAttackInvalid,
        supportingText = if (state.saveAttempted && state.spellAttackInvalid) {
            { Text("Enter a modifier between -99 and 99.") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AcceptedSourcesRow(
    state: CharacterEditorState,
    availableSpellSources: List<String>,
    onOpenPicker: () -> Unit,
) {
    SectionLabel("Accepted Sources")
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${state.acceptedSourceBooks.size} of ${availableSpellSources.size} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenPicker) {
                Text("Choose")
            }
        }
    }
}

@Composable
private fun ArchetypeSpellcastingSection(
    state: CharacterEditorState,
    packages: List<ArchetypeSpellcastingPackage>,
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedPackages = packages.filter { it.dedicationOptionId in state.selectedBuildOptionIds }
    val availableToAdd = packages.filter { it.dedicationOptionId !in state.selectedBuildOptionIds }

    SectionLabel("Archetype Spellcasting")

    if (packages.isEmpty()) {
        Text(
            text = "No phase-one archetype spellcasting packages available.",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    if (selectedPackages.isEmpty()) {
        Text(
            text = "No archetypes selected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        selectedPackages.forEach { packageDef ->
            SelectedArchetypeRow(state, packageDef)
        }
    }

    if (availableToAdd.isNotEmpty()) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (selectedPackages.isEmpty()) "Add Archetype" else "Add Another Archetype")
        }
    }

    if (showPicker) {
        ArchetypePickerDialog(
            available = availableToAdd,
            onPick = { picked ->
                state.toggleBuildOption(picked, ArchetypeTier.DEDICATION, turnOn = true)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedArchetypeRow(
    state: CharacterEditorState,
    packageDef: ArchetypeSpellcastingPackage,
) {
    val basicSelected = packageDef.basicSpellcastingOptionId
        ?.let { it in state.selectedBuildOptionIds } ?: false
    val expertSelected = packageDef.expertSpellcastingOptionId
        ?.let { it in state.selectedBuildOptionIds } ?: false
    val masterSelected = packageDef.masterSpellcastingOptionId
        ?.let { it in state.selectedBuildOptionIds } ?: false
    val effectiveLevel = state.level ?: 1
    val slotSummary = remember(effectiveLevel, basicSelected, expertSelected, masterSelected) {
        summarizeArchetypeSlotsForLevel(
            level = effectiveLevel,
            hasBasic = basicSelected,
            hasExpert = expertSelected,
            hasMaster = masterSelected,
        )
    }
    val hasAnyUpgrade = packageDef.basicSpellcastingOptionId != null ||
        packageDef.expertSpellcastingOptionId != null ||
        packageDef.masterSpellcastingOptionId != null

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = packageDef.label,
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(
                onClick = {
                    state.toggleBuildOption(packageDef, ArchetypeTier.DEDICATION, turnOn = false)
                },
            ) {
                Text(
                    text = "Remove",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Text(
            text = slotSummary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hasAnyUpgrade) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (packageDef.basicSpellcastingOptionId != null) {
                    FilterChip(
                        selected = basicSelected,
                        onClick = {
                            state.toggleBuildOption(packageDef, ArchetypeTier.BASIC, !basicSelected)
                        },
                        label = { Text("Basic") },
                    )
                }
                if (packageDef.expertSpellcastingOptionId != null) {
                    FilterChip(
                        selected = expertSelected,
                        onClick = {
                            state.toggleBuildOption(packageDef, ArchetypeTier.EXPERT, !expertSelected)
                        },
                        label = { Text("Expert") },
                    )
                }
                if (packageDef.masterSpellcastingOptionId != null) {
                    FilterChip(
                        selected = masterSelected,
                        onClick = {
                            state.toggleBuildOption(packageDef, ArchetypeTier.MASTER, !masterSelected)
                        },
                        label = { Text("Master") },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchetypePickerDialog(
    available: List<ArchetypeSpellcastingPackage>,
    onPick: (ArchetypeSpellcastingPackage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Archetype") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(available, key = { it.archetypeId }) { pkg ->
                    Text(
                        text = pkg.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button) { onPick(pkg) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PreRemasterToggle(state: CharacterEditorState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Pre-Remaster Terms")
            Switch(
                checked = state.legacyEnabled,
                onCheckedChange = { state.legacyEnabled = it },
            )
        }
        Text(
            text = "Show pre-Remaster spell names (Heal becomes Soothe, etc.).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourcePickerDialog(
    state: CharacterEditorState,
    availableSpellSources: List<String>,
    onDismiss: () -> Unit,
) {
    var sourceSearchQuery by remember { mutableStateOf("") }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val selectedSpellSources = remember(availableSpellSources, state.acceptedSourceBooks) {
        availableSpellSources.filter { it in state.acceptedSourceBooks }
    }
    val filteredSpellSources = remember(availableSpellSources, state.acceptedSourceBooks, sourceSearchQuery) {
        val query = sourceSearchQuery.trim()
        availableSpellSources.filter { sourceBook ->
            sourceBook !in state.acceptedSourceBooks &&
                (query.isEmpty() || sourceBook.contains(query, ignoreCase = true))
        }
    }
    val close = {
        sourceSearchQuery = ""
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("Accepted Sources") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { state.acceptedSourceBooks = availableSpellSources.toSet() }) {
                        Text("All")
                    }
                    TextButton(
                        onClick = {
                            state.acceptedSourceBooks = availableSpellSources
                                .filter { it in coreSpellSourceBooks }
                                .toSet()
                        },
                    ) {
                        Text("Core Only")
                    }
                    TextButton(onClick = { state.acceptedSourceBooks = emptySet() }) {
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
                SectionLabel("Selected (${selectedSpellSources.size})")
                if (selectedSpellSources.isEmpty()) {
                    Text(
                        text = "No sources selected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = screenHeightDp * 0.25f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(selectedSpellSources, key = { it }) { sourceBook ->
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
                                    checked = true,
                                    onCheckedChange = {
                                        state.acceptedSourceBooks = state.acceptedSourceBooks - sourceBook
                                    },
                                )
                            }
                        }
                    }
                }
                SectionLabel("Available (${filteredSpellSources.size})")
                LazyColumn(
                    modifier = Modifier.heightIn(max = screenHeightDp * 0.45f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (filteredSpellSources.isEmpty()) {
                        item("empty-search") {
                            Text(
                                text = if (sourceSearchQuery.isBlank()) {
                                    "All available sources are already selected."
                                } else {
                                    "No unselected sources matched that search."
                                },
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
                                checked = sourceBook in state.acceptedSourceBooks,
                                onCheckedChange = { checked ->
                                    state.acceptedSourceBooks = state.acceptedSourceBooks.toMutableSet().apply {
                                        if (checked) add(sourceBook) else remove(sourceBook)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = close) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal enum class ArchetypeTier { DEDICATION, BASIC, EXPERT, MASTER }

private fun ArchetypeSpellcastingPackage.optionIdFor(tier: ArchetypeTier): String? = when (tier) {
    ArchetypeTier.DEDICATION -> dedicationOptionId
    ArchetypeTier.BASIC -> basicSpellcastingOptionId
    ArchetypeTier.EXPERT -> expertSpellcastingOptionId
    ArchetypeTier.MASTER -> masterSpellcastingOptionId
}

private fun toggleArchetypeTier(
    packageDef: ArchetypeSpellcastingPackage,
    tier: ArchetypeTier,
    currentIds: Set<String>,
    turnOn: Boolean,
): Set<String> {
    val next = currentIds.toMutableSet()
    val tiers = ArchetypeTier.values()
    if (turnOn) {
        tiers.takeWhile { it != tier }.plus(tier).forEach { t ->
            packageDef.optionIdFor(t)?.let { next += it }
        }
    } else {
        tiers.dropWhile { it != tier }.forEach { t ->
            packageDef.optionIdFor(t)?.let { next -= it }
        }
    }
    return next
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
