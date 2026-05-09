# Ingestion Validation Plan

## Purpose

Before building out the frontend-backed backend surface, Synapse needs a high
confidence proof that live ingestion and historical scanning are capturing the
right Discord data, updating mutable state correctly, and leaving the database
in a trustworthy state.

This document defines what "good enough" means for phase one.

---

## Phase-One Goal

The immediate goal is not theoretical perfection. The goal is to reach a level
of confidence where manual inspection plus targeted validation show that:

- live ingestion is working correctly
- historical scanning is working correctly
- mutable Discord state is reconciled correctly
- the event lake and child tables contain the data needed for downstream use
- rules and future frontend behavior can rely on stable identifiers instead of
  brittle names or magic constants

If Synapse cannot prove that, building the API and frontend slice on top of it
would be reckless.

---

## What Must Be Proven

### 1. Live Ingestion Correctness

For supported event types, the system must show that new Discord activity is
recorded in the expected parent and child tables with the expected identifiers,
timestamps, and derived detail.

### 2. Historical Scan Correctness

Historical scanning must demonstrate that it can backfill intended scope and
that repeated scans do not silently corrupt data or create garbage duplicates.

### 3. Mutable State Handling

The system must correctly handle Discord data that can change over time,
including at least:

- member profile details
- role membership
- channel names
- category names
- thread metadata
- message edits where current-state storage is intended
- message deletion behavior and its downstream reward consequences
- member active or inactive state
- voice session open and close state

### 4. Identifier Discipline

Any entity that matters across renames should be bound and referenced by stable
Discord external IDs or internal database IDs derived from them.

Names are useful for display. They are not a trustworthy linking strategy.

### 5. Rule and Configuration Compatibility

The backend must eventually detect and flag rules or configuration that no
longer match the known structure of the current guild state.

Examples:

- a rule references a role that no longer exists
- a configured channel is no longer monitored or no longer exists
- a future settings record references invalid role IDs

Renames should not break correctly ID-bound configuration. Deletion or missing
references should be surfaced clearly.

Current decision:

- if a referenced role or channel disappears, affected rules should be flagged as
  invalid and removed from the evaluation run set until repaired

---

## Mutable-State Semantics Locked So Far

### Message Edits

- Message edits are treated as current-state updates.
- Synapse should overwrite the current message snapshot rather than create a
  parallel permanent duplicate of the same message state.
- Message edits should be eligible to trigger re-evaluation.
- Re-evaluation should be deterministic against the same message subject rather
  than treated as a fresh reward opportunity.
- Re-evaluation must not allow double-dipping or easy abuse through repeated
  micro-edits.
- Anti-abuse handling should not depend primarily on vague "significant change"
  heuristics.

The policy direction is clear: updated content may matter, duplicated reward
extraction must not.

### Message Deletes

- A deleted message should no longer be treated as valid reward-bearing content.
- Users should not be able to farm rewards by posting, earning, deleting, and
  reposting the same or similar content.
- Deletion handling should follow tombstone-plus-reversal semantics for
  content-derived rewards.
- Immediate reversal is the current direction for content-derived rewards tied
  to deleted messages.

This is a meaningful design point because the current backend increments member
balances directly; safe deletion semantics will require explicit handling rather
than wishful thinking.

### Reactions

- Reaction changes do not need a rich dedicated live event model for v1.
- The important behavior is whether content has reactions, not a high-frequency
  stream of reaction churn.
- Reaction handling should stay simple and avoid overengineering unless real
  abuse or missing data proves otherwise.
- If reaction-sensitive rules exist before live reaction hooks do, they should
  be treated as snapshot-based behavior rather than fully real-time triggers.

### Channel and Thread Mutations in Scope

The currently agreed mutable channel and thread behaviors that matter are:

- creation
- deletion
- rename
- movement between categories or parents

Anything beyond that should be justified by actual product need, not added for
the thrill of complexity.

---

## Current Reality and Known Gaps

The current backend already has:

- live ingestion wiring
- historical scanning wiring
- JDA-driven reconciliation behavior
- event lake schema and child tables
- ID-based storage for most guild entities

The current backend does not yet appear to have a full operational proof layer
for all of this.

Important known gaps include:

- no persistent scan checkpoint or watermark tables yet
- no persisted scan job model yet
- no frontend-facing or admin-facing ingestion audit surface yet
- no rule or configuration validation layer against current guild structure yet
- no complete automated proof suite for Discord-driven ingest behavior
- message update, message delete, reaction add, and reaction remove hooks now
  exist, but edit reward re-evaluation is intentionally deferred until reward
  deltas can be computed safely
- reaction remove-all and reaction remove-emoji events are not currently handled
- broader thread-update events beyond the limited thread/channel updates already
  wired still need review

That is not fatal. It just means phase one must be deliberate.

---

## Proof Standard

The proof standard for phase one is pragmatic.

We are not trying to mathematically prove Discord ingestion. We are trying to
build enough confidence that a careful human can compare Discord reality against
database state and conclude the system is behaving correctly.

This means using:

- targeted manual test scenarios in Discord
- repeated scans where useful
- direct database inspection
- focused query checks
- code review of mutation paths
- logging and operational status improvements where needed

---

## Manual Validation Matrix

Manual validation should cover at least these scenarios.

### Message and Content

- simple message create
- message with mentions
- message with attachment
- message with embed-producing link
- message edit
- thread message
- forum post message
- reaction changes if currently captured only as message-state snapshots

### Member and Role State

- member joins
- member leaves
- member rejoins
- nickname or profile change
- role added
- role removed
- role rename

### Channel and Thread State

- channel create
- channel rename
- category rename
- thread create
- thread archive or unarchive where relevant
- thread lock state change where relevant

### Voice State

- voice join
- voice move
- voice leave
- startup reconciliation with existing voice presence

### Historical Scan

- initial full scan over selected scope
- repeat scan over already-covered scope
- targeted gap recovery scenario
- confirmation that monitored scope is respected

For each scenario, database state should be compared against actual Discord
reality using SQLite inspection and, where needed, direct Discord observation.

---

## Questions Phase One Must Answer

For each validated scenario, we should be able to answer:

1. Did the expected parent event row get created?
2. Did the expected child-table rows get created or updated?
3. Did mutable state update the intended current-state table rather than create
   bad duplicates?
4. Are timestamps sensible?
5. Are stable IDs preserved even when names change?
6. Would a rule or frontend feature have enough data to reason about this event
   correctly?
7. If the object was later edited, moved, or deleted, does Synapse respond in a
  way that matches the intended product semantics?

If the answer to any of those is "not sure," phase one is not done.

---

## Required Backend Improvements for Phase One

Some work will likely be required before confidence is high enough.

### 1. Better Ingestion Auditability

We likely need clearer operational visibility for:

- last successful startup
- last successful reconciliation
- current live connection status
- whether a historical scan is running
- scan summary or completion state

### 2. Durable Historical Scan State

To properly support gap recovery and admin-triggered scans, the backend will
need durable scan policy and checkpoint state instead of relying on in-memory
watermark maps alone.

### 3. Structure Validation

We need a way to detect stale references in rules and future configuration.

Renames should be harmless when ID-bound. Missing or deleted targets should be
flagged.

Validation should run at startup, after reconciliation, and after admin saves.

### 4. Current-State vs Event-History Clarity

For each mutable Discord entity, we should be explicit about whether Synapse is
storing:

- immutable historical events
- current-state snapshots
- both

If that line stays fuzzy, validation turns into guesswork.

### 5. Reward Accounting Fit

Currency awards now have an initial ledger foundation: fired currency outcomes
are recorded in `reward_ledger` before member balances are updated.

That is only the foundation. Edit deltas, deletion reversals, and future replay
semantics still need explicit implementation on top of the ledger.

---

## Frontend Contract Implications

When the frontend is later wired up, it should not rely on display names as
authoritative identifiers.

The frontend should:

- consume stable IDs from the backend
- treat names as display values only
- avoid hardcoding guild structure, role IDs, channel IDs, or rule catalog data
- rely on backend-provided links and references wherever possible

Anything else is brittle nonsense.

---

## Rule Authoring Implication

There is likely a future need for a friendlier rule-authoring layer or a
higher-level AST-like expression system.

That work is not phase one.

Phase one only needs to ensure that the underlying data and references are good
enough that a richer rule authoring experience can later be built on solid
ground.

---

## Deployment Concerns After Phase One

Once data confidence is established and the frontend-backed slice is built,
deployment should aim for simplicity.

Current preference:

- easy local deployment
- easy cloud deployment
- ideally a single Docker image or otherwise minimal deployment surface
- viable domain access for the application
- clear documentation for hosting and runtime configuration

Possible cloud targets include AWS or Azure depending on available credits.

---

## Exit Criteria for Phase One

Phase one is complete when all of the following are true:

- live ingestion has been manually validated across key supported scenarios
- historical scanning has been manually validated across key supported scenarios
- mutable-state reconciliation behavior has been manually validated
- obvious stale-reference and ID-binding risks are understood and either fixed
  or explicitly documented
- the operator can inspect the database and confidently map stored state back to
  Discord reality
- confidence is high enough to proceed with the frontend-backed backend slice

At that point, the second phase can begin without building on sand.