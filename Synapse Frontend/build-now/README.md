# Build Now

These ideas fit the current Synapse MVP, the backend contract in `local_docs/FRONTEND_HANDOFF.md`, and the actual product direction.

## Core Screens Worth Building

### Login and Auth Entry
Use `screens/01-login.html`.

Keep:
- the centered OAuth entry
- the data-forward split variant that proves the instance is alive
- a simple access-denied state for non-members or failed auth

Do not build:
- a guild picker
- a first-run onboarding wizard
- anything that implies the frontend configures the bot instance

### Admin Dashboard
Use `screens/02-admin-dashboard.html`.

Keep:
- dense status-first admin overview
- live ingestion health
- recent rule activity / event activity
- quick navigation to event lake, members, leaderboard, and scans

Preferred direction:
- start from the dense overview shell
- borrow the live-feed emphasis where it helps
- do not over-invest in command-palette gimmicks first

### Member Dashboard
Use `screens/03-member-dashboard.html`.

Keep:
- level / currency / leaderboard positioning
- recent rewards ledger
- strong explainability language around rewards

Build carefully:
- the reward trace overlay is on-brand, but the backend still needs richer trace data before the full experience is real
- do not fake deep predicate traces if the API cannot supply them yet

### Event Lake Browser
Use `screens/05-event-lake.html`.

Keep:
- live tail
- forensic search
- aggregates
- taxonomy as read-only reference

Constraint:
- taxonomy should help admins understand ingested data, not pretend they can author rules from the frontend yet

### Leaderboard
Use `screens/06-leaderboard.html`.

Keep:
- authenticated leaderboard views only
- season framing where it improves comprehension
- member-facing rank context

Do not build:
- public leaderboard routes
- unauthenticated ranking surfaces

### Members and Profiles
Use `screens/07-members-profile.html`.

Keep:
- admin directory
- admin member detail
- member profile
- reward-history emphasis

Constraint:
- treat manual adjustments and other write actions as future until real endpoints and audit flows exist

### Channels and Historical Scan
Use `screens/08-channels-scan.html`.

Keep:
- channel inventory
- historical scan scope picker
- progress/logging views
- explicit ingest versus replay language

This is strongly on-vision because it exposes how Synapse proves coverage instead of hiding the machinery.

## Not Build-Now

These are not part of the current build even if they still fit long-term:

- rule mutation UI
- broad settings management UI
- public access controls
- achievements or marketplace surfaces

## Missing Design Gap

The current pack no longer includes a rules editor because that was mostly fantasy for the current backend. When the time comes, the next real screen to add is a read-only rules catalog that matches `GET /api/rules` before any mutation UI exists.
