# SpellApp

Android-first Pathfinder 2e spell list manager with a strict offline runtime model.

## Current Status
1. Multi-module Android project scaffolded.
2. Compose app shell with Spell List and Spell Detail routes backed by local Room data.
3. First-launch spell dataset seed from bundled asset (`app/src/main/assets/spells.normalized.json`).
4. Functional and technical requirements captured in:
   - `requirements.md`
   - `technical-requirements.md`
   - `phase-0-plan.md`
   - `phase-1-plan.md`

## JDK Setup
1. Gradle runtime JDK: 21 (project/tool execution).
2. Android compilation target: Java/Kotlin 17 bytecode (set in module build files).
3. Use Gradle 8.7 with AGP 8.6.1.

## Modules
1. `app`: entrypoint and navigation host.
2. `feature-spells`: read-only spell list/detail UI placeholders.
3. `core-ui`: app theme.
4. `core-model`: shared domain models.
5. `core-data`: data contracts (Room integration next).

## Next Steps
1. Wire spell search/filter controls to Room query parameters.
2. Replace bundled sample dataset with importer-generated full dataset.
3. Expand tests: migrations, repository integration, and airplane-mode UI flows.

## Local Validation Helpers
1. `scripts/check_no_network.ps1`: checks for `INTERNET` permission and banned network dependency patterns.

## Full Dataset Update
1. Clone/update `foundryvtt/pf2e` locally.
2. Run:
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_spell_dataset.ps1 `
  -FoundrySpellsDir "<path-to-foundry\packs\data\spells>" `
  -SourceCommit "<foundry-commit-hash>"
```
3. Rebuild app. The script overwrites:
   - `app/src/main/assets/spells.normalized.json`
   - `app/src/main/assets/spells.attribution.json`
   - `app/src/main/assets/spells.changelog.json`
