# Pathfinder 2e Spell App - Phase 1 Plan

## 1. Phase Goal
Deliver the prepared-caster gameplay loop so a player can run Wizard/Cleric spellcasting during a live session entirely offline.

## 2. Phase 1 Scope
1. Local character profiles for prepared casters (Wizard and Cleric first).
2. Prepared spell assignment by rank/slot, including cantrip visibility.
3. In-session cast flow that consumes prepared entries correctly.
4. Focus pool tracking with refocus support.
5. Session history and single-step undo for the most recent cast action.
6. Day-cycle actions: `Refocus`, `Rest`, and `New Day Preparation` with confirmation.
7. Warn-only rules helper that never blocks actions.
8. Remaster-first terminology with optional legacy toggle.
9. Preserve strict offline runtime model and zero network permissions.

## 3. Out of Scope (Phase 1)
1. Spontaneous caster logic (Sorcerer/Bard/Oracle, signature spells, repertoire casting).
2. Cloud sync, telemetry, ads SDKs, or any runtime network calls.
3. Strict lockout rules mode (warn-only remains default behavior).
4. Full backup/import-export workflows (planned for later phase).
5. Expanded automated test harness and migration harness hardening (explicitly deferred for now).

## 4. Target Deliverables
1. Character list/create/edit flow for prepared casters.
2. Prepared slot management screens with rank-based assignment.
3. Cast action UX (<= 2 taps from character dashboard for common casts).
4. Session log with last-action undo.
5. Focus and day-cycle actions implemented with transactional persistence.
6. `core-rules` warn-only validation pipeline integrated into cast flow.
7. Settings toggle for remaster default with optional legacy terminology view.
8. Updated docs reflecting Phase 1 architecture and behavior.

## 5. Module Impact
1. `feature-character`: new module for character profile flows.
2. `feature-session`: new module for dashboard, cast log, undo, and day-cycle actions.
3. `core-rules`: new module for warn-only rule evaluation.
4. `core-data`: extend Room schema for characters, prepared slots, focus, and session log.
5. `feature-spells`: integrate character context and cast actions from spell detail/list.
6. `app`: navigation and DI updates for new modules.

## 6. Milestones
1. M1 - Character Storage and Schema
   - Add Room schema v2 for characters, prepared slots, focus state, session events.
   - Add transactional repository methods for cast/undo/day-cycle actions.
2. M2 - Character Setup UX
   - Character list/create/edit for Wizard/Cleric.
   - Class profile defaults and editable casting stats.
3. M3 - Prepared Spell Management
   - Assign prepared spells to slots/ranks.
   - Cantrip presentation and daily preparation workflow.
4. M4 - In-Session Loop
   - Dashboard summary, cast action, focus tracking, undo.
   - `Refocus`, `Rest`, `New Day Preparation` flows.
5. M5 - Rules Warnings + Settings
   - Warn-only rule checks and warning display/override.
   - Remaster default + legacy toggle integration.
6. M6 - Offline Hardening + Demo Readiness
   - Confirm no-network constraints still pass.
   - Manual Phase 1 demo checklist completed.

## 7. Ticket Backlog (Execution Order)

### EPIC A: Data Model and Persistence
1. P1-001: Add domain models for `CharacterProfile`, `PreparedSlot`, `FocusState`, `SessionEvent`.
2. P1-002: Add Room entities/tables and indexes for character/session data.
3. P1-003: Implement DAOs and repository interfaces for character lifecycle + session actions.
4. P1-004: Add transactional write APIs for cast, undo-last-cast, refocus, rest, new day.
5. P1-005: Add DB schema bump and migration path from Phase 0 schema.

### EPIC B: Character Setup
1. P1-010: Implement character list screen.
2. P1-011: Implement create/edit character flow for Wizard/Cleric.
3. P1-012: Add casting profile fields (level, key ability, spell DC/attack modifiers).
4. P1-013: Add per-character toggles for relevant prepared-caster edge cases.

### EPIC C: Prepared Spells
1. P1-020: Implement prepared slot assignment UI by rank.
2. P1-021: Add filtering/selecting spells while preparing slots.
3. P1-022: Implement slot consumption rules for prepared casting.
4. P1-023: Add clear preparation reset for new day flow.
5. P1-024: Add cantrip section behavior in character context.

### EPIC D: Session Runtime UX
1. P1-030: Build character dashboard (slots/focus/session summary).
2. P1-031: Implement cast action flow from spell list/detail in character context.
3. P1-032: Write session event entries for all cast/day-cycle actions.
4. P1-033: Implement single-step undo for most recent cast event.
5. P1-034: Add `Refocus`, `Rest`, and `New Day Preparation` actions with confirmation dialogs.

### EPIC E: Rules Helper (Warn-Only)
1. P1-040: Create `core-rules` warn-only evaluator interface and warning codes.
2. P1-041: Implement prepared-caster checks (slot availability, preparation mismatch, focus bounds).
3. P1-042: Surface warnings in cast flow with explicit override action.
4. P1-043: Persist warning outcomes in session event metadata.

### EPIC F: Settings and Terminology
1. P1-050: Add app settings screen scaffold if missing.
2. P1-051: Add `Legacy Terminology` toggle (off by default).
3. P1-052: Wire terminology mapping for UI labels where remaster/legacy diverge.

### EPIC G: Validation and Demo
1. P1-060: Re-run no-network checks (`INTERNET` permission and banned deps).
2. P1-061: Manual airplane-mode verification for full prepared-caster loop.
3. P1-062: Performance smoke pass for cast/filter interactions on target device.
4. P1-063: Phase 1 demo checklist and release notes update.

## 8. Phase 1 Acceptance Criteria
1. User can create Wizard and Cleric profiles locally.
2. User can prepare spells by slot/rank and cast them with correct consumption behavior.
3. User can track/use/refocus focus points through the app.
4. User can undo the most recent cast action.
5. User can run `Refocus`, `Rest`, and `New Day Preparation` workflows with confirmations.
6. Warn-only rules guidance appears for likely illegal actions and allows override.
7. App remains fully offline with no runtime network calls and no `INTERNET` permission.
8. App defaults to remaster terminology and supports optional legacy toggle.

## 9. Risks and Mitigations
1. Risk: Rules complexity causes over-engineered validation early.
   - Mitigation: keep validator scoped to warn-only checks needed for prepared-caster loop.
2. Risk: UX friction from too many taps during combat.
   - Mitigation: dashboard shortcuts + cast flow tuned for <= 2 taps in common path.
3. Risk: Data integrity issues around undo/day-cycle writes.
   - Mitigation: transaction boundaries and append-only session event model.
4. Risk: Scope creep into spontaneous casting.
   - Mitigation: explicit Phase 2 boundary; reject repertoire/signature work in Phase 1.

## 10. Definition of Done (Phase 1)
1. All Phase 1 tickets complete except explicitly deferred hardening items.
2. Acceptance criteria validated on at least one phone profile.
3. Documentation updated and aligned:
   - `requirements.md`
   - `technical-requirements.md`
   - `phase-1-plan.md`
4. Phase 2 kickoff brief prepared with spontaneous-caster scope only.
