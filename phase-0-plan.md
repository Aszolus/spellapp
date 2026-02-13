# Pathfinder 2e Spell App - Phase 0 Plan

## 1. Phase Goal
Build the offline data foundation and a read-only Android spell browser so we can validate data quality, local performance, and UX before cast-state logic.

## 2. Phase 0 Scope
1. Android app shell with navigation and baseline architecture.
2. Local bundled spell dataset generated from `foundryvtt/pf2e` spell files.
3. Build-time importer tool with license validation and attribution output.
4. Room database setup, migrations baseline, and initial seeding flow.
5. Read-only spell browsing:
   - list view,
   - search by name,
   - filter by rank/tradition/trait/rarity,
   - detail view with full text and heightened entries.
6. Zero runtime network guarantees and verification checks.

## 3. Out of Scope (Phase 0)
1. Character creation/edit flows.
2. Slot tracking, cast actions, focus pool, undo/session log.
3. Import/export of character data.
4. Spontaneous caster logic.

## 4. Target Deliverables
1. `app` project that runs on Android with Compose UI.
2. `tools/importer` script/tool that produces normalized spell dataset.
3. Versioned bundled dataset artifact in app assets.
4. Room schema v1 with spell catalog tables and indexes.
5. Spell list/detail screens backed by Room queries.
6. CI checks:
   - no `INTERNET` permission,
   - no network runtime dependency in app module,
   - dataset generation and validation pass.
7. Phase 0 demo checklist completed.

## 5. Proposed Repository Layout
1. `app/`
2. `core-model/`
3. `core-data/`
4. `feature-spells/`
5. `core-ui/`
6. `tools/importer/`
7. `docs/` (optional, if we want architecture decision records)

## 6. Milestones
1. M0 - Project Bootstrap
   - Android project init, Compose setup, module skeleton, DI wiring.
2. M1 - Importer and Dataset
   - Parse source spells, normalize fields, enforce license rules, emit attribution.
3. M2 - Local Storage
   - Room entities/DAOs, seed pipeline from bundled dataset, query indexes.
4. M3 - Read-Only Spell UX
   - Spell list/search/filter/detail in Compose.
5. M4 - Hardening and Validation
   - performance checks, airplane-mode checks, CI policy gates, release notes for dataset provenance.

## 7. Ticket Backlog (Execution Order)

### EPIC A: Bootstrap
1. P0-001: Initialize Android project (Kotlin, Compose, Material3, minSdk 28).
2. P0-002: Create module structure (`app`, `core-model`, `core-data`, `feature-spells`, `core-ui`).
3. P0-003: Add navigation shell with placeholder Spell List and Spell Detail routes.
4. P0-004: Add static analysis + formatting + test baseline config.

### EPIC B: Importer
1. P0-010: Build importer CLI in `tools/importer` with input directory support.
2. P0-011: Map Foundry spell JSON into normalized schema.
3. P0-012: Add license validation gate (`ORC Notice` + `OGL` only for MVP).
4. P0-013: Emit outputs:
   - `spells.normalized.json`,
   - `spells.attribution.json`,
   - `spells.changelog.json`.
5. P0-014: Record source commit hash and dataset version metadata.
6. P0-015: Add importer tests with fixtures for malformed and missing-license entries.

### EPIC C: Local Data
1. P0-020: Define Room entities for spells, traits, traditions, and search tokens.
2. P0-021: Add DAO queries for list, search, and filters.
3. P0-022: Add DB indexes for name/rank/tradition/rarity lookups.
4. P0-023: Implement first-run seed from bundled normalized dataset.
5. P0-024: Add schema versioning and migration test harness.

### EPIC D: Spell Browser
1. P0-030: Implement spell list screen with paging/lazy rendering.
2. P0-031: Implement search input with debounce and local query.
3. P0-032: Implement filters (rank/tradition/trait/rarity).
4. P0-033: Implement spell detail screen (actions, targets, save, duration, heightened text, source/license).
5. P0-034: Add empty/error states for missing data and query edge cases.

### EPIC E: Network Isolation and Quality Gates
1. P0-040: Add build check to fail if merged manifest includes `android.permission.INTERNET`.
2. P0-041: Add dependency check to fail if HTTP client/runtime networking libs are introduced in app runtime.
3. P0-042: Add instrumentation smoke test in airplane mode for list/detail flow.
4. P0-043: Add startup and filter latency benchmark tests against performance budgets.
5. P0-044: Add release checklist item for dataset attribution/provenance inclusion.

## 8. Phase 0 Acceptance Criteria
1. App installs and runs with no network permission and no runtime network calls.
2. App seeds local spell DB from bundled dataset on first launch.
3. User can browse/search/filter/view spells fully offline in airplane mode.
4. Importer enforces license metadata and fails on invalid input.
5. CI gates pass and block regressions for network isolation + dataset validation.

## 9. Risks and Mitigations
1. Risk: Upstream schema drift in Foundry spell JSON.
   - Mitigation: importer schema adapter + fixture tests + explicit required-field checks.
2. Risk: Search/filter performance degradation with larger datasets.
   - Mitigation: Room indexes + measured query benchmarks + pagination.
3. Risk: Data-text formatting inconsistencies in spell descriptions.
   - Mitigation: normalization rules + render tests for complex text blocks.

## 10. Definition of Done (Phase 0)
1. All P0 tickets complete and merged.
2. Acceptance criteria met on at least one phone and one tablet profile.
3. `requirements.md` and `technical-requirements.md` remain consistent with implementation decisions.
4. Phase 1 kickoff brief prepared with confirmed carry-forward architecture choices.
