# Synapse

A community engagement engine for Discord. One instance, one guild.

Synapse records activity in your server, evaluates it against administrator-defined rules, and rewards members with XP and currency. The event lake is immutable. Rewards are derived and recalculable.

For a full description of what Synapse is and how it works, see [local_docs/WHAT_IS_SYNAPSE.md](local_docs/WHAT_IS_SYNAPSE.md).

---

## Stack

| Component       | Technology                      |
|-----------------|---------------------------------|
| Language        | Java 25                         |
| Framework       | Quarkus                         |
| Discord API     | JDA (Java Discord API) 6.3.1+   |
| Database (Dev)  | SQLite                          |
| Database (Prod) | PostgreSQL                      |
| Database Access | JDBI 3                          |
| Serialization   | Jackson                         |
| Frontend        | Svelte + Vite (separate repo)   |

---

## Prerequisites

- Java 25
- Maven 3.9+ (or use the included `mvnw` wrapper)
- A Discord bot token with `MESSAGE_CONTENT` intent enabled

---

## Configuration

Set these environment variables (or add them to a `.env` file in the project root).

Check readiness without printing secret values:

```shell
bash scripts/check-runtime-config.sh bot
bash scripts/check-runtime-config.sh frontend
bash scripts/check-runtime-config.sh prod
```

### Bot core

| Variable                          | Required | Default  | Description                                         |
|-----------------------------------|----------|----------|-----------------------------------------------------|
| `SYNAPSE_DISCORD_BOT_ENABLED`     | No       | `true`   | Disable JDA startup for DB/API smoke tests          |
| `SYNAPSE_DISCORD_TOKEN`           | Yes      | —        | Your Discord bot token                              |
| `SYNAPSE_DISCORD_GUILD_ID`        | Yes      | `0`      | The snowflake ID of the guild this instance manages |
| `SYNAPSE_DISCORD_SCAN_HISTORICAL` | No       | `false`  | Run historical channel scan on startup              |
| `SYNAPSE_DATASOURCE_AUTOMIGRATE`  | No       | `false`  | Whether to run the SQL migration manager on startup |
| `SYNAPSE_SQLITE_DB_PATH`          | No       | `target/synapse.sqlite` locally, `/data/synapse.sqlite` in the JVM image | SQLite database path for dev and local container runs |

### Frontend + OAuth (required for the HTTP API)

| Variable                          | Required for API | Default                                       | Description                                                          |
|-----------------------------------|------------------|-----------------------------------------------|----------------------------------------------------------------------|
| `SYNAPSE_DISCORD_CLIENT_ID`       | Yes              | —                                             | Discord application client ID for OAuth2                             |
| `SYNAPSE_DISCORD_CLIENT_SECRET`   | Yes              | —                                             | Discord application client secret                                    |
| `SYNAPSE_DISCORD_REDIRECT_URI`    | No               | `http://localhost:8080/api/auth/callback`     | OAuth2 redirect target (must match the Discord app config)           |
| `SYNAPSE_FRONTEND_REDIRECT_URI`   | No               | `http://localhost:5173/`                      | Where users land after a successful login                            |
| `SYNAPSE_FRONTEND_ORIGINS`        | No               | `http://localhost:5173`                       | Comma-separated CORS origins for the frontend                        |
| `SYNAPSE_ADMIN_ROLE_IDS`          | Yes for admin    | empty                                         | Comma-separated Discord role IDs that confer admin access            |
| `SYNAPSE_SESSION_TTL_SECONDS`     | No               | `86400`                                       | Session lifetime in seconds                                          |
| `SYNAPSE_SESSION_COOKIE_SECURE`   | No               | `false` (dev) / `true` (`%prod` profile)      | Mark the session cookie `Secure` (HTTPS-only)                        |
| `SYNAPSE_OAUTH_STATE_TTL_SECONDS` | No               | `300`                                         | OAuth state lifetime in seconds                                      |
| `SYNAPSE_OAUTH_STATE_MAX_ACTIVE`  | No               | `4096`                                        | Maximum active OAuth login states before oldest entries are evicted  |
| `SYNAPSE_API_RATE_LIMIT_ENABLED`  | No               | `true`                                        | Enable in-memory API rate limiting                                   |
| `SYNAPSE_API_RATE_LIMIT_WINDOW_SECONDS` | No          | `60`                                          | Rate-limit window length                                             |
| `SYNAPSE_API_RATE_LIMIT_DEFAULT_REQUESTS` | No        | `120`                                         | Requests per client/route/window for normal API routes               |
| `SYNAPSE_API_RATE_LIMIT_AUTH_REQUESTS` | No           | `20`                                          | Requests per client/route/window for OAuth routes                    |
| `SYNAPSE_API_RATE_LIMIT_ADMIN_MUTATION_REQUESTS` | No | `5`                                           | Requests per client/route/window for expensive admin mutations       |

### Production database

The `%prod` profile switches the datasource to PostgreSQL. Set these when
running with `-Dquarkus.profile=prod` or `QUARKUS_PROFILE=prod`:

| Variable               | Default                                          |
|------------------------|--------------------------------------------------|
| `SYNAPSE_DB_URL`       | `jdbc:postgresql://localhost:5432/synapse`       |
| `SYNAPSE_DB_USERNAME`  | `synapse`                                        |
| `SYNAPSE_DB_PASSWORD`  | empty                                            |

Dev mode continues to use SQLite at `target/synapse.sqlite` with no extra setup.

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

## Running The Backend Image

The maintained container path is the JVM image. The legacy/native template
Dockerfiles were removed because they were unused Quarkus scaffolding, not a
deployment strategy.

The image defaults to `QUARKUS_PROFILE=sqlite` for a self-contained local/live
proof run. Override it with `QUARKUS_PROFILE=prod` and PostgreSQL credentials
when deploying behind a real production database.

```shell
./mvnw package -Dquarkus.profile=sqlite
docker build -f src/main/docker/Dockerfile.jvm -t synapse-backend:jvm .
docker rm -f synapse-java-backend 2>/dev/null || true
docker run -d \
    --name synapse-java-backend \
    --env-file .env \
    -p 8080:8080 \
    -v synapse_java_data:/data \
    synapse-backend:jvm
```

Do not build the SQLite image with the default packaged profile and then try to
flip it at runtime. Quarkus fixes the datasource kind at build time. For a
PostgreSQL deployment, package with the production profile and run with
`QUARKUS_PROFILE=prod` plus `SYNAPSE_DB_URL`, `SYNAPSE_DB_USERNAME`, and
`SYNAPSE_DB_PASSWORD`.

For a live Discord proof pass, `.env` should include at least
`SYNAPSE_DISCORD_TOKEN`, `SYNAPSE_DISCORD_GUILD_ID`,
`SYNAPSE_DISCORD_SCAN_HISTORICAL=true`, and
`SYNAPSE_DATASOURCE_AUTOMIGRATE=true`.

To inspect the SQLite database produced by the container:

```shell
docker cp synapse-java-backend:/data/synapse.sqlite data/synapse.sqlite
bash scripts/data-proof-report.sh
```

## PostgreSQL Smoke Test

Before claiming production readiness, run the prod profile against PostgreSQL:

```shell
bash scripts/prod-postgres-smoke.sh
```

The smoke test starts temporary PostgreSQL and backend containers, disables JDA
with `SYNAPSE_DISCORD_BOT_ENABLED=false`, verifies `/api/health`, confirms core
schema tables exist, and then removes the temporary containers. This proves the
prod datasource path without opening another Discord gateway session.

---

## Project Structure

```
src/main/java/edu/franklin/acm/synapse/
├── activity/             # DAOs, migrations, and domain records (JDBI)
├── api/                  # JAX-RS resources, DTOs, auth, rate limiting, query services
├── bot/                  # SynapseBot — JDA bootstrap and lifecycle
├── rules/                # Rule engine, predicates, reward replay
└── scanners/             # Live ingestion, reconciliation, historical scanning

src/main/resources/
├── application.properties
└── schemas/
    ├── synapse.sql       # Single source of truth for fresh database schema
    └── migrations/       # Upgrade scripts for existing databases

scripts/
└── data-proof-report.sh  # Repeatable SQLite proof report for live/staging data
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

## HTTP API

The bot exposes an HTTP API on port 8080. The canonical inventory of routes lives in [local_docs/API_ENDPOINT_INDEX.md](local_docs/API_ENDPOINT_INDEX.md). The auth model is documented in [local_docs/AUTH.md](local_docs/AUTH.md).

- `GET /api/health` is the only public endpoint.
- Member-facing reads require an authenticated Discord guild member.
- Admin endpoints additionally require membership in one of the role IDs listed in `SYNAPSE_ADMIN_ROLE_IDS`.
- OAuth login uses a short-lived state cookie plus PKCE to protect the callback and token exchange.
- API routes have a small in-memory rate limiter; OAuth and expensive admin mutation routes are capped more tightly than ordinary reads.
- Destructive admin actions (`POST /api/scans/historical`, `POST /api/admin/replay/messages`) require an explicit `confirm` value in the JSON body.
- Message reward replay is queued as a persisted async job. Poll `GET /api/admin/replay/messages/{jobId}` for status.

Production assumes a same-host reverse proxy for the frontend and `/api/*`.
The `%prod` profile disables Quarkus CORS and enables trusted forwarded-header
handling so TLS-terminating proxies can preserve secure-cookie behavior. Do not
expose the Quarkus port directly to the internet with forwarded headers enabled.

## Rule Authoring Status

Rules are currently read-only through the HTTP API. The engine supports
composable rules made from a fixed predicate catalogue, AND logic across
predicates, cooldowns, and implemented currency outcomes. Arbitrary nested logic
trees, raw expression builders, rule mutation endpoints, audit logging for rule
changes, and simulation endpoints are not implemented yet.

The first frontend should display rules and validity reasons, not create or edit
rules. The local design contract lives in
[local_docs/RULE_AUTHORING_FRONTEND_CONTRACT.md](local_docs/RULE_AUTHORING_FRONTEND_CONTRACT.md).

---

## License

See [LICENSE](LICENSE).
