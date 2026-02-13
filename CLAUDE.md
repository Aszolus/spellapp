# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android Pathfinder 2e spell manager. **Strictly offline** — no network permissions, no HTTP libraries. Build-time Gradle tasks (`checkNoInternetPermission`, `checkNoBannedNetworkDependencies`) gate `preBuild` and will fail the build if `android.permission.INTERNET` or any banned networking dependency (okhttp, retrofit, ktor, volley) is detected.

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests (all modules)
./gradlew test

# Run a single test class
./gradlew :app:testDebugUnitTest --tests "com.spellapp.SomeTest"

# Run Android instrumentation tests
./gradlew connectedAndroidTest

# Run offline-enforcement validation script
powershell -ExecutionPolicy Bypass -File scripts/check_no_network.ps1

# Update spell dataset from Foundry VTT source
powershell -ExecutionPolicy Bypass -File scripts/update_spell_dataset.ps1 -FoundrySpellsDir "<path>" -SourceCommit "<hash>"
```

## Build Environment

- **Gradle:** 8.7 with AGP 8.6.1
- **Kotlin:** 2.0.21 with Compose compiler plugin
- **JDK:** 21 runtime, Java 17 bytecode target
- **Min SDK:** 28 | **Target/Compile SDK:** 35
- **KSP:** 2.0.21-1.0.25 (Room annotation processing in `core-data`)

## Module Architecture

```
app                    → Android application, navigation host, DI container (AppContainer)
feature-spells         → Spell list/detail UI (Compose screens)
core-ui                → SpellAppTheme, shared Material 3 design tokens
core-model             → Pure Kotlin/JVM domain models (no Android dependency)
core-data              → Room database, DAOs, repository implementations
```

**Dependency flow:** `app` → `feature-spells`, `core-data`, `core-model`, `core-ui`. Feature modules depend on `core-model` and `core-ui`. `core-data` depends on `core-model`. `core-model` has zero dependencies.

## Key Patterns

- **Manual DI via AppContainer** (`app/.../AppContainer.kt`): lazy-initialized singletons for database, repositories, and data sources. No Hilt/Dagger.
- **MVVM + Repository:** ViewModels expose `Flow<State>`, collected as Compose state. Repositories abstract Room DAOs.
- **Navigation:** Compose Navigation with sealed `AppDestinations` class. Routes and NavGraph defined in `SpellAppNavGraph.kt`.
- **Room database** (version 3): Entities in `core-data`, migrations defined in `SpellDatabase.kt`. Schema changes require explicit migration objects.
- **Spell data seeding:** `spells.normalized.json` bundled in `app/src/main/assets/`, loaded on first launch via `AppContainer.seedSpellsIfNeeded()`.

## Constraints

- **Never add** `android.permission.INTERNET` to any manifest.
- **Never add** okhttp, retrofit, ktor, or volley dependencies.
- `core-model` must remain a pure `kotlin.jvm` module — no Android framework imports.
- Version catalog (`gradle/libs.versions.toml`) is the single source for dependency versions.
