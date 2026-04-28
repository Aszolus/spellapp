# SpellApp

Android-first Pathfinder 2e character builder and table companion with a strict offline runtime model.

## Current Product Direction
1. SpellApp is shifting from its earlier spell-focused scope into a full PF2e character builder.
2. The level-one character creation workflow is the primary builder path right now.
3. Leveling choices after level one belong in the Level Workbench.
4. Spell browsing and spellcasting support remain important, but they are supporting features inside the broader character tool.
5. Runtime behavior stays fully offline: no network permission, no HTTP client dependencies, no telemetry, and no cloud sync.

## Current Status
1. Multi-module Android project scaffolded with Compose navigation.
2. Local Room-backed data layer with first-launch dataset seeding from bundled assets.
3. Character builder module with ancestry, heritage, background, class, attribute modifier, skill, feat, and level workbench flows in progress.
4. Local rules documentation captured in:
   - `docs/CharacterCreation.md`
   - `docs/LevelingUp.md`
5. Functional and technical product docs:
   - `requirements.md`
   - `technical-requirements.md`

## JDK Setup
1. Gradle runtime JDK: 21 for project and tool execution.
2. Android compilation target: Java/Kotlin 17 bytecode.
3. Gradle 8.7 with AGP 8.6.1.

## Modules
1. `app`: Android entrypoint, navigation host, and manual dependency wiring.
2. `feature-character`: character creation, character editing, builder validation, and level workbench UI.
3. `feature-spells`: local spell browsing, spell details, and spell-use support.
4. `core-rules`: pure Kotlin rules and derivation helpers.
5. `core-ui`: shared Material 3 theme and design tokens.
6. `core-model`: shared domain models.
7. `core-data`: Room database, DAOs, repositories, and bundled asset loading.

## Near-Term Focus
1. Make level-one creation match `docs/CharacterCreation.md` clearly and completely.
2. Keep every post-level-one choice in the Level Workbench.
3. Improve rules explanations and validation messages before adding optional rules.
4. Continue deriving character facts from selected build choices instead of ad-hoc UI fields.
5. Preserve offline build gates and local-only data updates.

## Local Validation Helpers
1. `scripts/check_no_network.ps1`: checks for `INTERNET` permission and banned network dependency patterns.
2. `./gradlew test`: runs all unit tests and offline-enforcement build checks.
3. `./gradlew :feature-character:testDebugUnitTest`: runs focused character builder tests.

## Dataset Updates
Dataset updates are local development or release tasks only. Runtime app behavior must not fetch data from the network.

Update spells:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_spell_dataset.ps1 `
  -FoundrySpellsDir "<path-to-foundry\packs\pf2e\spells>" `
  -SourceCommit "<foundry-commit-hash>"
```

Update classes:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_class_dataset.ps1 `
  -FoundryClassesDir "<path-to-foundry\packs\pf2e\classes>" `
  -SourceCommit "<foundry-commit-hash>"
```

Update rules catalog:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_rules_catalog.ps1 `
  -FoundryPf2ePacksDir "<path-to-foundry\packs\pf2e>" `
  -SourceCommit "<foundry-commit-hash>"
```

Generated app assets include:
1. `app/src/main/assets/spells.normalized.json`
2. `app/src/main/assets/spells.attribution.json`
3. `app/src/main/assets/spells.changelog.json`
4. `app/src/main/assets/classes.normalized.json`
5. `app/src/main/assets/classes.attribution.json`
6. `app/src/main/assets/classes.changelog.json`
7. `app/src/main/assets/rules.catalog.normalized.json`
8. `app/src/main/assets/rules.catalog.attribution.json`
9. `app/src/main/assets/rules.catalog.changelog.json`
