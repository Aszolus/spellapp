# SpellApp

Android-first Pathfinder 2e spell list manager with a strict offline runtime model.

## Current Status
1. Multi-module Android project scaffolded.
2. Compose app shell with Spell List and Spell Detail placeholder routes.
3. Build-time importer script scaffolded in `tools/importer`.
4. Functional and technical requirements captured in:
   - `requirements.md`
   - `technical-requirements.md`
   - `phase-0-plan.md`

## JDK Setup
1. Gradle runtime JDK: 21 (project/tool execution).
2. Android compilation target: Java/Kotlin 17 bytecode (set in module build files).
3. Use Gradle 8.7 with AGP 8.5.2.

## Modules
1. `app`: entrypoint and navigation host.
2. `feature-spells`: read-only spell list/detail UI placeholders.
3. `core-ui`: app theme.
4. `core-model`: shared domain models.
5. `core-data`: data contracts (Room integration next).

## Next Steps
1. Add Room schema and first-run seed path from importer output.
2. Replace sample spell list with DB-backed query flow.
3. Expand build checks for no-network guarantees.

## Local Validation Helpers
1. `scripts/check_no_network.ps1`: checks for `INTERNET` permission and banned network dependency patterns.
