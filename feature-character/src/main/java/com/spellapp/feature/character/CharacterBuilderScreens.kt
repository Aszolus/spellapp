package com.spellapp.feature.character

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.spellapp.core.model.AbilityScore
import com.spellapp.core.model.ClassChoice
import com.spellapp.core.model.ClassChoiceGroup

private val coreSourceBooksForBuilder = setOf(
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
fun CharacterBuilderRoute(
    uiState: CharacterBuilderUiState,
    onNameChange: (String) -> Unit,
    onLevelChange: (String) -> Unit,
    onAncestrySelected: (String) -> Unit,
    onHeritageSelected: (String) -> Unit,
    onBackgroundSelected: (String) -> Unit,
    onClassSelected: (String) -> Unit,
    onClassChoiceSelected: (ClassChoiceGroup, ClassChoice) -> Unit,
    onKeyAbilitySelected: (AbilityScore) -> Unit,
    onAbilityBoostSelected: (String, AbilityScore?) -> Unit,
    onVoluntaryFlawEnabledChange: (Boolean) -> Unit,
    onSkillChoiceSelected: (String, String?) -> Unit,
    onLoreSkillChoiceSelected: (String, String) -> Unit,
    onFeatSelected: (String, String?, String?) -> Unit,
    onFeatPickerOpen: (String) -> Unit,
    onFeatPickerDismiss: () -> Unit,
    onSpellDcChange: (String) -> Unit,
    onSpellAttackChange: (String) -> Unit,
    onAcceptedSourcesChange: (Set<String>) -> Unit,
    onSourceBookToggle: (String, Boolean) -> Unit,
    onArchetypeTierToggle: (ArchetypeSpellcastingPackage, ArchetypeTier, Boolean) -> Unit,
    onLegacyTerminologyChange: (Boolean) -> Unit,
    onSectionToggle: (CharacterBuilderSectionId) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showDiscardConfirm by remember { mutableStateOf(false) }
    val requestBack = {
        if (uiState.isDirty && !uiState.isSaving) {
            showDiscardConfirm = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = true) {
        requestBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.isNewCharacter) "Create Character" else "Edit Character")
                },
                navigationIcon = {
                    TextButton(onClick = requestBack) {
                        Text("Back")
                    }
                },
            )
        },
        bottomBar = {
            CharacterBuilderSaveBar(
                isSaving = uiState.isSaving,
                canAttemptSave = !uiState.isLoading && uiState.loadError == null,
                onCancel = requestBack,
                onSave = onSave,
            )
        },
    ) { innerPadding ->
        CharacterBuilderContent(
            uiState = uiState,
            onNameChange = onNameChange,
            onLevelChange = onLevelChange,
            onAncestrySelected = onAncestrySelected,
            onHeritageSelected = onHeritageSelected,
            onBackgroundSelected = onBackgroundSelected,
            onClassSelected = onClassSelected,
            onClassChoiceSelected = onClassChoiceSelected,
            onKeyAbilitySelected = onKeyAbilitySelected,
            onAbilityBoostSelected = onAbilityBoostSelected,
            onVoluntaryFlawEnabledChange = onVoluntaryFlawEnabledChange,
            onSkillChoiceSelected = onSkillChoiceSelected,
            onLoreSkillChoiceSelected = onLoreSkillChoiceSelected,
            onFeatSelected = onFeatSelected,
            onFeatPickerOpen = onFeatPickerOpen,
            onFeatPickerDismiss = onFeatPickerDismiss,
            onSpellDcChange = onSpellDcChange,
            onSpellAttackChange = onSpellAttackChange,
            onAcceptedSourcesChange = onAcceptedSourcesChange,
            onSourceBookToggle = onSourceBookToggle,
            onArchetypeTierToggle = onArchetypeTierToggle,
            onLegacyTerminologyChange = onLegacyTerminologyChange,
            onSectionToggle = onSectionToggle,
            modifier = Modifier.padding(innerPadding),
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard Changes?") },
            text = {
                Text("Unsaved character changes will be lost.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onBack()
                    },
                ) {
                    Text(
                        text = "Discard",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep Editing")
                }
            },
        )
    }
}

@Composable
private fun CharacterBuilderContent(
    uiState: CharacterBuilderUiState,
    onNameChange: (String) -> Unit,
    onLevelChange: (String) -> Unit,
    onAncestrySelected: (String) -> Unit,
    onHeritageSelected: (String) -> Unit,
    onBackgroundSelected: (String) -> Unit,
    onClassSelected: (String) -> Unit,
    onClassChoiceSelected: (ClassChoiceGroup, ClassChoice) -> Unit,
    onKeyAbilitySelected: (AbilityScore) -> Unit,
    onAbilityBoostSelected: (String, AbilityScore?) -> Unit,
    onVoluntaryFlawEnabledChange: (Boolean) -> Unit,
    onSkillChoiceSelected: (String, String?) -> Unit,
    onLoreSkillChoiceSelected: (String, String) -> Unit,
    onFeatSelected: (String, String?, String?) -> Unit,
    onFeatPickerOpen: (String) -> Unit,
    onFeatPickerDismiss: () -> Unit,
    onSpellDcChange: (String) -> Unit,
    onSpellAttackChange: (String) -> Unit,
    onAcceptedSourcesChange: (Set<String>) -> Unit,
    onSourceBookToggle: (String, Boolean) -> Unit,
    onArchetypeTierToggle: (ArchetypeSpellcastingPackage, ArchetypeTier, Boolean) -> Unit,
    onLegacyTerminologyChange: (Boolean) -> Unit,
    onSectionToggle: (CharacterBuilderSectionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Loading character...")
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.loadError != null) {
            item("load-error") {
                Text(
                    text = uiState.loadError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            return@LazyColumn
        }
        item("intro") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Character Setup",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Set up the spell-management parts of this character. Other build areas stay out of the way until they are supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(uiState.sections, key = { it.id.name }) { section ->
            CharacterBuilderSection(
                section = section,
                expanded = uiState.expandedSection == section.id,
                onToggle = { onSectionToggle(section.id) },
            ) {
                when (section.id) {
                    CharacterBuilderSectionId.IDENTITY -> IdentitySection(
                        uiState = uiState,
                        onNameChange = onNameChange,
                        onLevelChange = onLevelChange,
                    )

                    CharacterBuilderSectionId.ANCESTRY_HERITAGE -> AncestryHeritageSection(
                        uiState = uiState,
                        onAncestrySelected = onAncestrySelected,
                        onHeritageSelected = onHeritageSelected,
                    )

                    CharacterBuilderSectionId.BACKGROUND -> BackgroundSection(
                        uiState = uiState,
                        onBackgroundSelected = onBackgroundSelected,
                    )

                    CharacterBuilderSectionId.CLASS_SPELLCASTING -> ClassSpellcastingSection(
                        uiState = uiState,
                        onClassSelected = onClassSelected,
                        onClassChoiceSelected = onClassChoiceSelected,
                        onKeyAbilitySelected = onKeyAbilitySelected,
                    )

                    CharacterBuilderSectionId.ABILITY_SCORES -> AbilityScoresSection(
                        uiState = uiState,
                        onAbilityBoostSelected = onAbilityBoostSelected,
                        onVoluntaryFlawEnabledChange = onVoluntaryFlawEnabledChange,
                    )

                    CharacterBuilderSectionId.SKILLS -> SkillsSection(
                        uiState = uiState,
                        onSkillChoiceSelected = onSkillChoiceSelected,
                        onLoreSkillChoiceSelected = onLoreSkillChoiceSelected,
                    )

                    CharacterBuilderSectionId.FEATS -> FeatsSection(
                        uiState = uiState,
                        onFeatSelected = onFeatSelected,
                        onFeatPickerOpen = onFeatPickerOpen,
                        onFeatPickerDismiss = onFeatPickerDismiss,
                    )

                    CharacterBuilderSectionId.CASTING_STATS -> CastingStatsSection(
                        uiState = uiState,
                        onSpellDcChange = onSpellDcChange,
                        onSpellAttackChange = onSpellAttackChange,
                    )

                    CharacterBuilderSectionId.SPELL_SOURCES -> SpellSourcesSection(
                        uiState = uiState,
                        onAcceptedSourcesChange = onAcceptedSourcesChange,
                        onSourceBookToggle = onSourceBookToggle,
                    )

                    CharacterBuilderSectionId.ARCHETYPE_SPELLCASTING -> ArchetypeSpellcastingBuilderSection(
                        uiState = uiState,
                        onArchetypeTierToggle = onArchetypeTierToggle,
                    )

                    CharacterBuilderSectionId.PREFERENCES -> PreferencesSection(
                        uiState = uiState,
                        onLegacyTerminologyChange = onLegacyTerminologyChange,
                    )
                }
            }
        }
        if (uiState.saveError != null) {
            item("save-error") {
                Text(
                    text = uiState.saveError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CharacterBuilderSection(
    section: CharacterBuilderSectionSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = section.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = section.status.label(),
                style = MaterialTheme.typography.labelMedium,
                color = section.status.color(),
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
            )
        }
        section.validationMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun IdentitySection(
    uiState: CharacterBuilderUiState,
    onNameChange: (String) -> Unit,
    onLevelChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChange,
        label = { Text("Name") },
        placeholder = { Text("e.g. Elara Nightweave") },
        singleLine = true,
        isError = uiState.saveAttempted && uiState.nameInvalid,
        supportingText = if (uiState.saveAttempted && uiState.nameInvalid) {
            { Text("Name is required.") }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = uiState.levelText,
        onValueChange = onLevelChange,
        label = { Text("Level (1-20)") },
        singleLine = true,
        isError = uiState.saveAttempted && uiState.levelInvalid,
        supportingText = if (uiState.saveAttempted && uiState.levelInvalid) {
            { Text("Enter a level between 1 and 20.") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AncestryHeritageSection(
    uiState: CharacterBuilderUiState,
    onAncestrySelected: (String) -> Unit,
    onHeritageSelected: (String) -> Unit,
) {
    var picker by remember { mutableStateOf<BuilderPickerKind?>(null) }
    val selectedAncestry = uiState.selectedAncestryId
        ?.let { id -> uiState.availableAncestries.firstOrNull { it.id == id } }
    val selectedHeritage = uiState.selectedHeritageId
        ?.let { id -> uiState.availableHeritages.firstOrNull { it.id == id } }

    BuilderSelectionRow(
        label = "Ancestry",
        value = selectedAncestry?.name ?: "None selected",
        onChoose = { picker = BuilderPickerKind.ANCESTRY },
    )
    BuilderSelectionRow(
        label = "Heritage",
        value = selectedHeritage?.name ?: if (selectedAncestry == null) "Choose ancestry first" else "None selected",
        enabled = selectedAncestry != null,
        onChoose = { picker = BuilderPickerKind.HERITAGE },
    )
    selectedAncestry?.let { ancestry ->
        Text(
            text = listOfNotNull(
                ancestry.hp?.let { "HP $it" },
                ancestry.size.ifBlank { null },
                ancestry.traits.rarity.takeIf { it != "common" },
            ).joinToString(" · ").ifBlank { ancestry.source.title.orEmpty() },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    BuilderWarnings(uiState.builderWarningLines)

    when (picker) {
        BuilderPickerKind.ANCESTRY -> BuilderPickerDialog(
            title = "Choose Ancestry",
            items = uiState.availableAncestries.map {
                BuilderPickerItem(it.id, it.name, it.source.title.orEmpty())
            },
            onPick = { item ->
                onAncestrySelected(item.id)
                picker = null
            },
            onDismiss = { picker = null },
        )

        BuilderPickerKind.HERITAGE -> BuilderPickerDialog(
            title = "Choose Heritage",
            items = uiState.availableHeritages.map {
                BuilderPickerItem(it.id, it.name, it.source.title.orEmpty())
            },
            onPick = { item ->
                onHeritageSelected(item.id)
                picker = null
            },
            onDismiss = { picker = null },
        )

        null -> Unit
    }
}

@Composable
private fun BackgroundSection(
    uiState: CharacterBuilderUiState,
    onBackgroundSelected: (String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val selectedBackground = uiState.selectedBackgroundId
        ?.let { id -> uiState.availableBackgrounds.firstOrNull { it.id == id } }
    BuilderSelectionRow(
        label = "Background",
        value = selectedBackground?.name ?: "None selected",
        onChoose = { showPicker = true },
    )
    selectedBackground?.let { background ->
        Text(
            text = listOf(
                background.source.title.orEmpty(),
                background.grants.mapNotNull { it.name }.take(2).joinToString(prefix = "Grants: "),
            ).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    BuilderWarnings(uiState.builderWarningLines)
    if (showPicker) {
        BuilderPickerDialog(
            title = "Choose Background",
            items = uiState.availableBackgrounds.map {
                BuilderPickerItem(it.id, it.name, it.source.title.orEmpty())
            },
            onPick = { item ->
                onBackgroundSelected(item.id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassSpellcastingSection(
    uiState: CharacterBuilderUiState,
    onClassSelected: (String) -> Unit,
    onClassChoiceSelected: (ClassChoiceGroup, ClassChoice) -> Unit,
    onKeyAbilitySelected: (AbilityScore) -> Unit,
) {
    SectionLabel("Class")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        uiState.availableClasses.forEach { definition ->
            FilterChip(
                selected = uiState.selectedClassId == definition.classId,
                onClick = { onClassSelected(definition.classId) },
                label = { Text(definition.label) },
            )
        }
    }

    uiState.classChoiceGroups.forEach { group ->
        SectionLabel(if (group.required) "${group.label} (Required)" else group.label)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            group.choices.forEach { choice ->
                FilterChip(
                    selected = choice.optionId in uiState.selectedBuildOptionIds,
                    onClick = { onClassChoiceSelected(group, choice) },
                    label = { Text(choice.label) },
                )
            }
        }
    }

    SectionLabel("Key Ability")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keyAbilityOptions(
            classId = uiState.selectedClassId,
            classDefinitions = uiState.classDefinitionsByClass,
        ).forEach { ability ->
            FilterChip(
                selected = uiState.keyAbility == ability,
                onClick = { onKeyAbilitySelected(ability) },
                label = { Text(ability.label()) },
            )
        }
    }

    if (uiState.classPreviewLines.isNotEmpty()) {
        SectionLabel("Spellcasting Preview")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            uiState.classPreviewLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityScoresSection(
    uiState: CharacterBuilderUiState,
    onAbilityBoostSelected: (String, AbilityScore?) -> Unit,
    onVoluntaryFlawEnabledChange: (Boolean) -> Unit,
) {
    val activeLevel = uiState.level ?: 1
    uiState.buildFacts?.let { facts ->
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            facts.abilityScores.forEach { (ability, score) ->
                Text(
                    text = "${ability.label()} $score (${facts.abilityModifiers[ability]?.withSign() ?: "+0"})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        facts.hp?.let { hp ->
            Text(
                text = "HP $hp",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Voluntary flaw", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Adds optional flaw/boost slots at level 1.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = uiState.voluntaryFlawEnabled,
            onCheckedChange = onVoluntaryFlawEnabledChange,
        )
    }
    val slotsByLevel = uiState.abilityBoostSlots.groupBy { it.level }
    slotsByLevel.keys.sorted().forEach { level ->
        SectionLabel(if (level <= activeLevel) "Level $level active boosts" else "Level $level planned boosts")
        slotsByLevel.getValue(level).forEach { slot ->
            AbilityBoostSlotRow(
                slot = slot,
                selected = slot.fixedChoice ?: uiState.selectedAbilityBoosts[slot.slotId],
                enabled = slot.fixedChoice == null,
                onAbilityBoostSelected = onAbilityBoostSelected,
            )
        }
    }
    uiState.abilityIssues.take(4).forEach { issue ->
        Text(
            text = issue.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (issue.active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityBoostSlotRow(
    slot: BuilderAbilityBoostSlot,
    selected: AbilityScore?,
    enabled: Boolean,
    onAbilityBoostSelected: (String, AbilityScore?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = slot.label,
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            slot.choices.forEach { ability ->
                FilterChip(
                    selected = selected == ability,
                    onClick = { onAbilityBoostSelected(slot.slotId, ability) },
                    enabled = enabled,
                    label = { Text(ability.label()) },
                )
            }
        }
    }
}

@Composable
private fun SkillsSection(
    uiState: CharacterBuilderUiState,
    onSkillChoiceSelected: (String, String?) -> Unit,
    onLoreSkillChoiceSelected: (String, String) -> Unit,
) {
    val activeLevel = uiState.level ?: 1
    var activeSlot by remember { mutableStateOf<BuilderSkillChoiceSlot?>(null) }
    uiState.buildFacts?.let { facts ->
        SectionLabel("Current Skill Totals")
        val trained = facts.skillRanks
            .filterValues { it.value > 0 }
            .toSortedMap()
        if (trained.isEmpty()) {
            Text(
                text = "No trained skills yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                trained.entries.take(16).forEach { (skillId, rank) ->
                    val total = facts.skillTotals[skillId]?.withSign().orEmpty()
                    Text(
                        text = "${BuilderRules.displaySkillName(skillId)}: ${rank.label} $total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    val slotsByLevel = uiState.skillChoiceSlots.groupBy { it.level }
    slotsByLevel.keys.sorted().forEach { level ->
        SectionLabel(if (level <= activeLevel) "Level $level active skill choices" else "Level $level planned skill choices")
        slotsByLevel.getValue(level).forEach { slot ->
            val selected = uiState.selectedSkillChoices[slot.slotId]
            BuilderSelectionRow(
                label = slot.label,
                value = selected?.let(BuilderRules::displaySkillName) ?: "No skill selected",
                onChoose = { activeSlot = slot },
            )
        }
    }
    uiState.skillIssues.take(4).forEach { issue ->
        Text(
            text = issue.message,
            style = MaterialTheme.typography.bodySmall,
            color = if (issue.active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    activeSlot?.let { slot ->
        SkillChoicePickerDialog(
            slot = slot,
            onPick = { skillId ->
                onSkillChoiceSelected(slot.slotId, skillId)
                activeSlot = null
            },
            onLorePick = { loreName ->
                onLoreSkillChoiceSelected(slot.slotId, loreName)
                activeSlot = null
            },
            onClear = {
                onSkillChoiceSelected(slot.slotId, null)
                activeSlot = null
            },
            onDismiss = { activeSlot = null },
        )
    }
}

@Composable
private fun FeatsSection(
    uiState: CharacterBuilderUiState,
    onFeatSelected: (String, String?, String?) -> Unit,
    onFeatPickerOpen: (String) -> Unit,
    onFeatPickerDismiss: () -> Unit,
) {
    if (uiState.expectedFeatSlots.isEmpty()) {
        Text(
            text = "No feat slots are expected for the selected class.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val activeLevel = uiState.level ?: 1
    if (uiState.isLoadingFeatDetails) {
        Text(
            text = "Loading feat details...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..20).forEach { level ->
            val slots = uiState.expectedFeatSlots.filter { it.level == level }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (level <= activeLevel) "Level $level" else "Level $level planned",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (level <= activeLevel) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (slots.isEmpty()) {
                    Text(
                        text = "No tracked builder choices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    slots.forEach { slot ->
                        val selectedFeatId = uiState.selectedFeatSlotOptions[slot.slotId]
                        val selectedFeat = selectedFeatId?.let(uiState.featsById::get)
                        val selectedLegality = selectedFeatId
                            ?.let { uiState.featLegalityBySlotId[slot.slotId]?.get(it) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "${slot.kind.replaceFirstChar { it.uppercase() }} feat",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = listOfNotNull(
                                        selectedFeat?.name ?: "No feat selected",
                                        selectedLegality?.status?.label()?.takeIf { selectedFeat != null },
                                    ).joinToString(" - "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (selectedLegality?.status) {
                                        BuilderLegalityStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
                                        BuilderLegalityStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            TextButton(
                                onClick = { onFeatPickerOpen(slot.slotId) },
                                enabled = !uiState.isLoadingFeatDetails,
                            ) {
                                Text(if (selectedFeat == null) "Choose" else "Change")
                            }
                        }
                    }
                }
            }
        }
    }
    BuilderWarnings(uiState.builderWarningLines)
    uiState.activeFeatPickerSlotId
        ?.let { slotId -> uiState.expectedFeatSlots.firstOrNull { it.slotId == slotId } }
        ?.let { slot ->
        FeatPickerDialog(
            title = "Choose ${slot.kind.replaceFirstChar { it.uppercase() }} Feat",
            feats = uiState.activeFeatPickerCandidates,
            legalityByFeatId = uiState.activeFeatPickerLegalityByFeatId,
            loading = uiState.isPreparingFeatPicker,
            onClear = {
                onFeatSelected(slot.slotId, null, null)
                onFeatPickerDismiss()
            },
            onPick = { feat, overrideReason ->
                onFeatSelected(slot.slotId, feat.id, overrideReason)
                onFeatPickerDismiss()
            },
            onDismiss = onFeatPickerDismiss,
        )
    }
}

@Composable
private fun CastingStatsSection(
    uiState: CharacterBuilderUiState,
    onSpellDcChange: (String) -> Unit,
    onSpellAttackChange: (String) -> Unit,
) {
    Text(
        text = "Use the current values from the player's character sheet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = uiState.spellDcText,
        onValueChange = onSpellDcChange,
        label = { Text("Spell DC") },
        singleLine = true,
        isError = uiState.saveAttempted && uiState.spellDcInvalid,
        supportingText = if (uiState.saveAttempted && uiState.spellDcInvalid) {
            { Text("Enter a DC between 0 and 99.") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = uiState.spellAttackText,
        onValueChange = onSpellAttackChange,
        label = { Text("Spell Attack Modifier") },
        singleLine = true,
        isError = uiState.saveAttempted && uiState.spellAttackInvalid,
        supportingText = if (uiState.saveAttempted && uiState.spellAttackInvalid) {
            { Text("Enter a modifier between -99 and 99.") }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SpellSourcesSection(
    uiState: CharacterBuilderUiState,
    onAcceptedSourcesChange: (Set<String>) -> Unit,
    onSourceBookToggle: (String, Boolean) -> Unit,
) {
    var showSourcePicker by remember { mutableStateOf(false) }
    if (uiState.availableSpellSources.isEmpty()) {
        Text(
            text = "No sources are available yet.",
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
                text = "${uiState.acceptedSourceBooks.size} of ${uiState.availableSpellSources.size} selected",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { showSourcePicker = true }) {
                Text("Choose")
            }
        }
    }

    if (showSourcePicker) {
        BuilderSourcePickerDialog(
            uiState = uiState,
            onAcceptedSourcesChange = onAcceptedSourcesChange,
            onSourceBookToggle = onSourceBookToggle,
            onDismiss = { showSourcePicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchetypeSpellcastingBuilderSection(
    uiState: CharacterBuilderUiState,
    onArchetypeTierToggle: (ArchetypeSpellcastingPackage, ArchetypeTier, Boolean) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    if (uiState.selectedArchetypePackages.isEmpty()) {
        Text(
            text = "No spellcasting archetypes selected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        uiState.selectedArchetypePackages.forEach { packageDef ->
            SelectedBuilderArchetypeRow(
                uiState = uiState,
                packageDef = packageDef,
                onArchetypeTierToggle = onArchetypeTierToggle,
            )
        }
    }
    if (uiState.availableArchetypePackages.isNotEmpty()) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.selectedArchetypePackages.isEmpty()) "Add Archetype" else "Add Another Archetype")
        }
    }

    if (showPicker) {
        BuilderArchetypePickerDialog(
            available = uiState.availableArchetypePackages,
            onPick = { picked ->
                onArchetypeTierToggle(picked, ArchetypeTier.DEDICATION, true)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedBuilderArchetypeRow(
    uiState: CharacterBuilderUiState,
    packageDef: ArchetypeSpellcastingPackage,
    onArchetypeTierToggle: (ArchetypeSpellcastingPackage, ArchetypeTier, Boolean) -> Unit,
) {
    val basicSelected = packageDef.basicSpellcastingOptionId
        ?.let { it in uiState.selectedBuildOptionIds } ?: false
    val expertSelected = packageDef.expertSpellcastingOptionId
        ?.let { it in uiState.selectedBuildOptionIds } ?: false
    val masterSelected = packageDef.masterSpellcastingOptionId
        ?.let { it in uiState.selectedBuildOptionIds } ?: false
    val slotSummary = summarizeArchetypeSlotsForLevel(
        level = uiState.level ?: 1,
        hasBasic = basicSelected,
        hasExpert = expertSelected,
        hasMaster = masterSelected,
    )
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
                    onArchetypeTierToggle(packageDef, ArchetypeTier.DEDICATION, false)
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
                            onArchetypeTierToggle(packageDef, ArchetypeTier.BASIC, !basicSelected)
                        },
                        label = { Text("Basic") },
                    )
                }
                if (packageDef.expertSpellcastingOptionId != null) {
                    FilterChip(
                        selected = expertSelected,
                        onClick = {
                            onArchetypeTierToggle(packageDef, ArchetypeTier.EXPERT, !expertSelected)
                        },
                        label = { Text("Expert") },
                    )
                }
                if (packageDef.masterSpellcastingOptionId != null) {
                    FilterChip(
                        selected = masterSelected,
                        onClick = {
                            onArchetypeTierToggle(packageDef, ArchetypeTier.MASTER, !masterSelected)
                        },
                        label = { Text("Master") },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferencesSection(
    uiState: CharacterBuilderUiState,
    onLegacyTerminologyChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Pre-remaster Names",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Show legacy spell names when the dataset provides them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = uiState.legacyTerminologyEnabled,
            onCheckedChange = onLegacyTerminologyChange,
        )
    }
}

@Composable
private fun CharacterBuilderSaveBar(
    isSaving: Boolean,
    canAttemptSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onCancel,
                enabled = !isSaving,
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onSave,
                enabled = canAttemptSave && !isSaving,
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        }
    }
}

@Composable
private fun BuilderSourcePickerDialog(
    uiState: CharacterBuilderUiState,
    onAcceptedSourcesChange: (Set<String>) -> Unit,
    onSourceBookToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var sourceSearchQuery by remember { mutableStateOf("") }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val selectedSpellSources = remember(uiState.availableSpellSources, uiState.acceptedSourceBooks) {
        uiState.availableSpellSources.filter { it in uiState.acceptedSourceBooks }
    }
    val filteredSpellSources = remember(
        uiState.availableSpellSources,
        uiState.acceptedSourceBooks,
        sourceSearchQuery,
    ) {
        val query = sourceSearchQuery.trim()
        uiState.availableSpellSources.filter { sourceBook ->
            sourceBook !in uiState.acceptedSourceBooks &&
                (query.isEmpty() || sourceBook.contains(query, ignoreCase = true))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sources") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { onAcceptedSourcesChange(uiState.availableSpellSources.toSet()) }) {
                        Text("All")
                    }
                    TextButton(
                        onClick = {
                            onAcceptedSourcesChange(
                                uiState.availableSpellSources
                                    .filter { sourceBook ->
                                        coreSourceBooksForBuilder.any { coreSource ->
                                            coreSource.sourceBookKey() == sourceBook.sourceBookKey()
                                        }
                                    }
                                    .toSet(),
                            )
                        },
                    ) {
                        Text("Core Only")
                    }
                    TextButton(onClick = { onAcceptedSourcesChange(emptySet()) }) {
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
                            SourceBookRow(
                                sourceBook = sourceBook,
                                checked = true,
                                onCheckedChange = { checked -> onSourceBookToggle(sourceBook, checked) },
                            )
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
                        SourceBookRow(
                            sourceBook = sourceBook,
                            checked = false,
                            onCheckedChange = { checked -> onSourceBookToggle(sourceBook, checked) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun SourceBookRow(
    sourceBook: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = sourceBook,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun BuilderSelectionRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onChoose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onChoose,
            enabled = enabled,
        ) {
            Text("Choose")
        }
    }
}

@Composable
private fun BuilderWarnings(lines: List<String>) {
    if (lines.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.take(4).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SkillChoicePickerDialog(
    slot: BuilderSkillChoiceSlot,
    onPick: (String) -> Unit,
    onLorePick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var loreName by remember { mutableStateOf("") }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val filtered = remember(slot, query) {
        val normalized = query.trim()
        BuilderRules.standardSkills
            .filter { skill -> skill.id in slot.choices }
            .filter { skill ->
                normalized.isBlank() ||
                    skill.label.contains(normalized, ignoreCase = true)
            }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slot.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search skills") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = screenHeightDp * 0.35f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered, key = { it.id }) { skill ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onPick(skill.id) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(skill.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = skill.ability.label(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (slot.allowLore) {
                    SectionLabel("Lore")
                    OutlinedTextField(
                        value = loreName,
                        onValueChange = { loreName = it },
                        label = { Text("Lore skill name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { onLorePick(loreName) },
                        enabled = loreName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Use Lore")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) {
                Text("Clear")
            }
        },
    )
}

@Composable
private fun FeatPickerDialog(
    title: String,
    feats: List<BuilderFeatRecord>,
    legalityByFeatId: Map<String, BuilderFeatLegality>,
    loading: Boolean,
    onClear: () -> Unit,
    onPick: (BuilderFeatRecord, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedFeatId by remember { mutableStateOf<String?>(null) }
    var overrideReason by remember { mutableStateOf("") }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val filtered = remember(feats, query) {
        val normalized = query.trim()
        feats.filter { feat ->
            normalized.isBlank() ||
                feat.name.contains(normalized, ignoreCase = true) ||
                feat.traits.any { trait -> trait.contains(normalized, ignoreCase = true) }
        }
    }
    val selectedFeat = selectedFeatId?.let { id -> filtered.firstOrNull { it.id == id } }
    val selectedLegality = selectedFeatId?.let(legalityByFeatId::get)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search feats") },
                    singleLine = true,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = screenHeightDp * 0.38f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (loading) {
                        item("loading") {
                            Text(
                                text = "Preparing feat choices...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (filtered.isEmpty()) {
                        item("empty") {
                            Text(
                                text = "No feat choices matched.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!loading) BuilderLegalityStatus.values().forEach { status ->
                        val group = filtered.filter { feat ->
                            legalityByFeatId[feat.id]?.status == status
                        }
                        if (group.isNotEmpty()) {
                            item("${status.name}-label") {
                                SectionLabel("${status.label()} (${group.size})")
                            }
                            items(group, key = { it.id }) { feat ->
                                val legality = legalityByFeatId[feat.id]
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(role = Role.Button) { selectedFeatId = feat.id }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = feat.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "Level ${feat.level} - ${feat.rarity} - ${feat.traits.take(3).joinToString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when (legality?.status) {
                                            BuilderLegalityStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
                                            BuilderLegalityStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                selectedFeat?.let { feat ->
                    HorizontalDivider()
                    Text(feat.name, style = MaterialTheme.typography.titleSmall)
                    if (feat.prerequisites.isNotEmpty()) {
                        Text(
                            text = "Prerequisites: ${feat.prerequisites.joinToString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    selectedLegality?.reasons.orEmpty().take(3).forEach { reason ->
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    selectedLegality?.warnings.orEmpty().take(3).forEach { warning ->
                        Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    if (selectedLegality?.requiresOverride == true) {
                        OutlinedTextField(
                            value = overrideReason,
                            onValueChange = { overrideReason = it },
                            label = { Text("Override reason") },
                            singleLine = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = feat.description.take(700),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedFeat?.let { feat ->
                        onPick(
                            feat,
                            overrideReason.trim().takeIf { selectedLegality?.requiresOverride == true },
                        )
                    }
                },
                enabled = !loading &&
                    selectedFeat != null &&
                    (selectedLegality?.requiresOverride != true || overrideReason.isNotBlank()),
            ) {
                Text("Choose")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun BuilderPickerDialog(
    title: String,
    items: List<BuilderPickerItem>,
    allowClear: Boolean = false,
    onClear: () -> Unit = {},
    onPick: (BuilderPickerItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val filtered = remember(items, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            items
        } else {
            items.filter { item ->
                item.label.contains(normalized, ignoreCase = true) ||
                    item.subtitle.contains(normalized, ignoreCase = true)
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = screenHeightDp * 0.55f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (filtered.isEmpty()) {
                        item("empty") {
                            Text(
                                text = "No matches.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onPick(item) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        dismissButton = if (allowClear) {
            {
                TextButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun BuilderArchetypePickerDialog(
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private enum class BuilderPickerKind {
    ANCESTRY,
    HERITAGE,
}

private data class BuilderPickerItem(
    val id: String,
    val label: String,
    val subtitle: String,
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CharacterBuilderSectionStatus.color() = when (this) {
    CharacterBuilderSectionStatus.COMPLETE -> MaterialTheme.colorScheme.primary
    CharacterBuilderSectionStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.error
    CharacterBuilderSectionStatus.OPTIONAL -> MaterialTheme.colorScheme.onSurfaceVariant
    CharacterBuilderSectionStatus.BLOCKED -> MaterialTheme.colorScheme.error
}

private fun CharacterBuilderSectionStatus.label(): String = when (this) {
    CharacterBuilderSectionStatus.COMPLETE -> "Done"
    CharacterBuilderSectionStatus.NEEDS_REVIEW -> "Review"
    CharacterBuilderSectionStatus.OPTIONAL -> "Optional"
    CharacterBuilderSectionStatus.BLOCKED -> "Blocked"
}

private fun BuilderLegalityStatus.label(): String = when (this) {
    BuilderLegalityStatus.ELIGIBLE -> "Eligible"
    BuilderLegalityStatus.NEEDS_REVIEW -> "Needs Review"
    BuilderLegalityStatus.UNAVAILABLE -> "Unavailable"
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
