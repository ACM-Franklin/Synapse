# Current Task: Pre-Frontend Backend Risk Burn-Down

## Goal

Address backend readiness concerns that do not require user-provided Discord
OAuth secrets or manual Discord application changes before frontend integration.

## Plan

- [x] Add non-secret runtime config validation for bot/frontend/prod readiness.
- [x] Add a bot-disabled runtime mode for DB/API smoke tests.
- [x] Prove the PostgreSQL production profile with a temporary local container.
- [x] Document rule-authoring constraints so frontend design does not invent unsupported behavior.
- [x] Run tests, script checks, and commit tracked changes.

## Review

- `scripts/check-runtime-config.sh bot` passes against current `.env`.
- `scripts/check-runtime-config.sh frontend` correctly reports missing OAuth client ID, client secret, and admin role IDs without printing secret values.
- `scripts/check-runtime-config.sh prod` correctly reports missing production DB values without printing secret values.
- `scripts/prod-postgres-smoke.sh` passed: temporary PostgreSQL plus prod-profile backend reached `/api/health` with JDA disabled and schema tables present.
- Rule authoring remains read-only for the first frontend; the builder contract is documented locally and requires backend mutation/catalogue/simulation work before editable rules are safe.
- Full test suite passes and `git diff --check` is clean.
