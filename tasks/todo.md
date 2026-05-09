# Current Task: Ingestion Completeness

## Goal

Close non-speculative live-ingestion gaps from the coverage matrix and leave
deferred Discord surfaces documented instead of ambiguous.

## Plan

- [x] Read exact JDA event syntax for reaction bulk-removal events.
- [x] Add DAO support for clearing reaction counts by message and emoji.
- [x] Wire live reaction remove-all and remove-emoji handlers.
- [x] Add focused SQLite-backed tests for the new mutation behavior.
- [x] Update ingestion coverage documentation and roadmap review notes.
- [x] Run Maven, schema, diagnostics, and whitespace verification.

## Review

- Confirmed JDA exposes `onMessageReactionRemoveAll(MessageReactionRemoveAllEvent)` and `onMessageReactionRemoveEmoji(MessageReactionRemoveEmojiEvent)` through `ListenerAdapter`.
- Added live remove-all handling that clears all stored reaction rows for the message and sets the aggregate message reaction count to zero.
- Added live remove-emoji handling that deletes only the removed emoji row and subtracts its stored count from the aggregate count with a zero floor.
- Added focused SQLite-backed handler coverage for remove-all and remove-emoji snapshot behavior.
- Verified with `./mvnw test`, SQLite schema load, diagnostics, and `git diff --check`.
