# Current Task: Phase 7+8 Hardening Before Frontend

## Goal

Close the concrete backend risks found after the first API/auth slice so the
frontend is not built on obvious security and operations debt.

## Plan

- [x] Add OAuth state issuance, storage, callback validation, and single-use expiry.
- [x] Move bulk message replay behind persisted async jobs with a single-running-job guard and status endpoint.
- [x] Add leaderboard indexes through schema and migration.
- [x] Return a pending dashboard DTO for authenticated guild members not yet reconciled into `members`.
- [x] Validate rule `event_type` against the known event types Synapse can evaluate.
- [x] Lock down production Quarkus non-application surfaces and document the cookie deployment topology.
- [x] Add a small in-memory API rate limiter for OAuth, admin mutation, and ordinary API routes.
- [x] Update risk/docs/endpoint inventory and run focused plus full validation.

## Review

- Working from `local_docs/BACKEND_OPEN_RISKS.md`; Tier 1 items are security/ops blockers, not polish.
- Keep the slice narrow: no rule mutation, no guest mode, no new frontend UX, no deployment ceremony.
- Focused hardening tests, full test suite, diagnostics, and `git diff --check` pass.
- Phase is ready to preserve in git with the validated hardening slice.
