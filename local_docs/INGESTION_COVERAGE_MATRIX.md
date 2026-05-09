# Ingestion Coverage Matrix

## Purpose

This matrix tracks what Synapse currently captures from Discord and where each
event surface is handled.

Statuses:

- **Live**: handled from JDA gateway events
- **Historical**: handled by the historical scanner
- **Reconciliation**: handled by startup reconciliation
- **Partial**: stored in some form, but not complete enough to call done
- **Deferred**: intentionally not implemented yet
- **Missing**: needed or likely needed, but not implemented

---

## Message and Content Events

| Discord surface | Current status | Notes |
| --- | --- | --- |
| Message create | Live, Historical | Persists parent event, message snapshot, attachments, and reaction snapshot. Fires rule evaluation for live create events. |
| Message edit | Partial | Live handler updates the current message snapshot, attachments, and reaction snapshot. Reward re-evaluation is deferred until delta accounting by stable message subject exists. |
| Message delete | Live | Live handler tombstones the message and reverses unreversed currency awards tied to the original message event. |
| Message reaction add | Live, Historical snapshot | Live handler increments per-emoji count and message reaction count. Historical scan stores snapshot counts from retrieved messages. |
| Message reaction remove | Live | Live handler decrements per-emoji count and message reaction count, floored at zero. |
| Reaction remove all / remove emoji | Missing | Not currently handled. Decide whether this matters before adding more event plumbing. |
| Poll vote add/remove | Deferred | Poll fields are snapshotted on messages, but poll vote events are not part of the current reward model. |

---

## Member and Role Events

| Discord surface | Current status | Notes |
| --- | --- | --- |
| Member join | Live | Upserts full member profile and fires member-join rule context. |
| Member leave | Live | Marks member inactive and closes open voice sessions. |
| Member update | Live | Updates member profile and detects role changes. |
| Role add/remove on member | Live via member update | Role deltas are detected against stored member role snapshot. |
| Role rename | Reconciliation | Role names are upserted during reconciliation. Dedicated live role update handling is not yet present. |
| Role delete | Partial | Reconciliation can mark missing structure indirectly only if explicit inactive handling exists for the relevant DAO. Rule/config invalidation is still future work. |

---

## Channel, Category, and Thread Events

| Discord surface | Current status | Notes |
| --- | --- | --- |
| Channel create | Live, Reconciliation | Upserts channel/category state. |
| Channel delete | Live | Marks channel inactive where DAO support exists. |
| Channel rename | Live, Reconciliation | ID-bound upsert handles name changes. |
| Channel parent/category move | Live, Reconciliation | Updates channel/category relationship. |
| Category rename | Reconciliation | Dedicated category rename live handling is not currently explicit. |
| Thread create | Historical, Reconciliation | Thread state is discovered by scans and reconciliation. Dedicated live thread-create handling is not explicit. |
| Thread archive/lock | Live, Reconciliation | Limited thread mutable state is updated through channel update events. |
| Thread delete | Partial | Needs explicit verification against JDA behavior and DAO inactive handling. |

---

## Voice Events

| Discord surface | Current status | Notes |
| --- | --- | --- |
| Voice join | Live, Reconciliation | Opens voice session. |
| Voice leave | Live, Reconciliation | Closes voice session and records duration. |
| Voice move | Live | Closes old session and opens new one. |
| Startup orphan voice sessions | Reconciliation | Reconciliation closes stale sessions and reopens current ones. |

---

## Historical Scan

| Surface | Current status | Notes |
| --- | --- | --- |
| Text/news channel history | Historical | Scans message history with caller-provided watermarks. |
| Active threads | Historical | Scans active thread cache. |
| Archived threads | Historical | Scans archived thread containers. |
| Forum posts | Historical | Scans active and archived forum posts, including tags. |
| Durable checkpoints | Missing | Watermark input exists, but persisted checkpoint tables do not. |
| Persisted scan jobs | Missing | No durable scan job status model yet. |

---

## Rule and Reward Surfaces

| Surface | Current status | Notes |
| --- | --- | --- |
| Currency award ledger | Implemented foundation | Currency awards are recorded in `reward_ledger` before member balances are updated. |
| Message delete reversal | Live | Delete handling inserts reversal ledger rows and subtracts awarded currency. |
| Message edit delta evaluation | Deferred | Needs subject-based result comparison; current edit handler updates snapshot only. |
| Rule/config stale reference validation | Missing | Needed before frontend rule editing is safe. |
| Replay | Deferred | Ledger foundation supports future replay, but replay is not implemented. |

---

## Current High-Priority Gaps

1. Durable historical scan checkpoints.
2. Persisted scan jobs and scan status.
3. Rule/config reference validation.
4. Message edit reward delta model.
5. Manual validation runbook with exact Discord actions and SQL checks.
6. Decision on reaction remove-all and reaction remove-emoji handling.