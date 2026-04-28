# Dataset Importers

This directory contains build-time ingestion tools for local PF2e data. The catalog importer is the new source of truth for broad player-facing data. The older spell, class, builder, spellcasting, and rules scripts remain during migration so the current app behavior can coexist with the Stage 1 catalog audit work.

## Intended Inputs
1. PF2e spell JSON files from `foundryvtt/pf2e` (`packs/pf2e/spells`).
2. PF2e class JSON files from `foundryvtt/pf2e` (`packs/pf2e/classes`).
3. PF2e rules-source JSON files from `foundryvtt/pf2e`:
   - `packs/pf2e/classes`
   - `packs/pf2e/class-features`
   - `packs/pf2e/feats`
   - `packs/pf2e/ancestries`
   - `packs/pf2e/backgrounds`
   - `packs/pf2e/journals`
2. A pinned upstream commit hash.

## Intended Outputs
### Catalog Importer
1. `catalog/catalog.db`
2. `catalog/catalog.manifest.json`
3. `catalog/catalog.audit.json`

The catalog importer supersedes these older generated datasets once app integration is complete:
1. `spells.normalized.json`
2. `classes.normalized.json`
3. `class-spellcasting.normalized.json`
4. `builder.manifest.normalized.json` and its referenced builder assets
5. `rules.catalog.normalized.json`
6. `rules.reference.*.json.gz`

### Legacy Importers
1. `spells.normalized.json`
2. `spells.attribution.json`
3. `spells.changelog.json`
4. `classes.normalized.json`
5. `classes.attribution.json`
6. `classes.changelog.json`
7. `rules.catalog.normalized.json`
8. `rules.catalog.attribution.json`
9. `rules.catalog.changelog.json`

## Constraints
1. Importer validates spell license metadata (`ORC Notice` / `OGL` for MVP policy).
2. Runtime app does not fetch spell data from network.
3. Importer is development-time only.

## Catalog Script
Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_catalog_dataset.ps1 `
  -FoundryPf2eRoot "<path-to-foundry-pf2e-root>" `
  -SourceCommit "<foundry-commit-hash>"
```

The catalog script writes only to `tools/importer/out/catalog/`. Stage 1 does not copy `catalog.db` into app assets and does not change runtime behavior.

Included data is limited to player-facing PF2e Item packs: ancestries, heritages, backgrounds, classes, class features, ancestry features, feats, spells, equipment, actions, conditions, deities, familiar abilities, and player-relevant effect/reference packs. SF2e, Actor, JournalEntry, RollTable, Macro, and image binaries are excluded. Image paths are retained and PF2e-local missing paths are audited.

The generated manifest and database include `catalog_schema_version`, `source_commit`, PF2e system version, included pack counts, issue counts, and database size. Raw record payloads are preserved in SQLite as gzip-compressed blobs to keep the APK-size path realistic. The audit reports malformed JSON, duplicate IDs, unresolved UUIDs, missing localization, suspicious traits, missing source metadata, missing PF2e images, and size-budget warnings.

Default size budgets are a warning above 40 MiB and a failure above 80 MiB. Use `-AllowOversize` only when deliberately reviewing a larger catalog.

During migration:
1. Stage 1: catalog importer coexists with legacy scripts; no app behavior changes.
2. Stage 2: app reads from `catalog.db`; legacy scripts become compatibility/debug tools.
3. Cleanup: old runtime JSON assets and superseded scripts can be removed or archived once all consumers use catalog repositories.

## Spell Script
Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\importer\import_spells.ps1 `
  -InputDir "<path-to-foundry-spell-json>" `
  -OutputDir ".\tools\importer\out" `
  -SourceCommit "<foundry-commit-hash>"
```

Notes:
1. Input directory is scanned recursively for JSON files.
2. Non-spell entries are skipped.
3. Import fails on parse errors, duplicate IDs, and invalid/missing licenses.
4. Cantrips are normalized to `rank = 0` even when upstream `system.level.value` is `1`.
   Detection uses both the `cantrip` trait and source path segment (`.../cantrip/...`) as fallback.

## Project-Level Dataset Update
To regenerate and install the spell dataset into app assets:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_spell_dataset.ps1 `
  -FoundrySpellsDir "<path-to-foundry\packs\pf2e\spells>" `
  -SourceCommit "<foundry-commit-hash>"
```

This script also enforces a minimum spell count guard (`MinSpellCount`, default `1000`).

To regenerate and install the class dataset into app assets:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_class_dataset.ps1 `
  -FoundryClassesDir "<path-to-foundry\packs\pf2e\classes>" `
  -SourceCommit "<foundry-commit-hash>"
```

This script enforces a minimum class count guard (`MinClassCount`, default `15`).

To regenerate and install the rules catalog into app assets:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_rules_catalog.ps1 `
  -FoundryPf2ePacksDir "<path-to-foundry\packs\pf2e>" `
  -SourceCommit "<foundry-commit-hash>"
```

This script enforces a minimum rules option count guard (`MinOptionCount`, default `200`).
