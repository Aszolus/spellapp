# Rules Source-of-Truth Plan

## 1. Goal
Build a deterministic, offline rules pipeline that derives a character's spellcasting state from selected character options, not from ad-hoc UI toggles.

This must cover:
1. Class and class features.
2. Archetypes and archetype spellcasting feats.
3. General/skill/class/archetype feats that affect spellcasting.
4. Ancestry and ancestry feats.
5. Background effects.

## 2. Why This Is Needed
Current approach is too coarse for PF2e exceptions:
1. Many slot/cantrip/preparation effects are exception-based.
2. Archetype spellcasting progression is staged by feat benefits (Basic/Expert/Master), not a simple fixed table.
3. Features can add permanent preparations, extra cantrips, or slot adjustments.

## 3. Architecture Target
## 3.1 Layers
1. Raw dataset layer (imported PF2e JSON assets).
2. Normalized rules catalog (typed effect records).
3. Character build state (chosen class/ancestry/background + selected feats/features).
4. Derivation engine (pure function from build state -> casting state).
5. Runtime/session state (prepared assignments, expended state, session log).

## 3.2 Separation Rules
1. Importer parses and normalizes; no runtime UI logic.
2. Derivation engine is deterministic and side-effect free.
3. UI reads derived state only; UI never invents slot counts.

## 4. Data Model Additions
## 4.1 Character Build State
Add persistent selections for:
1. Ancestry.
2. Background.
3. Archetypes.
4. Selected feats/features by source and level.

## 4.2 Rules Effect DSL (typed)
Start with a minimal sealed model:
1. `GrantCantrips(count, tradition, sourceTrack)`
2. `GrantPreparedSlots(rank, count, sourceTrack)`
3. `GrantSpellcastingTrack(trackKey, progressionType, tradition, castingStyle)`
4. `GrantPermanentPreparedSpell(spellIdOrSelector, rank, sourceTrack)`
5. `AdjustSlotCounts(rule)` (for exceptions like "except top two ranks")
6. `GrantSignatureSpell(count, sourceTrack)` (for spontaneous paths later)

## 5. Import Pipeline Plan
Create a new importer workflow that reads:
1. `packs/pf2e/classes`
2. `packs/pf2e/class-features`
3. `packs/pf2e/feats`
4. `packs/pf2e/ancestries`
5. `packs/pf2e/backgrounds`
6. `packs/pf2e/journals` (for archetype spellcasting baseline references)

Outputs:
1. `rules.catalog.normalized.json` (typed effects)
2. `rules.catalog.attribution.json`
3. `rules.catalog.changelog.json`

## 6. Execution Phases
## Phase A - Foundation
1. Add `core-rules` module back as a derivation engine + effect model (no warnings logic yet).
2. Add new build-state entities/tables in `core-data`.
3. Add repository interfaces for build selections.

## Phase B - Minimal Vertical Slice
1. Support class-only prepared casters (Wizard/Cleric/Druid).
2. Derive slots/cantrips from typed rules catalog.
3. Keep existing session/preparation UI functional with derived slot source.

## Phase C - Archetype Spellcasting (Correct Model)
1. Add dedication + Basic/Expert/Master feat selection to character edit.
2. Implement archetype spellcasting benefits progression from selected feats.
3. Replace current archetype-count shortcut with feat-driven track creation.

## Phase D - Ancestry and Background Effects
1. Add ancestry/background selection in character edit.
2. Apply their spellcasting-related effects via same effect DSL.
3. Validate interaction order with class and archetype effects.

## Phase E - Exceptions and Edge Cases
1. Permanent prepared spells.
2. Extra cantrips from features.
3. Slot-count modifiers with constraints (including "except top two").
4. Source tagging on all derived outcomes for UI explainability.

## 7. UI Plan
Character editor should evolve in this order:
1. Core identity: class, ancestry, background.
2. Archetype selection + feat picks (dedication/basic/expert/master).
3. Feature/feat picker (filtered to spellcasting-relevant first).
4. Read-only "Derived Spellcasting Summary" panel showing exactly why slots/cantrips exist.

Preparation screen:
1. Display-only for derived tracks/caps.
2. No track mutation controls.
3. Continue allowing spell assignment/casting/undo.

## 8. Validation Strategy
1. Add deterministic snapshot tests for derivation output by build state.
2. Add fixture tests for known archetype spellcasting progressions.
3. Add regression fixtures for ancestry/background spellcasting grants.
4. Add migration tests for new build-state tables.

## 9. Acceptance Criteria
1. Slot/cantrip tracks come only from derivation engine output.
2. Archetype slots are feat-driven, not manually counted.
3. Ancestry/background spellcasting effects can alter derived state.
4. Each derived slot/cantrip/permanent prep entry has source metadata.
5. App remains fully offline with local datasets only.

## 10. Immediate Next Tickets
1. Reintroduce `core-rules` with effect DSL and `deriveCastingState(buildState)` interface.
2. Add build-state persistence schema (`character_build_*` tables).
3. Add importer skeleton for `rules.catalog.normalized.json`.
4. Refactor character editor to store archetype feat selections (starting with wizard/cleric/druid dedications + basic/expert/master).
