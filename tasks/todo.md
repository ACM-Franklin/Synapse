# Current Task: Verification Foundation

## Goal

Create a private roadmap to done and add the first meaningful automated tests
for the completed ledger and live message mutation phases.

## Plan

- [x] Add ignored private `ROADMAP.md` with continuous-update instructions.
- [x] Add SQLite-backed test support.
- [x] Add schema and DAO contract tests.
- [x] Add rule engine reward ledger tests.
- [x] Add message delete reversal tests.
- [x] Run Maven, schema, diagnostics, and whitespace verification.

## Review

- Added private ignored `ROADMAP.md` with a phase-by-phase path to done and an explicit rule to keep it updated.
- Added SQLite-backed JDBI test support using a temp database file, not in-memory SQLite connection roulette.
- Added schema smoke coverage for reward ledger and live message mutation schema pieces.
- Added DAO tests for message upsert immutability, tombstones, reaction counters, and unreversed reward lookup.
- Added rule engine tests proving ledger-backed currency awards, event deduplication, and cooldown suppression.
- Added delete reversal coverage proving tombstone behavior, reversal ledger entries, balance correction, and repeated-delete idempotency.
- Fixed cooldown timestamp comparison in `RuleEngine`; SQLite stores `CURRENT_TIMESTAMP` with a space separator, while Java `LocalDateTime.toString()` uses `T`, which made the old comparison unreliable.
- Updated message deletion lookup to ignore already-deleted messages so repeated delete handling does not mint duplicate delete events.
