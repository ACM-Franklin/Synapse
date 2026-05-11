# Current Task: Vite/Svelte Frontend MVP Shell

## Goal

Create a real Vite + Svelte frontend in `Synapse Frontend/` that renders the
implemented MVP API contract without inventing unsupported product surfaces.

## Product Boundary

- Auth is Discord OAuth through backend-owned `/api/auth/*` endpoints.
- Member UI can show current user, guild summary, personal dashboard, recent
	reward rows, and authenticated leaderboard data.
- Admin UI can show system status, read-only rules, historical scan operation
	state, and message reward replay operation state.
- Event lake search, member directory APIs, channel inventory APIs, settings,
	setup, seasons, achievements, marketplace, and rule mutation stay out until
	backend endpoints exist.

## Plan

- [x] Preserve the static wireframe pack as reference material.
- [x] Add Vite/Svelte/TypeScript project files in `Synapse Frontend/`.
- [x] Add a typed API client matching the Java DTO records and frontend handoff.
- [x] Build authenticated shell, login gate, member dashboard, leaderboard,
	admin status, read-only rules, scan, and replay views.
- [x] Add honest loading, empty, error, pending-member, and access-denied states.
- [x] Install dependencies and run the frontend build.

## Review

- Added a runnable Vite/Svelte/TypeScript app under `Synapse Frontend/`.
- Root `index.html` now mounts the Svelte app; `wireframes.html` preserves access
	to the static reference pack.
- API client uses `credentials: include`, optional `VITE_SYNAPSE_API_BASE`, and
	DTO shapes matching the Java records.
- UI is limited to implemented endpoints: auth, guild summary, member dashboard,
	leaderboard, system status, read-only rules, historical scan, and replay.
- No rule mutation, setup wizard, public leaderboard, achievements, seasons, or
	marketplace UI was added.
- `npm install`, `npm run check`, `npm run build`, and `git diff --check` pass.
