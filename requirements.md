# Pathfinder 2e Spell List Manager (Android)

## 1. Objective
Create an Android app to manage Pathfinder 2e spell lists during live play, with fast access, low friction, and rules-aware behavior for multiple classes and spellcasting traditions.

## 2. Primary Use Cases
1. Build and maintain spell lists for one or more characters.
2. View all currently available spells during combat in a few taps.
3. Track prepared slots, spontaneous slots, cantrips, focus points, and expend/recover state.
4. Filter spells quickly by level, action cost, tradition, traits, school, and current tactical needs.
5. Open a spell detail view with full text and key mechanics while at the table.

## 3. Target Users
1. Single player managing one or more PF2e spellcasters.
2. GM managing NPC spellcasters.
3. Offline-first users playing at tables without reliable internet.

## 4. Platform and Constraints
1. Android-first (phone priority, tablet supported).
2. Usable with one hand, large touch targets, minimal text entry.
3. Offline-first: all spell browsing/filtering/detail/casting features must work without internet.
4. Fast startup and low-latency filtering (target: sub-200ms perceived response for common filters on modern devices).

## 5. In Scope (MVP)
1. Local character profiles with:
   - Name, level, class/archetype spellcasting profile.
   - Key casting ability and spell DC/attack modifiers (editable).
2. Spell database import from `foundryvtt/pf2e` (`packs/data/spells`) with preserved source/license metadata.
3. Spell list management:
   - Add/remove spells known or in spellbook/repertoire.
   - Mark prepared spells and quantities where relevant.
   - Spontaneous-only features (e.g., signature spells) are deferred to Phase 2.
4. Session tracking:
   - Spend/restore spell slots by rank.
   - Track focus points and recharge.
   - Mark active durations and sustained spells.
5. Search and filter:
   - Name, rank, tradition, traits, rarity, school, cast actions, damage/heal tags.
6. Spell detail page:
   - Cast actions, components, targets, range, save/attack, duration, heightened entries, traits, and full rules text.
7. Data persistence and backup:
   - Local storage.
   - Export/import character data (JSON file).

## 6. Out of Scope (MVP)
1. Full character builder (ancestry/skills/equipment automation).
2. Encounter tracking for all party members.
3. VTT sync or cloud account system.
4. Homebrew rule engine automation beyond basic custom notes/tags.

## 7. Functional Requirements

### 7.1 Character and Class Rules Model
1. Must support multiple spellcasting paradigms:
   - Prepared (e.g., Wizard, Cleric, Druid) in MVP.
   - Spontaneous (e.g., Sorcerer, Bard, Oracle) in Phase 2.
   - Bounded/partial casters where relevant.
2. Must support traditions (arcane/divine/occult/primal), including class-specific restrictions.
3. Must support rank-based spell slots and cantrip auto-scaling behavior (display at character rank context).
4. Must allow per-character overrides for unusual feats/class features.

### 7.2 Spell Lifecycle in Play
1. User can cast a spell in <= 2 taps from list view.
2. Casting updates available slots/focus and logs an entry in current session history.
3. User can undo last cast action quickly.
4. User can perform "refocus", "rest", and "new day preparation" workflows with confirmation.

### 7.3 Prepared vs Spontaneous Handling
1. MVP (prepared casters):
   - Assign spells to specific slots/ranks.
   - Consume exactly prepared entries unless flexible options are enabled.
2. Phase 2 (spontaneous casters):
   - Consume slot by rank and choose known spell at cast time.
   - Handle signature spell behavior in heightened selection UI.
3. Hybrid edge cases should be configurable with toggles and notes.

### 7.4 Rules Validation Mode
1. MVP validation mode is permissive helper (warn-only).
2. App should surface warnings for likely illegal actions but allow user override.
3. Strict lockout mode is out of scope for MVP.

### 7.5 Focus Spells
1. Track focus pool max/current.
2. Mark focus spell cast and decrement pool.
3. Support refocus actions and limits per table rules.

### 7.6 Heightening and Variants
1. Display all heightened effects clearly.
2. At cast time, select cast rank; app shows resulting text/stat changes.
3. Allow custom spell notes for house rules or remastered text differences.

## 8. Non-Functional Requirements
1. Performance:
   - Open app to usable state in < 2 seconds on mid-range devices.
2. Reliability:
   - No data loss on app restart/crash.
3. UX:
   - Dark and light themes.
   - High-contrast option for table lighting.
4. Privacy:
   - No required account for core features.
5. Maintainability:
   - Rules/data updates must be separable from app code.
6. Defaults:
   - Default terminology/data presentation is remaster.
   - Legacy terminology/rules view is available as an optional toggle.

## 9. Data and Content Requirements
1. Primary candidate source: `foundryvtt/pf2e` spell JSON (`packs/data/spells`), ingested through a local build script.
2. Dataset ingestion must preserve per-spell metadata:
   - `source` (book + page).
   - `license` (e.g., `ORC Notice`, `OGL`).
3. Content policy for MVP:
   - Include both `ORC Notice` and `OGL` spells.
   - Keep license metadata visible and queryable so users can filter by license if needed.
4. Legal gating requirement:
   - Build must fail if any included spell is missing a recognized license field.
   - Generate an attribution file from included sources/licenses at build time.
5. Update strategy:
   - Pin a specific upstream commit hash for reproducible imports.
   - Re-run import tool on demand and produce a changelog of added/changed/removed spells.
   - Runtime app behavior must not make any network calls.
   - Data refreshes occur only via shipping a new app build or local on-device file import.
6. Store spell data in versioned local format (e.g., JSON + schema version).
7. Include migration strategy for app updates that alter data structure.

## 10. Proposed MVP Screens
1. Character List.
2. Character Dashboard (slots/focus summary + quick actions).
3. Spell Browser/Search.
4. Prepared/Repertoire Manager.
5. Cast Flow modal/page.
6. Session Log and Undo.
7. Settings (rules toggles, backup/export).

## 11. Acceptance Criteria (MVP)
1. User can create Wizard and Cleric profiles and cast prepared spells with correct slot handling.
2. User can complete a full combat encounter using only app controls without manual paper tracking.
3. User can close/reopen app and session state remains intact.
4. User can export one character and import it on another device.
5. In Android airplane mode, user can search, view, and cast spells with no errors or missing spell text.
6. App defaults to remaster presentation and can switch to a legacy terminology toggle.

## 12. Locked Decisions
1. Technical spell source is `foundryvtt/pf2e` (`packs/data/spells`).
2. Rules behavior is permissive helper (warn-only) for MVP.
3. Class rollout is prepared casters first, spontaneous casters in Phase 2.
4. App defaults to remaster with an optional legacy toggle.
5. Sync strategy is fully local-only (no network sync).

## 13. Suggested Delivery Phases
1. Phase 0: Data model + local spell dataset + read-only browsing.
2. Phase 1: Prepared caster workflows (profiles, prepared slots, cast flow, focus, undo/session log).
3. Phase 2: Spontaneous caster workflows (repertoire, signature spells, heightened spontaneous casting UX).
4. Phase 3: Backup/import-export + polish + accessibility pass.
