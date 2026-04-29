# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Android Pathfinder 2e character builder and table companion. The app began with a spell-focused scope, but the product direction is now a full character builder with spell support as one part of the broader character workflow. The long-term goal is an authoritative player-facing character sheet that can be used as the source of truth during play.

**Strictly offline**: no network permissions, no HTTP libraries, no telemetry, no cloud sync. Build-time Gradle tasks (`checkNoInternetPermission`, `checkNoBannedNetworkDependencies`) gate `preBuild` and fail the build if `android.permission.INTERNET` or banned networking dependencies (okhttp, retrofit, ktor, volley) are detected.

## Product Priorities

1. Level-one PF2e Remaster character creation is the primary workflow.
2. `docs/CharacterCreation.md` and `docs/LevelingUp.md` are the local rules source of truth for builder behavior.
3. Post-level-one choices belong in the Level Workbench, not in the level-one creation sections.
4. Rules errors should appear where the user can fix them and explain what went wrong.
5. Derived sheet facts should come from selected build choices, level choices, local rules data, and tracked play state.
6. Manual overrides should be explicit and visible; do not hide them as normal derived values.
7. Optional rules, legacy/pre-remaster terminology toggles, voluntary flaws, and alternate ancestry boost handling are out of scope until explicitly reintroduced.
8. Spell browsing, spell preparation, and spellcasting support remain important, but they support the character sheet rather than defining the whole app.

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests (all modules)
./gradlew test

# Run focused character builder tests
./gradlew :feature-character:testDebugUnitTest

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.spellapp.SomeTest"

# Run Android instrumentation tests
./gradlew connectedAndroidTest

# Run offline-enforcement validation script
powershell -ExecutionPolicy Bypass -File scripts/check_no_network.ps1

# Update local spell dataset from Foundry VTT source
powershell -ExecutionPolicy Bypass -File scripts/update_spell_dataset.ps1 -FoundrySpellsDir "<path>" -SourceCommit "<hash>"

# Update local class dataset from Foundry VTT source
powershell -ExecutionPolicy Bypass -File scripts/update_class_dataset.ps1 -FoundryClassesDir "<path>" -SourceCommit "<hash>"

# Update local rules catalog from Foundry VTT source
powershell -ExecutionPolicy Bypass -File scripts/update_rules_catalog.ps1 -FoundryPf2ePacksDir "<path>" -SourceCommit "<hash>"
```

## Build Environment

- **Gradle:** 8.7 with AGP 8.6.1
- **Kotlin:** 2.0.21 with Compose compiler plugin
- **JDK:** 21 runtime, Java 17 bytecode target
- **Min SDK:** 28 | **Target/Compile SDK:** 35
- **KSP:** 2.0.21-1.0.25 (Room annotation processing in `core-data`)

## Module Architecture

```
app                    -> Android application, navigation host, DI container (AppContainer)
feature-character      -> Character creation/editing, builder validation, level workbench UI
feature-spells         -> Spell browsing, spell detail, and spell-use support
core-rules             -> Pure Kotlin rules and derivation helpers
core-ui                -> SpellAppTheme, shared Material 3 design tokens
core-model             -> Pure Kotlin/JVM domain models
core-data              -> Room database, DAOs, repositories, asset-backed data sources
```

**Dependency flow:** `app` depends on feature modules plus `core-data`, `core-model`, and `core-ui`. Feature modules depend on shared core modules. `feature-spells` depends on `core-rules`; `core-rules` depends only on `core-model`. Keep `core-model` pure Kotlin/JVM with no Android framework imports.

## Key Patterns

- **Manual DI via AppContainer** (`app/.../AppContainer.kt`): lazy-initialized singletons for database, repositories, and data sources. No Hilt/Dagger.
- **MVVM + Repository:** ViewModels expose `Flow<State>`, collected as Compose state. Repositories abstract Room DAOs and asset-backed sources.
- **Navigation:** Compose Navigation with sealed `AppDestinations` class. Routes and NavGraph are defined in `SpellAppNavGraph.kt`.
- **Room database:** Entities in `core-data`, migrations defined in `SpellDatabase.kt`. Schema changes require explicit migration objects.
- **Bundled data seeding:** Spells, classes, and rules catalog data are bundled in `app/src/main/assets/` and loaded locally.
- **Rules-first builder:** Builder UI should persist selected character choices and derive facts from those choices instead of storing editable derived stats.

## Constraints

- **Never add** `android.permission.INTERNET` to any manifest.
- **Never add** okhttp, retrofit, ktor, or volley dependencies.
- `core-model` must remain a pure `kotlin.jvm` module with no Android framework imports.
- Runtime data access must remain local-only.
- Version catalog (`gradle/libs.versions.toml`) is the single source for dependency versions.
