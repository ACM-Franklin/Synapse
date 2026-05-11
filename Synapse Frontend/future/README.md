# Future

These ideas are still worth remembering, but they are not honest build targets until more backend work exists.

## Worth Keeping For Later

### Read-Only Rules Catalog, Then Typed Rule Builder
Future direction, not current UI.

Backend prerequisites:
- stable read-only rules reference screen first
- `POST /api/rules`
- `PUT /api/rules/{ruleId}`
- enable/disable mutation support
- audit logging for admin changes
- backend-owned rule catalog metadata
- simulation endpoint against stored data

When that work exists:
- start with a constrained form builder
- a sentence-builder may be acceptable later
- keep everything typed and validated

Do not resurrect:
- raw DSL editing as a primary experience
- node-graph rule editing
- any fake save button before mutation endpoints exist

### Seasons Management
The seasons ideas in the old leaderboard pack were directionally good.

Worth revisiting after:
- season APIs exist
- historical season data is exposed cleanly
- leaderboard and profile endpoints can speak in season terms without hardcoded mock structure

### Settings and Audit Surfaces
The settings screen had some ideas worth keeping later:
- role/auth mapping visibility
- currency naming and policy
- audit log browsing
- destructive admin actions with real confirmation flows

Not before:
- real settings endpoints exist
- audit APIs exist
- operator actions are safely backed by the backend

### Richer Member Analytics
Heatmaps, by-rule breakdowns, and deeper personal stats can fit later.

Only after:
- query surfaces exist for those numbers
- we can explain the data honestly without stuffing placeholders everywhere

### Achievements
This is only worth revisiting if the backend actually grows beyond the current stub outcomes.

If it happens:
- keep it restrained
- tie it to explainable event and rule data
- do not let it turn the product into a cheap dopamine casino

## Explicitly Cut

These were removed because they do not fit the product direction now and should not quietly creep back in:

- public leaderboard and public profile ideas
- guest or unauthenticated product surfaces
- first-run guild connect wizard flows
- multi-guild selection or ownership flows
- marketplace and inventory concepts
- raw rule DSL and node-graph editing
