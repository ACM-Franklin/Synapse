# Current Task: Ingestion Completeness - Complete Review

## Goal

Close non-speculative live-ingestion gaps from the coverage matrix and leave
deferred Discord surfaces documented instead of ambiguous.

## Plan

- [x] Confirm exact JDA event syntax for generic channel create/delete/name events.
- [x] Add meaningful tests proving category rename is live.
- [x] Add meaningful tests proving thread create/delete/archive/lock are live.
- [x] Update the coverage matrix and roadmap to remove stale channel/thread ambiguity.
- [x] Run Maven, schema, diagnostics, and whitespace verification.

## Review

- Verified exact JDA channel event signatures before writing tests instead of guessing about handler coverage.
- Added SQLite-backed `ChannelEventHandlerTest` coverage proving live category rename plus thread create, rename, delete, archive, and lock persistence.
- Updated the coverage matrix and roadmap so Phase 3 reflects verified live behavior instead of stale ambiguity.
- Verified with full test suite pass, schema load, clean diagnostics on touched files, and `git diff --check`.
