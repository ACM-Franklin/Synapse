# Synapse Frontend

This folder now contains the runnable Vite/Svelte frontend plus the preserved
static wireframe pack.

## Development

```bash
npm install
npm run dev
```

The app defaults to same-origin API calls. For split-origin local development
against Quarkus on port 8080, set:

```bash
VITE_SYNAPSE_API_BASE=http://localhost:8080 npm run dev
```

All API calls include credentials because Synapse auth uses a server-side
session cookie.

## Structure

- `src/` contains the Svelte app.
- `../assets/images/` is served as the Vite public asset directory for shared frontend imagery.
- `wireframes.html` links to the preserved static design reference.
- `build-now/README.md` documents ideas that fit the current Synapse MVP and backend contract.
- `future/README.md` documents ideas worth keeping after more backend work exists.
- `screens/` contains the trimmed wireframes that still fit the product direction.

For the current login screen polish, place this file in `../assets/images/`:

- `synapse-logo.png`

The login screen loads it from `/images/synapse-logo.png` at runtime.

## Guardrails

Synapse is:

- one Discord guild
- authenticated by Discord OAuth
- transparency-first around rewards and ingestion
- split between member and admin experiences
- backed by a real event lake, not fake SaaS theater

Synapse is not:

- a public dashboard
- a guest product
- a setup wizard
- a marketplace app
- a raw rule-DSL playground
- a multi-guild control panel

Ideas that violated those guardrails were either deleted outright or moved into the future document only if they still made sense later.
