# Pathfinder 2e Character Builder and Table Companion (Android) - Technical Requirements

## 1. Technical Goal

Deliver a fully self-contained Android app for PF2e character creation, leveling, rules explanation, spell reference, and table use with zero runtime network dependencies, durable local data, and deterministic rules derivation.

## 2. Hard Constraints

1. The app must function without internet for all runtime features.
2. The app must not make network calls at runtime.
3. The app must not request `android.permission.INTERNET`.
4. No cloud sync, telemetry, ads SDK, remote config, or online auth in v1.
5. Character, rules, class, and spell content must be bundled locally or imported explicitly through local files.
6. Any future online feature requires a separate product and architecture decision.

## 3. Platform Baseline

1. Language: Kotlin.
2. UI: Jetpack Compose + Material 3.
3. Architecture: MVVM with clear UI, domain/rules, and data boundaries.
4. Local database: Room over SQLite.
5. Serialization: `kotlinx.serialization` for imported datasets and future export/import payloads.
6. Minimum Android version: API 28+.
7. Gradle runtime JDK: 21.
8. Java/Kotlin bytecode target: 17.

## 4. Module Structure

1. `app`: Android entrypoint, navigation host, manual DI wiring, offline build gates.
2. `feature-character`: character creation/editing, builder sections, validation display, level workbench.
3. `feature-spells`: spell list, search/filter, spell detail, and future cast flow.
4. `core-rules`: pure Kotlin rules and deterministic derivation helpers.
5. `core-data`: Room entities/DAOs, repositories, migrations, bundled asset loading.
6. `core-model`: shared pure Kotlin domain models and enums.
7. `core-ui`: shared theme, Material 3 design tokens, common UI styling.
8. `tools` and `scripts`: local development/release import and validation tooling.

## 5. Runtime Architecture

1. ViewModels expose immutable UI state through `Flow`.
2. Compose screens render state and call ViewModel intents.
3. Repositories are the only data mutation boundary.
4. Builder selections are persisted as choices; derived facts are calculated from selections.
5. Rules derivation must be deterministic, side-effect free, and unit-testable.
6. UI must not invent slot counts, modifiers, trained proficiencies, or other derived facts.
7. Errors should carry enough metadata to attach messages to the relevant section and control.

## 6. Data Ingestion Pipeline (Build-Time or Local Tooling)

1. Sources:
   - `foundryvtt/pf2e` spell JSON.
   - `foundryvtt/pf2e` class JSON.
   - PF2e rules catalog inputs for ancestries, heritages, backgrounds, feats, and related build options.
   - Local docs in `docs/` for rules not yet represented structurally.
2. Importers must pin and record upstream commit hashes.
3. Importers must preserve recognized source and license metadata.
4. Importers must fail if required license/source metadata is missing or unknown.
5. Importers must emit:
   - normalized app dataset,
   - attribution artifact,
   - diff/changelog against previous dataset.
6. Importers and update scripts are development/release tooling only; runtime app code must not fetch remote content.

## 7. Runtime Data Model

1. Preloaded datasets are stored in bundled assets and/or local Room tables.
2. Character records, build selections, prompts, prepared choices, and future session state are stored locally.
3. Stored choices should keep stable identifiers (`STR`, `DEX`, class IDs, ancestry IDs, feat IDs, prompt keys).
4. Derived facts must be rebuildable from stored choices plus bundled rules/data.
5. All persisted payloads and schema changes must have forward migration paths.
6. Write operations that update related character state must use Room transactions.

## 8. Offline and Network Isolation Requirements

1. Build pipeline must fail if `INTERNET` permission appears in the merged manifest.
2. Build pipeline must fail if known HTTP client libraries are present in app runtime classpath.
3. Critical user journeys must pass in airplane mode.
4. App startup must not block on remote dependencies.
5. All fonts, icons, datasets, and rules assets required at runtime must be bundled locally.

## 9. Rules Engine Requirements

1. Character creation rules come from `docs/CharacterCreation.md` until superseded by equivalent structured rules data.
2. Leveling rules come from `docs/LevelingUp.md` until superseded by equivalent structured rules data.
3. Attribute modifiers are modifier-first:
   - Start at +0.
   - Boosts add +1.
   - Flaws subtract -1.
   - Level-one active range is -1 through +4.
4. Level-one validation must cover missing ancestry, heritage, background, class, attribute boosts/flaws, trained skill choices, required prompts, required class choices, and active feat slots.
5. Future-level choices must remain editable but nonblocking until the character reaches that level.
6. Validation results must include structured codes, source labels, affected slot IDs when available, and user-facing messages.
7. Optional rules must remain hidden and ignored until explicitly implemented.

## 10. Character Builder UI Requirements

1. Main builder sections should be level-one focused.
2. The Level Workbench owns all feat slots and all post-level-one choices.
3. Selection dialogs should expose enough rules detail to support informed choices.
4. Errors must not appear in unrelated sections where the user cannot fix them.
5. Attribute Modifiers must show final modifiers and source breakdowns rather than score-style math.
6. Manual casting stat and archetype spellcasting controls are not part of the current builder.
7. Name and level fields should disable autocomplete.

## 11. Performance Budgets

1. Cold start to interactive: <= 2 seconds on mid-range target devices.
2. Common builder interactions: <= 100 ms perceived response after local data is loaded.
3. Spell search/filter response: <= 200 ms perceived latency for common filters.
4. Spell detail open from list: <= 150 ms median.
5. No visible frame drops during rapid filtering or builder section navigation.

## 12. Reliability and Data Integrity

1. Crash-safe persistence: no partial state after interrupted writes.
2. Save must not persist hidden optional-rule choices that are no longer generated by the builder.
3. Stale saved choices must be reported clearly and cleaned up when the current generated slot model is saved.
4. Future export format must include schema version and checksum.
5. Future import must validate schema, required fields, and checksum before write.
6. Corrupt imports must fail safely with clear error messaging.

## 13. Security and Privacy

1. No account creation required.
2. No user data leaves device.
3. Use Android Storage Access Framework for future import/export file access.
4. Avoid broad storage permissions; request only scoped/document-based access.
5. Release builds must not include debug logs containing spell text or user character data.

## 14. Testing Requirements

1. Unit tests:
   - character creation validation,
   - attribute modifier derivation,
   - prompt persistence and stale-choice handling,
   - level workbench active/future gating,
   - spell/rules derivation helpers,
   - migration logic.
2. ViewModel tests:
   - section status summaries,
   - save blockers,
   - future choices remaining nonblocking,
   - hidden optional-rule cleanup.
3. Integration tests:
   - repository + Room transaction correctness,
   - asset loading,
   - future export/import round trip.
4. UI tests:
   - level-one creation workflow,
   - error placement and recovery,
   - spell search/filter workflows,
   - airplane mode workflows.
5. Build verification checks:
   - no `INTERNET` permission,
   - no network client dependency,
   - bundled datasets present and loadable.

## 15. Release and Update Strategy

1. Dataset updates are delivered by shipping a new app build or future explicit local import.
2. Every release must include:
   - dataset version,
   - upstream source commit hash when imported from Foundry PF2e,
   - generated attribution.
3. App must support opening old character data through migration paths.
4. If migration fails, the app must keep the old DB intact and surface recovery options.

## 16. Technical Acceptance Criteria (Current MVP)

1. App installs and runs with zero network permissions and zero network calls.
2. A valid level-one character can be created and saved according to `docs/CharacterCreation.md`.
3. Invalid active choices block saving with structured, local, actionable errors.
4. Future choices in the Level Workbench do not block lower-level characters.
5. Spell browsing and details are fully available in airplane mode.
6. CI or local Gradle checks reject builds that violate network isolation or license validation rules.
