# Rules Table — Design Direction

## Status: Deferred — Needs More Design Consideration

This document captures the agreed-upon direction for the rules/predicate system.
Implementation is deferred until the normalized event schema and scanning infrastructure
are solid.

---

## What Is A Rule?

A rule is a named collection of **boolean predicates** evaluated against event data.
When all predicates in a rule evaluate to `true`, the rule fires and produces a
consequence (reward, achievement, announcement, etc.).

## Agreed Principles

1. **Rules evaluate normalized SQL columns, not JSON blobs.**
   The entire point of the `message_events` / `message_attachments` normalization is
   to make predicate evaluation fast, indexable, and composable.

2. **Rules must be toggleable.**
   An administrator can enable or disable any rule without redeployment.

3. **Historical vs. live scope.**
   When a rule is created or updated, the administrator chooses whether it applies to:
   - Only events going forward (live evaluation)
   - All historical events retroactively
   - Both

   This prevents accidental mass-reward on backfill and gives granular control.

4. **Event type targeting.**
   A rule specifies which event types it evaluates against (e.g., `MESSAGE_CREATE`,
   `MEMBER_ROLE_CHANGE`, `MEMBER_JOIN`). Not every rule is about messages.

5. **No duplicate rewards from edits.**
   Because `message_events` upserts on edit (not append), an edited message is
   re-evaluated against current rules. The reward system must track "has this
   message already been rewarded by this rule?" to prevent double-dipping.

## Implementation Strategy — Option A (Agreed)

**Java holds the predicate logic. The rules table holds metadata and toggles.**

```sql
CREATE TABLE rules (
    id              INTEGER PRIMARY KEY,
    name            VARCHAR NOT NULL UNIQUE,
    description     TEXT,
    event_type      VARCHAR NOT NULL,          -- which event type this rule targets
    enabled         INTEGER NOT NULL DEFAULT 1,
    applies_historic INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Each rule row corresponds to a Java class or method that implements the actual
predicate chain. The rule's `name` is the lookup key that maps to the Java
evaluator.

## Future Evolution (Not Now)

- **Option B**: Add threshold columns (`min_content_length`, `requires_attachment`,
  `min_reaction_count`) so simple rules can be configured entirely from the admin
  dashboard without code changes.

- **Option C**: A DSL or expression language stored in the database, evaluated at
  runtime. Full rule engine territory. Only if Option B proves insufficient.

## Example Predicate Chains (What Rules Look Like In Java)

```java
// "Meaningful contribution" — real content, not a bot, not TTS
boolean fires = !authorIsBot
    && contentLength > 80
    && !isTts
    && type == 0;  // DEFAULT

// "Community helper" — a reply with substance
boolean fires = isReply
    && !authorIsBot
    && contentLength > 40
    && mentionUserCount <= 2;

// "Engagement magnet" — message that got organic reactions
boolean fires = reactionCount >= 10
    && contentLength > 20
    && !authorIsBot
    && mentionUserCount == 0;

// "Achievement: promoted to moderator"
boolean fires = eventType.equals("MEMBER_ROLE_CHANGE")
    && newRoles.contains(MODERATOR_ROLE_ID)
    && !oldRoles.contains(MODERATOR_ROLE_ID);
```
