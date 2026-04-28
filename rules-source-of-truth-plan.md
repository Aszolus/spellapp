# Rules Source-of-Truth Plan

## 1. Goal
Build a deterministic, offline rules pipeline that derives a character's visible sheet state from selected character options, not from ad-hoc UI toggles or hand-entered derived stats.

This must cover:
1. Ancestry, heritage, and ancestry feats.
2. Background effects.
3. Class, class features, and class choices.
4. Attribute modifiers, skills, saves, HP, Perception, DCs, and other sheet facts.
5. General, skill, class, ancestry, and archetype feats.
6. Spellcasting state where character choices grant or modify it.

## 2. Why This Is Needed
Current approach is too coarse for PF2e exceptions:
1. Character creation has ordered steps, required prompts, and source-specific grants.
2. Attribute modifier boosts/flaws, skills, feats, and class choices must be explainable by source.
3. Many slot/cantrip/preparation effects are exception-based.
4. Archetype spellcasting progression is staged by feat benefits, not a simple fixed table.
5. Features can add proficiencies, permanent preparations, extra cantrips, or slot adjustments.

## 3. Architecture Target
## 3.1 Layers
1. Raw dataset layer (imported PF2e JSON assets).
2. Normalized rules catalog (typed effect records).
3. Character build state (chosen class/ancestry/background + selected feats/features).
4. Derivation engine (pure function from build state -> visible character facts).
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
1. `ApplyAttributeBoost(attribute, source)`
2. `ApplyAttributeFlaw(attribute, source)`
3. `GrantSkillTraining(skill, source)`
4. `GrantFeatSlot(level, featType, source)`
5. `GrantPrompt(promptKey, options, source)`
6. `GrantCantrips(count, tradition, sourceTrack)`
7. `GrantPreparedSlots(rank, count, sourceTrack)`
8. `GrantSpellcastingTrack(trackKey, progressionType, tradition, castingStyle)`
9. `GrantPermanentPreparedSpell(spellIdOrSelector, rank, sourceTrack)`
10. `AdjustSlotCounts(rule)` (for exceptions like "except top two ranks")
11. `GrantSignatureSpell(count, sourceTrack)` (for spontaneous paths later)

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
1. Support level-one character creation.
2. Derive attribute modifiers, trained skills, HP, saves, Perception, and active feat/prompt slots.
3. Keep future choices in the Level Workbench and nonblocking until active.

## Phase C - Spellcasting Derivation
1. Support class-only prepared casters from selected class choices.
2. Derive slots/cantrips from typed rules catalog.
3. Keep preparation UI functional with derived slot source.

## Phase D - Archetypes and Optional Expansions
1. Add dedication and archetype feat selection to character edit.
2. Implement archetype spellcasting benefits from selected feats.
3. Add optional rules only after core creation and leveling are stable.

## Phase E - Exceptions and Edge Cases
1. Permanent prepared spells.
2. Extra cantrips from features.
3. Slot-count modifiers with constraints (including "except top two").
4. Source tagging on all derived outcomes for UI explainability.

## 7. UI Plan
Character editor should evolve in this order:
1. Level-one creation: ancestry, heritage, background, class, attributes, skills, required prompts.
2. Level Workbench: all feats and post-level-one choices.
3. Character summary: derived facts with source explanations.
4. Spellcasting summary: exactly why slots/cantrips exist once spellcasting derivation is active.

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
1. Level-one builder facts come only from selected choices and rules derivation.
2. Slot/cantrip tracks come only from derivation engine output once spellcasting derivation is active.
3. Archetype slots are feat-driven, not manually counted.
4. Each derived modifier, proficiency, slot, cantrip, and permanent prep entry has source metadata.
5. App remains fully offline with local datasets only.

## 10. Immediate Next Tickets
1. Stabilize level-one builder derivation and validation against `docs/CharacterCreation.md`.
2. Expand build-state persistence for generated prompt and feat slots.
3. Add importer skeleton for richer `rules.catalog.normalized.json` effects.
4. Move spellcasting, archetype, and optional-rule features onto the same derived-fact model.
