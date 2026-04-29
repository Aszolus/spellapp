# Pathfinder 2e Character Builder and Table Companion (Android)

## 1. Objective

Create an Android app for building and managing Pathfinder 2e characters during character creation, leveling, and live play. The end goal is an authoritative player-facing character sheet: a source of truth the player can rely on at the table. The app must be rules-aware, fast at the table, and fully usable without network access.

Spell browsing and spellcasting support remain part of the product, but the primary product boundary is now the whole character, not only spell lists.

## 2. Primary Use Cases

1. Create a level-one PF2e Remaster character by following the documented character creation steps.
2. See what each ancestry, heritage, background, and class grants before selecting it.
3. Choose attribute modifiers, trained skills, feats, and required prompts with clear validation.
4. Plan and complete post-level-one choices in the Level Workbench.
5. View derived character facts such as HP, attribute modifiers, skills, Perception, saves, and class or spell DCs.
6. Use the app as the active character sheet during play, not just as a setup wizard.
7. Browse spell data locally and use spell information in character context.
8. Keep character data available during live play without internet access.

## 3. Target Users

1. PF2e players creating and maintaining one or more characters.
2. Players using a phone or tablet at the table while a session is running.
3. GMs building or referencing NPC-style characters.
4. Offline-first users playing in locations without reliable internet.

## 4. Platform and Constraints

1. Android-first, with phone priority and tablet support.
2. One-handed use should remain practical for common at-table tasks.
3. All runtime features must work offline.
4. The app must not request network permission, include HTTP client dependencies, require an account, or use cloud sync.
5. Rules and content updates happen through bundled assets, migrations, or explicit local import/export workflows.

## 5. In Scope (Current MVP)

1. Local character profiles:
   - Name and level.
   - Ancestry and heritage.
   - Background.
   - Class.
   - Attribute modifiers.
   - Trained skills.
   - Feat and prompt selections.
2. Level-one creation workflow:
   - Main builder sections focus on level-one choices only.
   - `docs/CharacterCreation.md` is the source of truth for required steps and validation.
   - Errors identify the exact missing, duplicate, stale, or invalid choice and appear near the relevant control.
3. Level Workbench:
   - All feat slots live here, including level-one feats.
   - Post-level-one ability boosts, skill increases, and later choices live here.
   - Future choices can be edited but should not block saving until the character reaches that level.
4. Rules derivation:
   - Attribute modifiers start at +0.
   - Boosts add +1 and flaws subtract -1.
   - Level-one free boosts must be four different attributes.
   - Derived facts should consume modifiers directly rather than score-style math.
5. Local rules/data sources:
   - Character creation and leveling docs in `docs/`.
   - Bundled spell, class, and rules catalog assets.
   - Source/license metadata preserved for imported content.
6. Spell support:
   - Local spell browsing and details.
   - Spell-related character context where supported by the builder.
   - Spell preparation/casting workflows remain a product goal after the builder foundation is stable.
7. Persistence:
   - Local Room storage.
   - Forward migrations for schema changes.
   - Future local export/import for character backup.
8. Character sheet source of truth:
   - The sheet should show the player's current playable character state.
   - Sheet values should be derived from build choices, rules data, and tracked play state wherever possible.
   - Manual overrides should be explicit, visible, and explain why they differ from derived rules.

## 6. Out of Scope (Current MVP)

1. Optional rules, including voluntary flaws and alternate ancestry boost handling.
2. Legacy or pre-remaster terminology toggles.
3. Full equipment, AC, Bulk, strikes, deity details, and complete sheet-finalization automation.
4. Encounter tracking for full parties.
5. VTT sync, cloud accounts, online content lookup, telemetry, ads, or remote config.
6. Open-ended homebrew rule automation beyond future custom notes or overrides.

## 7. Functional Requirements

### 7.1 Character Creation Workflow

1. The builder must guide the user through the required level-one choices from `docs/CharacterCreation.md`.
2. Section boundaries must match where the user can act:
   - Ancestry and heritage selection shows ancestry/heritage information and ancestry/heritage errors.
   - Background selection shows background information and background errors.
   - Attribute Modifiers shows only active level-one boost/flaw choices and their modifier effects.
   - Skills shows level-one trained skill choices and current trained results.
   - Level Workbench shows feats and post-level-one planning.
3. Selection lists must include enough rules information to make a choice without leaving the screen.
4. Required prompts must be represented as builder-managed choices and must block saving when active and unanswered.

### 7.2 Validation and Errors

1. Missing active choices must name the exact step and requested action.
2. Duplicate active choices must name the duplicate and the rule being violated.
3. Stale selections must explain that the source changed and ask the user to choose from listed options.
4. Future-level issues must not block saving for lower-level characters.
5. Save blockers must be visible in a top-level summary and beside the affected control.

### 7.3 Attribute Modifiers

1. Visible language must use PF2e Remaster "attribute modifiers" rather than score-first terminology.
2. Every attribute starts at +0.
3. Ancestry flaws subtract 1, ancestry boosts add 1, background boosts add 1, class key boosts add 1, and free boosts add 1.
4. Level-one active modifiers must not be below -1 or above +4.
5. Each modifier should be explainable by source with before/after or equivalent source breakdown data.

### 7.4 Level Workbench

1. The workbench owns all feat slots, including level-one ancestry, class, and skill feats.
2. Empty levels with no tracked choices should be hidden.
3. Active and future choices must be labeled clearly.
4. Future choices become active blockers only when the character level reaches that level.

### 7.5 Spell Support

1. Spell content is bundled locally and browsable without internet.
2. Spell details must show rules text and key mechanics clearly.
3. Spellcasting-derived values should come from character choices when the rules layer supports them.
4. Manual casting-stat controls are not part of the current builder direction.

### 7.6 Character Sheet Source of Truth

1. The character sheet must be usable during live play as the player's trusted current character state.
2. Sheet facts must be derived from selected build choices, level choices, local rules data, and tracked session state rather than copied into unrelated fields.
3. Each derived value should expose enough source detail to answer "why is this number here?"
4. Incomplete build choices must be clearly separated from playable sheet facts.
5. Future manual overrides must be visible as overrides, not indistinguishable from rules-derived values.

## 8. Non-Functional Requirements

1. Performance:
   - Open app to usable state in under 2 seconds on mid-range devices.
   - Keep common builder interactions responsive.
2. Reliability:
   - No data loss on app restart or crash.
   - Save and migration paths must be tested.
3. UX:
   - Dark and light themes.
   - High contrast and legibility in dim table lighting.
   - Clear labels, dense but readable layouts, and no hidden critical rules information.
4. Privacy:
   - No required account.
   - No user data leaves the device.
5. Maintainability:
   - Rules/data updates must be separable from app code.
   - Derived facts should be deterministic and testable.

## 9. Data and Content Requirements

1. Primary imported sources are local Foundry PF2e data exports for spells, classes, and rules catalog inputs.
2. Import tooling must preserve source and license metadata.
3. Build/runtime assets must include normalized data, attribution, and changelog files.
4. Runtime app behavior must not make network calls.
5. Data refreshes occur only through a new app build, a local development update script, or future explicit local import.
6. Character creation and leveling behavior is governed by local documentation in `docs/` until a richer rules-data pipeline covers the same behavior.

## 10. Proposed MVP Screens

1. Character List.
2. Character Builder:
   - Identity.
   - Ancestry and Heritage.
   - Background.
   - Class.
   - Attribute Modifiers.
   - Skills.
   - Level Workbench.
3. Character Summary.
4. Character Sheet.
5. Spell Browser/Search.
6. Spell Detail.
7. Future Prepared/Repertoire Manager.
8. Future Session Log and Undo.
9. Future Settings and local backup/export.

## 11. Acceptance Criteria (Current MVP)

1. A user can create and save a valid level-one character that satisfies `docs/CharacterCreation.md`.
2. Invalid level-one choices are blocked with source-specific, fixable errors.
3. Ancestry, heritage, background, and class choices show rules detail before selection.
4. Attribute modifiers are understandable as modifier-first PF2e Remaster choices.
5. Future choices are editable in the Level Workbench and do not block lower-level saves.
6. The sheet can show trusted derived level-one facts after a valid level-one character is saved.
7. Spell browsing remains available offline.
8. In Android airplane mode, character creation, sheet viewing, and local spell lookup work with no missing runtime dependency.

## 12. Locked Decisions

1. Runtime app behavior is fully local-only.
2. The current rules presentation defaults to Remaster terminology.
3. Optional rules are deliberately excluded until the required creation workflow is solid.
4. Level-one creation and post-level-one planning are separate UX surfaces.
5. Stored build choices remain the compatibility boundary; derived facts can change as rules improve.
6. The character sheet is the product's source-of-truth surface; the builder exists to produce and maintain that sheet.

## 13. Suggested Delivery Phases

1. Builder Foundation: level-one character creation, validation, and derived facts.
2. Leveling Foundation: Level Workbench coverage for levels 2-20.
3. Sheet Completion: equipment, AC, Bulk, strikes, deity/details, conditions, resources, and source-of-truth sheet display.
4. Spell Integration: spell preparation, spellcasting context, cast flow, focus, undo/session log.
5. Backup and Polish: local export/import, accessibility pass, theming, and performance cleanup.
