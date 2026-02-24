# Synapse

A community engagement engine for Discord. One instance, one guild.

Synapse records activity in your server, evaluates it against administrator-defined rules, and rewards members with XP and currency. The event lake is immutable. Rewards are derived and recalculable.

For a full description of what Synapse is and how it works, see [docs/WHAT_IS_SYNAPSE.md](docs/WHAT_IS_SYNAPSE.md).

---

## Stack

| Component       | Technology                      |
|-----------------|---------------------------------|
| Language        | Java 21+                        |
| Framework       | Quarkus                         |
| Discord API     | JDA (Java Discord API) 6.3.1+   |
| Database (Dev)  | SQLite                          |
| Database (Prod) | PostgreSQL                      |
| Database Access | JDBI 3                          |
| Serialization   | Jackson                         |
| Frontend        | Svelte + Vite (separate repo)   |

---

## Prerequisites

- Java 21 or later
- Maven 3.9+ (or use the included `mvnw` wrapper)
- A Discord bot token with `MESSAGE_CONTENT` intent enabled

---

## Configuration

Set these environment variables (or add them to a `.env` file in the project root):

| Variable                 | Required | Default   | Description                                      |
|--------------------------|----------|-----------|--------------------------------------------------|
| `DISCORD_TOKEN`          | Yes      | —         | Your Discord bot token                           |
| `GUILD_ID`               | Yes      | `0`       | The snowflake ID of the guild this instance manages |
| `HISTORICAL_SCAN_ENABLED`| No       | `false`   | Run historical channel scan on startup           |

---

## Running in Dev Mode

```shell
./mvnw quarkus:dev
```

Quarkus dev mode enables live coding — save a file and the app recompiles automatically.

Dev UI is available at [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/).

---

## Building

```shell
# Standard JAR
./mvnw package

# Über-JAR (single fat jar)
./mvnw package -Dquarkus.package.jar.type=uber-jar

# Run the packaged application
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Project Structure

```
src/main/java/edu/franklin/acm/synapse/
├── bot/                  # SynapseBot — JDA bootstrap and lifecycle
├── activity/             # DAOs and domain records (JDBI)
├── historical/           # GuildHistoricalScanner — backfill from message history
└── scanner/              # GuildLiveScanner — real-time event ingestion

src/main/resources/
├── application.properties
└── schemas/
    └── synapse.sql       # Single source of truth for the database schema
```

---

## Architecture

### Event Lake (Immutable)

All Discord activity is ingested into an append-only event table. Events are structured JSON payloads capturing the full context of each interaction. Events are never modified or deleted.

### Scanners

- **GuildHistoricalScanner** — Paginates through channel history from oldest to newest. Resumable via watermark checkpoints.
- **GuildLiveScanner** — Listens to the JDA gateway and persists events as they arrive.

### Derived Data (Future)

Currency balances, levels, achievements, and leaderboards are all derived from the event lake by the rule engine. If rules change, derived data can be recalculated from the immutable event history.

---

## License

See [LICENSE](LICENSE).
