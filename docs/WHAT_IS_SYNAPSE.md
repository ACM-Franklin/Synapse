# What Is Synapse?

Synapse is a community engagement engine for a single Discord server. It records activity, evaluates it against administrator-defined rules, and rewards members with currency and experience. One instance of Synapse manages one guild. If you run multiple servers, you deploy multiple instances.

---

## Scope

Synapse was conceived around a simple idea: reward the people who actually participate in your community. Not with generic "post a message, get a point" mechanics, but with a configurable rule engine that lets administrators decide what matters, what gets rewarded, and how much.

Each deployment of Synapse is bound to exactly one Discord guild. The database, the event lake, the rule engine, and the web dashboard all operate in the context of that single guild. There is no multi-guild aggregation, no cross-server leaderboards, and no shared user identity across instances. This is a deliberate design choice that keeps the data model simple, the queries fast, and the deployment isolated.

If someone wants Synapse for two servers, they deploy two instances. That is not a limitation — it is a feature. Data isolation for free. No cross-contamination. No permission nightmares.

---

## How It Works

### The Event Lake

When Synapse connects to your guild, it can ingest activity in two ways:

1. **Historical Scanning** — On demand, Synapse walks through the message history of selected channels (or all channels) starting from the oldest message and working forward chronologically. This backfills the event lake with everything that happened before Synapse was installed, or fills gaps from periods of downtime.

2. **Live Scanning** — While running, Synapse listens to the Discord gateway and records events as they happen in real time.

Both scanners write to the same immutable event lake. Events are structured JSON records containing all the data JDA provides: message content, author, channel, reactions, attachments, mentions, timestamps, and more. Once written, events are never modified or deleted.

The event lake is the single source of truth. Everything else — currency balances, levels, leaderboards, achievements — is derived from it and can be recalculated at any time.

### The Rule Engine

The rule engine is how administrators define what participation means in their community. Rules are composable conditions that match against events and produce outcomes: XP awards, currency grants, achievement triggers, or role assignments.

Rules are versioned. When an administrator changes the rules, Synapse can optionally replay historical events against the new ruleset to recalculate rewards. Administrators choose whether rule changes apply retroactively, from a specific date, from this moment forward, or on a scheduled future date.

The rule engine does not produce side effects. It receives structured input and returns a result. All writes, announcements, and role changes happen in a separate service layer. This makes the engine safe to run in simulation mode against real data.

### Historical Scanning: Administrator Control

The historical scanner is not an automatic process that runs without permission. Administrators control:

- **Whether it runs at startup** or only on manual trigger.
- **Which channels to scan** — include or exclude specific channels or entire categories.
- **What time period to cover** — scan everything, scan from a specific date, or scan only gaps where the bot experienced downtime.
- **Whether rewards are calculated** — scan can populate the event lake without triggering the reward engine, allowing administrators to define their rules first and apply them later.

This is critical because administrators may want to set up their rule engine before running a historical scan, so that rewards are calculated correctly from the start. Alternatively, they may want to ingest all the raw data first and apply rules later.

---

## Features

### Engagement Economy

Synapse tracks two currencies: a primary currency (XP by default) and a secondary currency (Gold by default). Administrators can rename both. Primary currency drives leveling. Secondary currency drives marketplace purchases. Both are derived from the event lake through the rule engine.

### Achievements

Achievements are composable units combining a behavior strategy (score threshold, streak, peer reactions, etc.) with a visual identity (rarity tier, seasonal frame, custom artwork). The achievement system is wired into the rule engine — triggering an achievement is just another outcome type a rule can produce.

### Marketplace

The marketplace lets administrators configure cosmetic items that members can purchase with earned currency: unique roles, color changes, titles, and similar. Purchases are atomic at the database level. No double-spending, no over-allocation.

### Member Dashboard

Each authenticated member has a profile showing their stats, rank, achievements, and inventory. On any rewarded event, members can view a full trace explaining which rules matched, what calculations were applied, and what they received.

### Admin Dashboard

Administrators have a central interface to manage rules, achievements, marketplace items, and media. A taxonomy browser shows every event type observed and allows building rules directly from any event type. An observability screen shows reward rates, anomaly flags, and system health.

### Public Leaderboard

A public leaderboard is accessible without authentication, showing ranks and scores only. Usernames and avatars are not visible to unauthenticated visitors. Members who leave the server are automatically anonymized.

---

## Deployment Model

Synapse is self-hosted. One instance, one guild, one database, one domain. The administrator deploys Synapse, points it at their Discord bot token, and the system binds to that guild for its lifetime.

The stack:
- **Language:** Java 21+
- **Framework:** Quarkus
- **Discord API:** JDA (Java Discord API)
- **Database:** SQLite (development) / PostgreSQL (production)
- **Database Access:** JDBI 3
- **Frontend:** Svelte + Vite (separate repository)

---

## Use in Organizations

Synapse is well-suited to structured organizations that operate on Discord: student clubs, networks of clubs, and leadership bodies that need defensible documentation of community activity.

### Demonstrating Active Membership

Funding bodies, student government offices, and inter-club councils often require proof of active membership. Synapse produces a persistent, queryable record of actual participation: who contributed, how often, across which channels, and over what time period. A club applying for funding can report genuine participation numbers backed by structured data rather than a screenshot of a member count.

### Leadership and Accountability

The event lake gives leadership a way to look back at any period and understand what happened. Rules can reward contribution to specific channels, so officers active in decision-making spaces accumulate a traceable participation record. Useful for internal recognition and formal accountability at institutions that track officer involvement.

Because Synapse is self-hosted, the organization owns all of its data. No third-party platform to request records from, no privacy policy changes, no subscription fees.

---

## What Synapse Is Not

Synapse is not a moderation tool. It does not issue warnings, bans, or timeouts.

It is not a subscription service. You run it on your own infrastructure and you own all of the data.

It is not a multi-guild management platform. Each instance serves one community. Period.

It is not designed to manufacture engagement through compulsive mechanics. The intent is to measure and reflect participation that your community produces naturally, and to reward the kinds of contribution you have decided matter.

---

## Summary

Synapse records activity in your Discord server, evaluates it against a configurable rule engine, and produces a persistent record of who participated, how often, and what they earned. Members have a transparent view of their own stats and reward history. Administrators have full control over what gets scanned, what gets rewarded, and when changes take effect. One instance, one guild, no compromises.
