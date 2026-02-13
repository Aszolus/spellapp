# Class Dataset Integration Plan (PF2e `packs/pf2e/classes`)

## 1. Goal
Replace hardcoded class metadata in the app with a local, normalized dataset sourced from Foundry PF2e class JSON, while keeping runtime fully offline and preserving room for multiclass/archetype slot tracks.

## 2. Scope (This Initiative)
1. Ingest class metadata from `pf2e/packs/pf2e/classes`.
2. Normalize only fields we can trust structurally.
3. Load normalized class metadata from `app/src/main/assets`.
4. Use metadata for character creation/edit and display labels.
5. Keep slot progression logic in app rules layer for now.

## 3. Non-Goals (For This Initiative)
1. Full automatic spell-slot progression extraction from class prose.
2. Full multiclass/archetype mechanical implementation.
3. Replacing existing character persistence model in one step.

## 4. Phase Plan

### Phase A - Normalization Pipeline
1. Add importer script `tools/importer/import_classes.ps1`.
2. Source input: `D:\\pf2e\\packs\\pf2e\\classes`.
3. Validate each entry:
   - `type == "class"`
   - license in allowed set (`ORC`, `OGL`, optional `ORC Notice`)
4. Emit `classes.normalized.json` with:
   - `id` (slug from file name)
   - `name`
   - `publication.license`
   - `publication.remaster`
   - `keyAbilityOptions`
   - `spellcastingFlag` (`system.spellcasting`)
   - `classFeatureRefs` (UUID list from `system.items`)
5. Emit `classes.attribution.json` and `classes.changelog.json`.
6. Add update wrapper script similar to spells:
   - `scripts/update_class_dataset.ps1`

### Phase B - App Runtime Loading
1. Add `ClassMetadataRepository` in app/core-data boundary.
2. Implement `AssetClassMetadataRepository` reading `classes.normalized.json`.
3. Add fallback strategy:
   - if asset missing/corrupt, use existing static definitions for phase 1 classes.
4. Wire `CharacterListViewModel` to repository (already partially abstracted by source interface).

### Phase C - UI Adoption
1. Character editor class chips come from repository data.
2. Class labels and key ability options come from loaded definitions.
3. Preserve phase-1 filter:
   - prepared casters only
   - remaster default, optional legacy toggle later

### Phase D - Slot Rules Foundation
1. Add explicit slot rules abstraction:
   - `SpellSlotProgressionRuleSet`
   - input: classId, level, track config
   - output: slot caps by rank and track
2. Seed with hand-authored prepared-caster rules (wizard/cleric first).
3. Keep class metadata and slot rules separate so multiclass/archetype can compose later.

## 5. Refactor-First Tasks (Do Now While Building This)
1. Keep UI dependent on interfaces (`CharacterClassDefinitionSource` / repository), not static objects.
2. Keep repository transactions as the only mutation path for prepared slot state.
3. Move route orchestration into dedicated nav/state view models (already in progress).
4. Introduce slot `trackKey` end-to-end in persistence model before multiclass work.

## 6. Acceptance Criteria
1. `classes.normalized.json` is generated locally from PF2e source and committed to assets.
2. Character editor no longer hardcodes class chip list.
3. App remains fully offline and builds without network access.
4. Architecture supports adding a second slot track (multiclass/archetype) without nav/UI rewrite.

## 7. Risks
1. Class files do not encode full slot tables in machine-readable form.
   - Mitigation: class metadata + separate slot progression rules layer.
2. Future schema churn in Foundry PF2e JSON.
   - Mitigation: strict importer validation + changelog output + fallback defaults.
