# Current Task: Phase-One Ingestion Coverage

## Goal

Map the current ingestion surface, identify live and historical gaps, and begin
closing non-speculative live ingestion gaps that already have locked product
semantics.

## Plan

- [x] Map existing live scanner coverage.
- [x] Map existing historical scanner coverage.
- [x] Read exact JDA event syntax for any handlers being implemented.
- [x] Document the ingestion coverage matrix.
- [x] Implement safe live-message mutation support where semantics are locked.
- [x] Run Maven and schema verification.

## Review

- Added `INGESTION_COVERAGE_MATRIX.md` to document live, historical,
  reconciliation, missing, and deferred ingestion surfaces.
- Added message deletion tombstone columns to `messages`.
- Added live message update handling for current-state snapshot refresh.
- Added live message delete handling with reward ledger reversals for unreversed
  currency awards tied to the original message event.
- Added live reaction add/remove handling for per-emoji counts and aggregate
  message reaction counts.
- Explicitly enabled `GUILD_MESSAGES` because message update/delete events require
  it for guild text channels.
- Left edit reward delta re-evaluation deferred because doing it before
  subject-based delta accounting would create double-dip risk.
- Verified with `./mvnw test`.
- Verified schema loads with `sqlite3 :memory: < src/main/resources/schemas/synapse.sql`.
- Verified whitespace with `git diff --check`.
