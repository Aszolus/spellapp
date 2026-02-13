# Spell Importer (Phase 0 Skeleton)

This directory contains the build-time spell ingestion tool.

## Intended Inputs
1. PF2e spell JSON files from `foundryvtt/pf2e` (`packs/data/spells`).
2. A pinned upstream commit hash.

## Intended Outputs
1. `spells.normalized.json`
2. `spells.attribution.json`
3. `spells.changelog.json`

## Constraints
1. Importer validates spell license metadata (`ORC Notice` / `OGL` for MVP policy).
2. Runtime app does not fetch spell data from network.
3. Importer is development-time only.

## Current Script
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

## Project-Level Dataset Update
To regenerate and install the dataset into app assets:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\update_spell_dataset.ps1 `
  -FoundrySpellsDir "<path-to-foundry\packs\data\spells>" `
  -SourceCommit "<foundry-commit-hash>"
```

This script also enforces a minimum spell count guard (`MinSpellCount`, default `1000`).
