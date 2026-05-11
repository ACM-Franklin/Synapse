# Current Task: Phase 6 Bulk Replay Orchestration

## Goal

Make stored message reward recomputation runnable across the whole active
message set instead of one subject at a time.

## Plan

- [x] Add a cursor-based active-message replay query so orchestration can walk stored messages deterministically in batches.
- [x] Extend the replay service with a bulk active-message recomputation pass and a concrete run summary.
- [x] Prove that batched replay skips deleted messages, advances by cursor, and applies replay across multiple batches cleanly.
- [x] Validate the slice with focused DAO and replay-service tests, then rerun the broader suite and hygiene checks.

## Review

- Added a cursor-based replay candidate query in `MessageEventDao` so active stored messages can be walked deterministically by internal row ID while skipping deleted rows.
- Extended `MessageRewardReplayService` with `replayAllActiveMessages(batchSize)`, which runs stored-message replay in batches and returns a concrete run summary instead of hand-waving about “bulk replay” with no implementation.
- Added focused tests proving the DAO cursor query skips deleted messages and proving the bulk replay path processes multiple stored messages across multiple batches.
- The remaining gap is no longer internal orchestration. It is the missing admin/API trigger surface that would let an operator actually invoke bulk replay safely.
