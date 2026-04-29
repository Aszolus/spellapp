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
    onPromptChoiceSelected: (String, String?) -> Unit,
    onFeatSelected: (String, String?, String?) -> Unit,
    onFeatPickerOpen: (String) -> Unit,
    onFeatPickerDismiss: () -> Unit,
    onAcceptedSourcesChange: (Set<String>) -> Unit,
    onSourceBookToggle: (String, Boolean) -> Unit,
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
            onPromptChoiceSelected = onPromptChoiceSelected,
            onFeatSelected = onFeatSelected,
            onFeatPickerOpen = onFeatPickerOpen,
            onFeatPickerDismiss = onFeatPickerDismiss,
            onAcceptedSourcesChange = onAcceptedSourcesChange,
            onSourceBookToggle = onSourceBookToggle,
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
    onPromptChoiceSelected: (String, String?) -> Unit,
    onFeatSelected: (String, String?, String?) -> Unit,
    onFeatPickerOpen: (String) -> Unit,
    onFeatPickerDismiss: () -> Unit,
    onAcceptedSourcesChange: (Set<String>) -> Unit,
    onSourceBookToggle: (String, Boolean) -> Unit,
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
                        onPromptChoiceSelected = onPromptChoiceSelected,
                    )

                    CharacterBuilderSectionId.BACKGROUND -> BackgroundSection(
                        uiState = uiState,
                        onBackgroundSelected = onBackgroundSelected,
                        onPromptChoiceSelected = onPromptChoiceSelected,
                    )

                    CharacterBuilderSectionId.CLASS_SPELLCASTING -> ClassSpellcastingSection(
                        uiState = uiState,
                        onClassSelected = onClassSelected,
                        onClassChoiceSelected = onClassChoiceSelected,
                        onKeyAbilitySelected = onKeyAbilitySelected,
                        onPromptChoiceSelected = onPromptChoiceSelected,
                    )

                    CharacterBuilderSectionId.ABILITY_SCORES -> AbilityScoresSection(
                        uiState = uiState,
                        onAbilityBoostSelected = onAbilityBoostSelected,
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
                        onAbilityBoostSelected = onAbilityBoostSelected,
                        onSkillChoiceSelected = onSkillChoiceSelected,
                        onLoreSkillChoiceSelected = onLoreSkillChoiceSelected,
                    )

                    CharacterBuilderSectionId.SPELL_SOURCES -> SpellSourcesSection(
                        uiState = uiState,
                        onAcceptedSourcesChange = onAcceptedSourcesChange,
                        onSourceBookToggle = onSourceBookToggle,
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
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
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
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Number,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AncestryHeritageSection(
    uiState: CharacterBuilderUiState,
    onAncestrySelected: (String) -> Unit,
    onHeritageSelected: (String) -> Unit,
    onPromptChoiceSelected: (String, String?) -> Unit,
) {
    var picker by remember { mutableStateOf<BuilderPickerKind?>(null) }
    var detailDialog by remember { mutableStateOf<BuilderDetailDialogContent?>(null) }
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
        BuilderDetailBlock(
            title = ancestry.name,
            lines = ancestry.detailLines(),
            description = ancestry.description,
            onDetails = {
                detailDialog = BuilderDetailDialogContent(
                    title = ancestry.name,
                    lines = ancestry.detailLines(),
                    description = ancestry.description,
                )
            },
        )
    }
    selectedHeritage?.let { heritage ->
        BuilderDetailBlock(
            title = heritage.name,
            lines = heritage.detailLines(),
            description = heritage.description,
            onDetails = {
                detailDialog = BuilderDetailDialogContent(
                    title = heritage.name,
                    lines = heritage.detailLines(),
                    description = heritage.description,
                )
            },
        )
    }
    PromptChoices(
        slots = uiState.promptSlots.filter {
            it.source == BuilderPromptSource.ANCESTRY || it.source == BuilderPromptSource.HERITAGE
        },
        selectedPromptChoices = uiState.selectedPromptChoices,
        issues = uiState.promptIssues,
        onPromptChoiceSelected = onPromptChoiceSelected,
    )

    when (picker) {
        BuilderPickerKind.ANCESTRY -> BuilderPickerDialog(
            title = "Choose Ancestry",
            items = uiState.availableAncestries.map {
                BuilderPickerItem(it.id, it.name, it.pickerSubtitle())
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
                BuilderPickerItem(it.id, it.name, it.pickerSubtitle())
            },
            onPick = { item ->
                onHeritageSelected(item.id)
                picker = null
            },
            onDismiss = { picker = null },
        )

        null -> Unit
    }
    detailDialog?.let { content ->
        BuilderDetailDialog(
            content = content,
            onDismiss = { detailDialog = null },
        )
    }
}

@Composable
private fun BackgroundSection(
    uiState: CharacterBuilderUiState,
    onBackgroundSelected: (String) -> Unit,
    onPromptChoiceSelected: (String, String?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var detailDialog by remember { mutableStateOf<BuilderDetailDialogContent?>(null) }
    val selectedBackground = uiState.selectedBackgroundId
        ?.let { id -> uiState.availableBackgrounds.firstOrNull { it.id == id } }
    BuilderSelectionRow(
        label = "Background",
        value = selectedBackground?.name ?: "None selected",
        onChoose = { showPicker = true },
    )
    selectedBackground?.let { background ->
        BuilderDetailBlock(
            title = background.name,
            lines = background.summaryLines(),
            description = background.description,
            onDetails = {
                detailDialog = BuilderDetailDialogContent(
                    title = background.name,
                    lines = background.detailLines(),
                    description = background.description,
                )
            },
        )
        BackgroundGrantedResults(background)
    }
    PromptChoices(
        slots = uiState.promptSlots.filter { it.source == BuilderPromptSource.BACKGROUND },
        selectedPromptChoices = uiState.selectedPromptChoices,
        issues = uiState.promptIssues,
        onPromptChoiceSelected = onPromptChoiceSelected,
    )
    if (showPicker) {
        BuilderPickerDialog(
            title = "Choose Background",
            items = uiState.availableBackgrounds.map {
                BuilderPickerItem(it.id, it.name, it.pickerSubtitle())
            },
            onPick = { item ->
                onBackgroundSelected(item.id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
    detailDialog?.let { content ->
        BuilderDetailDialog(
            content = content,
            onDismiss = { detailDialog = null },
        )
    }
}

@Composable
private fun BackgroundGrantedResults(background: BuilderBackgroundRecord) {
    val grantedLines = buildList {
        if (background.trainedSkills.value.isNotEmpty()) {
            add("Skill: ${background.trainedSkills.value.joinToString { BuilderRules.displaySkillName(it) }}")
        }
        if (background.trainedSkills.lore.isNotEmpty()) {
            add("Lore: ${background.trainedSkills.lore.joinToString()}")
        }
        val grantNames = background.grants.mapNotNull { grant -> grant.displayName() }
        if (grantNames.isNotEmpty()) {
            add("Skill feat: ${grantNames.joinToString()}")
        }
    }
    if (grantedLines.isEmpty()) return
    SectionLabel("Granted at Level 1")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        grantedLines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PromptChoices(
    slots: List<BuilderPromptSlot>,
    selectedPromptChoices: Map<String, String>,
    issues: List<BuilderIssue>,
    onPromptChoiceSelected: (String, String?) -> Unit,
) {
    if (slots.isEmpty()) return
    var activeSlot by remember { mutableStateOf<BuilderPromptSlot?>(null) }
    SectionLabel("Required Choices")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { slot ->
            val selectedValue = selectedPromptChoices[slot.slotId]
            val selectedLabel = slot.choices.firstOrNull { choice -> choice.value == selectedValue }?.label
                ?: "No choice selected"
            BuilderSelectionRow(
                label = slot.label,
                value = selectedLabel,
                enabled = slot.choices.isNotEmpty(),
                onChoose = { activeSlot = slot },
            )
            issues
                .filter { issue -> issue.slotId == slot.slotId }
                .forEach { issue -> BuilderIssueText(issue) }
        }
    }
    activeSlot?.let { slot ->
        BuilderPickerDialog(
            title = slot.label,
            items = slot.choices.map { choice ->
                BuilderPickerItem(choice.value, choice.label, slot.sourceLabel)
            },
            allowClear = true,
            onClear = {
                onPromptChoiceSelected(slot.slotId, null)
                activeSlot = null
            },
            onPick = { item ->
                onPromptChoiceSelected(slot.slotId, item.id)
                activeSlot = null
            },
            onDismiss = { activeSlot = null },
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
    onPromptChoiceSelected: (String, String?) -> Unit,
) {
    var showClassPicker by remember { mutableStateOf(false) }
    val selectedClass = uiState.availableClasses.firstOrNull { definition ->
        definition.classId == uiState.selectedClassId
    }
    val selectedClassRecord = uiState.availableClassRecords.firstOrNull { record ->
        record.id == uiState.selectedClassId
    }
    SectionLabel("Class")
    BuilderSelectionRow(
        label = "Class",
        value = selectedClass?.label ?: "None selected",
        enabled = uiState.availableClasses.isNotEmpty(),
        onChoose = { showClassPicker = true },
    )
    selectedClassRecord?.let { record ->
        BuilderDetailBlock(
            lines = record.detailLines(selectedClass),
            description = record.description,
        )
    } ?: selectedClass?.let { definition ->
        BuilderDetailBlock(
            lines = definition.detailLines(),
            description = "",
        )
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

    PromptChoices(
        slots = uiState.promptSlots.filter { it.source == BuilderPromptSource.CLASS },
        selectedPromptChoices = uiState.selectedPromptChoices,
        issues = uiState.promptIssues,
        onPromptChoiceSelected = onPromptChoiceSelected,
    )

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

    if (showClassPicker) {
        BuilderPickerDialog(
            title = "Choose Class",
            items = uiState.availableClasses.map { definition ->
                val record = uiState.availableClassRecords.firstOrNull { it.id == definition.classId }
                BuilderPickerItem(definition.classId, definition.label, record?.pickerSubtitle(definition) ?: definition.pickerSubtitle())
            },
            onPick = { item ->
                onClassSelected(item.id)
                showClassPicker = false
            },
            onDismiss = { showClassPicker = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityScoresSection(
    uiState: CharacterBuilderUiState,
    onAbilityBoostSelected: (String, AbilityScore?) -> Unit,
) {
    val activeIssues = uiState.abilityIssues.filter { it.active && (it.level ?: 1) <= 1 }
    val activeChoiceIssues = activeIssues.filterNot { issue -> issue.isModifierIssue() }
    val activeSlots = uiState.abilityBoostSlots.filter { it.level == 1 }

    uiState.buildFacts?.let { facts ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel("Level 1")
            facts.hp?.let { hp -> ModifierChip("HP $hp", ChipTone.NEUTRAL) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AbilityScore.entries.forEach { ability ->
                AttributeModifierRow(
                    ability = ability,
                    modifier = facts.abilityModifiers[ability] ?: 0,
                    adjustments = facts.abilityAdjustments.filter { adjustment ->
                        adjustment.level == 1 && adjustment.ability == ability
                    },
                    issues = activeIssues.filter { issue -> issue.slotId == ability.modifierIssueSlotId() },
                )
            }
        }
    }

    if (activeSlots.isNotEmpty()) {
        SectionLabel("Choices")
        AbilityBoostGroups(
            slots = activeSlots,
            issues = activeChoiceIssues,
            selectedAbilityBoosts = uiState.selectedAbilityBoosts,
            onAbilityBoostSelected = onAbilityBoostSelected,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttributeModifierRow(
    ability: AbilityScore,
    modifier: Int,
    adjustments: List<BuilderAbilityAdjustment>,
    issues: List<BuilderIssue>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ability.label(),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ModifierChip("+0", ChipTone.NEUTRAL)
                    adjustments.forEach { adjustment ->
                        ModifierChip(
                            text = "${adjustment.sourceShortLabel()} ${adjustment.delta.withSign()}",
                            tone = if (adjustment.delta < 0) ChipTone.ERROR else ChipTone.POSITIVE,
                        )
                    }
                }
            }
            Text(
                text = modifier.withSign(),
                style = MaterialTheme.typography.titleMedium,
                color = if (issues.any { it.active }) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        issues.forEach { issue -> CompactAbilityIssueText(issue) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ModifierChip(
    text: String,
    tone: ChipTone,
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (tone) {
        ChipTone.NEUTRAL -> colorScheme.surfaceVariant
        ChipTone.POSITIVE -> colorScheme.secondaryContainer
        ChipTone.ERROR -> colorScheme.errorContainer
    }
    val contentColor = when (tone) {
        ChipTone.NEUTRAL -> colorScheme.onSurfaceVariant
        ChipTone.POSITIVE -> colorScheme.onSecondaryContainer
        ChipTone.ERROR -> colorScheme.onErrorContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private enum class ChipTone {
    NEUTRAL,
    POSITIVE,
    ERROR,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbilityBoostGroups(
    slots: List<BuilderAbilityBoostSlot>,
    issues: List<BuilderIssue>,
    selectedAbilityBoosts: Map<String, AbilityScore>,
    missingChoicesAreErrors: Boolean = true,
    onAbilityBoostSelected: (String, AbilityScore?) -> Unit,
) {
    slots
        .groupBy { it.groupId }
        .values
        .forEach { groupSlots ->
            val group = groupSlots.first()
            val groupIssues = issues
                .filter { issue -> issue.slotId == group.groupId }
                .filterNot { issue -> issue.isMissingAbilityChoiceIssue() }
            val selectedCount = groupSlots.count { slot ->
                slot.fixedChoice != null || selectedAbilityBoosts[slot.slotId]?.let { selected ->
                    selected in slot.choices
                } == true
            }
            val groupHasError = (missingChoicesAreErrors && selectedCount < groupSlots.size) ||
                groupIssues.any { it.active }
            val slotIssues = groupSlots.flatMap { slot ->
                issues.filter { issue ->
                    issue.slotId == slot.slotId &&
                        !issue.isMissingAbilityChoiceIssue() &&
                        slot.slotId !in issue.relatedSlotIds
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = group.compactGroupTitle(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "$selectedCount/${groupSlots.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (groupHasError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    groupSlots.forEachIndexed { index, slot ->
                        val selected = slot.fixedChoice ?: selectedAbilityBoosts[slot.slotId]
                        val hasIssue = slotIssues.any { issue -> issue.slotId == slot.slotId && issue.active }
                        AbilityBoostSlotChip(
                            slot = slot,
                            slotNumber = index + 1,
                            groupSize = groupSlots.size,
                            selected = selected,
                            missing = missingChoicesAreErrors && slot.required && selected == null,
                            hasIssue = hasIssue,
                            enabled = slot.fixedChoice == null,
                            onAbilityBoostSelected = onAbilityBoostSelected,
                        )
                    }
                }
                (groupIssues + slotIssues)
                    .distinctBy { issue -> issue.slotId + issue.message }
                    .forEach { issue -> CompactAbilityIssueText(issue) }
            }
        }
}

@Composable
private fun AbilityBoostSlotChip(
    slot: BuilderAbilityBoostSlot,
    slotNumber: Int,
    groupSize: Int,
    selected: AbilityScore?,
    missing: Boolean,
    hasIssue: Boolean,
    enabled: Boolean,
    onAbilityBoostSelected: (String, AbilityScore?) -> Unit,
) {
    var showPicker by remember(slot.slotId) { mutableStateOf(false) }
    val fixedChoice = slot.fixedChoice
    val interactive = fixedChoice == null && enabled
    val tone = when {
        missing || hasIssue -> ChipTone.ERROR
        slot.isFlaw -> ChipTone.ERROR
        selected != null -> ChipTone.POSITIVE
        else -> ChipTone.NEUTRAL
    }
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (tone) {
        ChipTone.NEUTRAL -> colorScheme.surfaceVariant
        ChipTone.POSITIVE -> colorScheme.secondaryContainer
        ChipTone.ERROR -> colorScheme.errorContainer
    }
    val contentColor = when (tone) {
        ChipTone.NEUTRAL -> colorScheme.onSurfaceVariant
        ChipTone.POSITIVE -> colorScheme.onSecondaryContainer
        ChipTone.ERROR -> colorScheme.onErrorContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.clickable(
            enabled = interactive,
            role = Role.Button,
        ) { showPicker = true },
    ) {
        Text(
            text = slot.compactChipLabel(slotNumber, groupSize, selected),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
    if (showPicker) {
        BuilderPickerDialog(
            title = slot.label,
            items = slot.choices.map { ability ->
                BuilderPickerItem(
                    id = ability.name,
                    label = "${ability.fullLabel()} (${ability.label()})",
                    subtitle = if (slot.isFlaw) "-1" else "+1",
                )
            },
            onPick = { item ->
                AbilityScore.entries.firstOrNull { ability -> ability.name == item.id }?.let { ability ->
                    onAbilityBoostSelected(slot.slotId, ability)
                }
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
@Composable
private fun BuilderIssueText(issue: BuilderIssue) {
    Text(
        text = issue.message,
        style = MaterialTheme.typography.bodySmall,
        color = if (issue.active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    issue.details?.let { details ->
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            color = if (issue.active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactAbilityIssueText(issue: BuilderIssue) {
    Text(
        text = issue.compactAbilityMessage(),
        style = MaterialTheme.typography.bodySmall,
        color = if (issue.active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SkillsSection(
    uiState: CharacterBuilderUiState,
    onSkillChoiceSelected: (String, String?) -> Unit,
    onLoreSkillChoiceSelected: (String, String) -> Unit,
) {
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
    val slotsByLevel = uiState.skillChoiceSlots
        .filter { it.level == 1 }
        .groupBy { it.level }
    slotsByLevel.keys.sorted().forEach { level ->
        SectionLabel("Level $level Skill Choices")
        slotsByLevel.getValue(level).forEach { slot ->
            val selected = uiState.selectedSkillChoices[slot.slotId]
            BuilderSelectionRow(
                label = slot.label,
                value = selected?.let(BuilderRules::displaySkillName) ?: "No skill selected",
                onChoose = { activeSlot = slot },
            )
            uiState.skillIssues
                .filter { issue -> issue.slotId == slot.slotId || slot.slotId in issue.relatedSlotIds }
                .forEach { issue -> BuilderIssueText(issue) }
        }
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
    onAbilityBoostSelected: (String, AbilityScore?) -> Unit,
    onSkillChoiceSelected: (String, String?) -> Unit,
    onLoreSkillChoiceSelected: (String, String) -> Unit,
) {
    val activeLevel = uiState.level ?: 1
    val futureAbilitySlots = uiState.abilityBoostSlots.filter { it.level > 1 }
    val futureSkillSlots = uiState.skillChoiceSlots.filter { it.level > 1 }
    val trackedLevels = (
        uiState.expectedFeatSlots.map { it.level } +
            futureAbilitySlots.map { it.level } +
            futureSkillSlots.map { it.level }
        ).distinct().sorted()
    var activeSkillSlot by remember { mutableStateOf<BuilderSkillChoiceSlot?>(null) }

    if (trackedLevels.isEmpty()) {
        Text(
            text = "No level choices are expected for the selected class.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (uiState.isLoadingFeatDetails) {
        Text(
            text = "Loading feat details...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "Level 1 feats stay here with later advancement. Future choices can be planned now and start blocking saves when that level is active.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        trackedLevels.forEach { level ->
            val featSlots = uiState.expectedFeatSlots.filter { it.level == level }
            val abilitySlots = futureAbilitySlots.filter { it.level == level }
            val skillSlots = futureSkillSlots.filter { it.level == level }
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
                featSlots.forEach { slot ->
                    FeatSlotRow(
                        slot = slot,
                        uiState = uiState,
                        onFeatPickerOpen = onFeatPickerOpen,
                    )
                }
                if (abilitySlots.isNotEmpty()) {
                    SectionLabel("Attribute Boosts")
                    AbilityBoostGroups(
                        slots = abilitySlots,
                        issues = uiState.abilityIssues.filter { issue -> issue.level == level },
                        selectedAbilityBoosts = uiState.selectedAbilityBoosts,
                        missingChoicesAreErrors = level <= (uiState.level ?: 1),
                        onAbilityBoostSelected = onAbilityBoostSelected,
                    )
                }
                if (skillSlots.isNotEmpty()) {
                    SectionLabel("Skill Increases")
                    skillSlots.forEach { slot ->
                        val selected = uiState.selectedSkillChoices[slot.slotId]
                        BuilderSelectionRow(
                            label = slot.label,
                            value = selected?.let(BuilderRules::displaySkillName) ?: "No skill selected",
                            onChoose = { activeSkillSlot = slot },
                        )
                        uiState.skillIssues
                            .filter { issue -> issue.slotId == slot.slotId || slot.slotId in issue.relatedSlotIds }
                            .forEach { issue -> BuilderIssueText(issue) }
                    }
                }
            }
        }
    }
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
    activeSkillSlot?.let { slot ->
        SkillChoicePickerDialog(
            slot = slot,
            onPick = { skillId ->
                onSkillChoiceSelected(slot.slotId, skillId)
                activeSkillSlot = null
            },
            onLorePick = { loreName ->
                onLoreSkillChoiceSelected(slot.slotId, loreName)
                activeSkillSlot = null
            },
            onClear = {
                onSkillChoiceSelected(slot.slotId, null)
                activeSkillSlot = null
            },
            onDismiss = { activeSkillSlot = null },
        )
    }
}

@Composable
private fun FeatSlotRow(
    slot: BuilderFeatSlot,
    uiState: CharacterBuilderUiState,
    onFeatPickerOpen: (String) -> Unit,
) {
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

private enum class BuilderPickerKind {
    ANCESTRY,
    HERITAGE,
}

private data class BuilderPickerItem(
    val id: String,
    val label: String,
    val subtitle: String,
)

private data class BuilderDetailDialogContent(
    val title: String,
    val lines: List<String>,
    val description: String,
)

private fun BuilderGrantRecord.displayName(): String? {
    return name
        ?: uuid
            ?.substringAfterLast('.')
            ?.takeIf { it.isNotBlank() }
}

@Composable
private fun BuilderDetailBlock(
    title: String? = null,
    lines: List<String>,
    description: String,
    onDetails: (() -> Unit)? = null,
) {
    val visibleLines = lines.filter { it.isNotBlank() }
    val summary = description.shortDescription(140)
    if (visibleLines.isEmpty() && summary.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null || onDetails != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                if (onDetails != null) {
                    TextButton(onClick = onDetails) {
                        Text("Details")
                    }
                }
            }
        }
        visibleLines.take(4).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BuilderDetailDialog(
    content: BuilderDetailDialogContent,
    onDismiss: () -> Unit,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val visibleLines = content.lines.filter { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(content.title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = screenHeightDp * 0.65f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (visibleLines.isNotEmpty()) {
                    item("facts") {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            visibleLines.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (content.description.isNotBlank()) {
                    item("description") {
                        Text(
                            text = content.description.trim(),
                            style = MaterialTheme.typography.bodyMedium,
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

private fun BuilderAncestryRecord.detailLines(): List<String> {
    return listOfNotNull(
        hp?.let { "Ancestry HP $it" },
        speed.takeIf { it.isNotBlank() }?.let { "Speed $it" },
        size.takeIf { it.isNotBlank() }?.let { "Size $it" },
        boosts.takeIf { it.isNotEmpty() }?.let { "Boosts: ${it.joinToString { boost -> boost.choiceText() }}" },
        flaws.takeIf { it.isNotEmpty() }?.let { "Flaws: ${it.joinToString { flaw -> flaw.choiceText() }}" },
        traits.displayLine(),
        source.title?.let { "Source: $it" },
    )
}

private fun BuilderAncestryRecord.pickerSubtitle(): String {
    return (detailLines().take(5) + description.shortDescription(120))
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun BuilderHeritageRecord.detailLines(): List<String> {
    return listOfNotNull(
        traits.displayLine(),
        grants.mapNotNull { it.displayName() }.takeIf { it.isNotEmpty() }?.let { "Grants: ${it.joinToString()}" },
        source.title?.let { "Source: $it" },
    )
}

private fun BuilderHeritageRecord.pickerSubtitle(): String {
    return (detailLines().take(3) + description.shortDescription(140))
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun BuilderBackgroundRecord.detailLines(): List<String> {
    return listOfNotNull(
        boosts.takeIf { it.isNotEmpty() }?.let { "Boosts: ${it.joinToString { boost -> boost.choiceText() }}" },
        trainedSkills.value.takeIf { it.isNotEmpty() }?.let {
            "Trained skill: ${it.joinToString { skill -> BuilderRules.displaySkillName(skill) }}"
        },
        trainedSkills.lore.takeIf { it.isNotEmpty() }?.let { "Lore: ${it.joinToString()}" },
        grants.mapNotNull { it.displayName() }.takeIf { it.isNotEmpty() }?.let { "Skill feat: ${it.joinToString()}" },
        traits.displayLine(),
        source.title?.let { "Source: $it" },
    )
}

private fun BuilderBackgroundRecord.summaryLines(): List<String> {
    return detailLines().filterNot { line ->
        line.startsWith("Trained skill:") ||
            line.startsWith("Lore:") ||
            line.startsWith("Skill feat:")
    }
}

private fun BuilderBackgroundRecord.pickerSubtitle(): String {
    return (detailLines().take(5) + description.shortDescription(120))
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun BuilderClassRecord.detailLines(definition: CharacterClassDefinition?): List<String> {
    return listOfNotNull(
        hp?.let { "Class HP $it" },
        "Key attribute: ${keyAbilityOptions.ifEmpty { definition?.keyAbilityOptions.orEmpty() }.joinToString { it.label() }}",
        trainedSkills.value.takeIf { it.isNotEmpty() }?.let {
            "Trained skill: ${it.joinToString { skill -> BuilderRules.displaySkillName(skill) }}"
        },
        trainedSkills.additional?.let { "Extra trained skills: $it plus Intelligence" },
        traits.displayLine(),
        source.title?.let { "Source: $it" },
    )
}

private fun BuilderClassRecord.pickerSubtitle(definition: CharacterClassDefinition?): String {
    return (detailLines(definition).take(5) + description.shortDescription(120))
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun CharacterClassDefinition.detailLines(): List<String> {
    return listOf(
        "Key attribute: ${keyAbilityOptions.joinToString { it.label() }}",
    )
}

private fun CharacterClassDefinition.pickerSubtitle(): String {
    return detailLines().joinToString(" - ")
}

private fun BuilderAbilityBoostRecord.choiceText(): String {
    selected?.let { return it.label() }
    return abilities.takeIf { it.isNotEmpty() }
        ?.joinToString("/") { it.label() }
        ?: "free"
}

private fun BuilderTraitsRecord.displayLine(): String? {
    val parts = (listOf(rarity.takeIf { it.isNotBlank() && it != "common" }) + values)
        .filterNotNull()
        .filter { it.isNotBlank() }
        .distinct()
    return parts.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Traits: ")
}

private fun String.shortDescription(maxLength: Int = 220): String {
    val cleaned = replace(Regex("\\s+"), " ").trim()
    if (cleaned.length <= maxLength) return cleaned
    return cleaned.take(maxLength).substringBeforeLast(' ').trimEnd('.', ',', ';', ':') + "..."
}

private fun BuilderAbilityAdjustment.sourceShortLabel(): String = when {
    groupId.startsWith("ancestry") -> "Anc"
    groupId.startsWith("background") -> "Bg"
    groupId.startsWith("class") -> "Cls"
    groupId.startsWith("free") -> "Free"
    else -> groupLabel.substringBefore(' ').take(4)
}

private fun BuilderIssue.isModifierIssue(): Boolean = slotId.startsWith("ability/modifier/")

private fun AbilityScore.modifierIssueSlotId(): String = "ability/modifier/${name.lowercase()}"

private fun BuilderIssue.isMissingAbilityChoiceIssue(): Boolean {
    return message.startsWith("Choose one ") || message == "Choose a class key attribute."
}

private fun BuilderIssue.compactAbilityMessage(): String {
    details?.let { detail ->
        if (detail.contains("higher than +4")) return "L1 max +4."
        if (detail.contains("lower than -1")) return "L1 min -1."
        if (detail.contains("different attributes") && message.contains(" is selected ")) {
            val ability = message.substringBefore(" is selected")
            val count = message.substringAfter(" is selected ").substringBefore(" in ")
            return "$ability selected $count here."
        }
    }
    if (message.contains(" no longer belongs to ")) {
        return "${message.substringBefore(" no longer belongs to ")} is not valid here."
    }
    return message
}

private fun BuilderAbilityBoostSlot.compactGroupTitle(): String = when (kind) {
    BuilderAbilityBoostKind.ANCESTRY_FLAW -> "Ancestry Flaw"
    BuilderAbilityBoostKind.ANCESTRY_BOOST -> "Ancestry Boosts"
    BuilderAbilityBoostKind.BACKGROUND_BOOST -> "Background Boosts"
    BuilderAbilityBoostKind.CLASS_KEY -> "Class Key"
    BuilderAbilityBoostKind.FREE_BOOST -> "Free Boosts"
}

private fun BuilderAbilityBoostSlot.compactChipLabel(
    slotNumber: Int,
    groupSize: Int,
    selected: AbilityScore?,
): String {
    val slotLabel = when {
        groupSize > 1 -> "#$slotNumber"
        kind == BuilderAbilityBoostKind.CLASS_KEY -> "Key"
        kind == BuilderAbilityBoostKind.ANCESTRY_FLAW -> "Flaw"
        else -> "Boost"
    }
    val delta = if (isFlaw) "-1" else "+1"
    val choice = selected?.label() ?: "Pick"
    return "$slotLabel $delta $choice"
}

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
