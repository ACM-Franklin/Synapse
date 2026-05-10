# Current Task: Phase 5 Data Proof Closeout

## Goal

Close Phase 5 by upgrading the real DB to the current schema and making the
proof pass repeatable.

## Plan

- [x] Capture a live SQLite baseline from the current guild dataset.
- [x] Lock the first acceptable mismatch category for thread seed messages.
- [x] Add concrete reconciliation report queries for missing, extra, stale, and deleted records.
- [x] Sanity-check the reconciliation pack against live data, including deleted-state after the real DB upgrade.
- [x] Add a repeatable proof report command and validate it against the real DB.

## Review

- Added `DATA_PROOF_LIVE_BASELINE.md` with the first real SQLite snapshot from the active guild dataset, including baseline counts, anchor entities, and integrity findings.
- Locked the first acceptable mismatch category for thread seed-message counts using the exact JDA `ThreadChannel.getMessageCount()` behavior.
- Added a real migration file for historical scan state so older databases can be upgraded safely.
- Added reconciliation queries for missing, orphaned, stale, inactive, and deleted-state checks, then ran them against the live DB.
- Fixed `MigrationManager` so fresh databases use the schema snapshot while existing databases run upgrade scripts only, which avoids replaying additive migrations onto already-current tables.
- Added `2_message_delete_state.sql`, applied both real upgrade scripts to `target/synapse.sqlite`, and recorded them in the live `migrations` table.
- Added `scripts/data-proof-report.sh` and validated it against the upgraded real DB; the full reconciliation set now returns clean zeroes where expected.
