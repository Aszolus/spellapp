# Pathfinder 2e Spell List Manager (Android) - Technical Requirements

## 1. Technical Goal
Deliver a fully self-contained Android app for PF2e spell management with zero runtime network dependencies, fast in-session interaction, and durable local data.

## 2. Hard Constraints
1. The app must function without internet for all features.
2. The app must not make any network calls at runtime.
3. The app must not request `android.permission.INTERNET`.
4. No cloud sync, telemetry, ads SDK, remote config, or online auth in v1.
5. Spell content must be bundled with the app and available immediately after install.

## 3. Platform Baseline
1. Language: Kotlin.
2. UI: Jetpack Compose + Material 3.
3. Architecture: MVVM with clear `ui`, `domain`, and `data` boundaries.
4. Local database: Room over SQLite.
5. Serialization: `kotlinx.serialization` for import/export payloads.
6. Minimum Android version: API 28+.

## 4. Module Structure
1. `app`: Android entrypoint, navigation, DI wiring.
2. `feature-character`: character creation/edit, class profile configuration.
3. `feature-spells`: spell list, search/filter, spell detail, cast flow.
4. `feature-session`: cast log, undo, refocus/rest/new day actions.
5. `core-rules`: warn-only validation engine and rule checks.
6. `core-data`: Room entities/DAOs, repositories, migrations.
7. `core-model`: domain models and shared enums.
8. `tools/importer` (non-Android): build-time spell ingestion pipeline.

## 5. Data Ingestion Pipeline (Build-Time)
1. Source: `foundryvtt/pf2e` spell JSON (`packs/data/spells`).
2. Importer must pin and record upstream commit hash.
3. Importer must preserve: spell ID, name, rank, traits, traditions, text, source, license.
4. Importer must fail if license is missing or unknown.
5. Importer must emit:
   - normalized spell dataset for app bundling,
   - attribution artifact,
   - diff/changelog against previous dataset.
6. Importer is the only component allowed to access network, and only during development/release workflows.

## 6. Runtime Data Model
1. Preloaded spell catalog stored locally in Room at first launch.
2. Character, prepared slots, focus pool, session history stored in Room tables.
3. All write operations for cast/undo/rest must use transactions.
4. All persisted payloads must include schema version.
5. DB migrations must be forward-tested from prior production schema versions.

## 7. Offline and Network Isolation Requirements
1. Build pipeline must fail if `INTERNET` permission appears in merged manifest.
2. Build pipeline must fail if known HTTP client libraries are present in app runtime classpath.
3. Instrumentation tests must run in airplane mode and pass critical user journeys.
4. App startup path must not block on any remote dependency.
5. Any future online feature requires explicit product decision and separate architecture update.

## 8. Rules Engine Requirements
1. MVP rules mode is warn-only and never blocks action execution.
2. Rule evaluation must be deterministic and side-effect free.
3. Rule checks must return structured warning codes and user-facing messages.
4. All cast actions must produce an event log entry with enough data for single-step undo.

## 9. Performance Budgets
1. Cold start to interactive: <= 2 seconds on mid-range target devices.
2. Spell search/filter response: <= 200 ms perceived latency for common filters.
3. Spell detail open from list: <= 150 ms median.
4. Cast action commit (with log write): <= 100 ms median.
5. No visible frame drops during rapid filtering on spell list screen.

## 10. Reliability and Data Integrity
1. Crash-safe persistence: no partial state after interrupted writes.
2. Undo must be idempotent and reversible only for the most recent cast action in MVP.
3. Export format must include checksum and schema version.
4. Import must validate schema, required fields, and checksum before write.
5. Corrupt import files must fail safely with clear error messaging.

## 11. Security and Privacy
1. No account creation required.
2. No user data leaves device.
3. Use Android Storage Access Framework for import/export file access.
4. Avoid broad storage permissions; request only scoped/document-based access.
5. Release builds must not include debug logs containing spell text or user character data.

## 12. Testing Requirements
1. Unit tests:
   - rules evaluation,
   - slot consumption logic,
   - prepared spell assignment logic,
   - migration logic.
2. Integration tests:
   - repository + Room transaction correctness,
   - cast/undo lifecycle,
   - import/export round trip.
3. UI tests:
   - prepared caster combat loop,
   - search/filter workflows,
   - airplane mode workflows.
4. Build verification checks:
   - no `INTERNET` permission,
   - no network client dependency,
   - bundled dataset present and loadable.

## 13. Release and Update Strategy
1. Dataset updates are delivered by shipping a new app build or importing a local dataset file.
2. Every release must include:
   - dataset version,
   - upstream source commit hash,
   - generated attribution.
3. App must support opening old character exports via migration path.
4. If migration fails, app must keep old DB intact and surface recovery options.

## 14. Technical Acceptance Criteria (MVP)
1. App installs and runs with zero network permissions and zero network calls.
2. Prepared-caster play loop (prepare, cast, undo, rest) works entirely offline.
3. Spell browsing and detail views are fully available in airplane mode.
4. Import/export works locally with schema validation and corruption handling.
5. CI gates reject builds that violate network-isolation or license-validation rules.
