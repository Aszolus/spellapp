# Dataset Importers

This directory contains build-time ingestion tools for spells, classes, and rules catalog data.

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
