# Current Task: Frontend Integration Prep

## Goal

Close the last small backend gaps before handing the implemented API contract
to frontend work.

## Plan

- [x] Add a deployed Quarkus happy-path auth test for session cookie to `CurrentUser` wiring.
- [x] Create `local_docs/FRONTEND_HANDOFF.md` with auth flow, endpoint examples, polling rules, and MVP boundaries.
- [x] Run focused and full validation.

## Review

- Keep this boring. No rule mutation, setup wizard, guest mode, or fake frontend-only API contracts.
- `local_docs/` is ignored by git; the handoff doc is a workspace handoff artifact unless force-added later.
- Focused endpoint auth test and full test suite pass.
