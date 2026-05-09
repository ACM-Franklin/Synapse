# Current Task: Reward Ledger Foundation

## Goal

Add the first backend foundation for attributable reward accounting so future
message edits, message deletes, replay, leveling, and reward traces do not rely
on blind direct balance increments.

## Plan

- [x] Inspect the current reward evaluation and currency dispatch flow.
- [x] Add a reward ledger table to the schema.
- [x] Add Java record and JDBI DAO support for ledger entries.
- [x] Wire currency outcomes through the ledger before updating member balances.
- [x] Keep behavior compatible with the current rule engine.
- [x] Run Maven verification.

## Review

- Added `reward_ledger` to the schema for attributable currency effects.
- Added `RewardLedgerEntry` and `RewardLedgerDao`.
- Produced `RewardLedgerDao` through `DaoProducer`.
- Wired `RuleEngine` so currency outcomes insert ledger entries before member
  balances are updated.
- Wrapped rule evaluation insert, ledger insert, and balance update in one JDBI
  transaction.
- Verified with `./mvnw test`.
- Verified schema loads with `sqlite3 :memory: < src/main/resources/schemas/synapse.sql`.
