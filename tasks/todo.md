# Current Task: Housekeeping And Live Backend Readiness

## Goal

Remove stale repo noise, verify the project structure is still sane, stop the
old Python prototype safely, and prepare the Java backend for a real live
Discord run before frontend integration.

## Plan

- [x] Inventory tracked and ignored docs for stale or duplicate guidance.
- [x] Review project directories for template leftovers and confusing artifacts.
- [x] Inspect Docker state and identify the old Python Synapse prototype.
- [x] Stop the old Python prototype without deleting fallback assets.
- [x] Verify Java backend live-run prerequisites and data-proof commands.
- [x] Apply safe cleanup edits and run validation.

## Review

- Old Python prototype containers stopped: `synapse-dashboard`, `synapse-api`, `synapse-bot`, `synapse-db`. Containers, images, and volumes were preserved for rollback.
- Java backend image now runs live against Discord with the explicit SQLite profile.
- Live proof result: historical scan job 1 completed with 64 checkpoints, 2,716 messages, 1,220 reaction rows, 50 members, 47 channels, zero duplicate messages, zero reaction mismatches, and zero reconciliation failures.
- Full test suite passes, `git diff --check` passes, and `GET /api/health` returns 200 from the running Java container.
