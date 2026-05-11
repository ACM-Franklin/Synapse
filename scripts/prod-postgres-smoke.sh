#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
image="${SYNAPSE_SMOKE_IMAGE:-synapse-backend:prod-smoke}"
network="${SYNAPSE_SMOKE_NETWORK:-synapse-prod-smoke}"
postgres_container="${SYNAPSE_SMOKE_POSTGRES_CONTAINER:-synapse-postgres-smoke}"
backend_container="${SYNAPSE_SMOKE_BACKEND_CONTAINER:-synapse-prod-smoke-backend}"
postgres_password="${SYNAPSE_SMOKE_DB_PASSWORD:-synapse_smoke_password}"

cleanup() {
    docker rm -f "$backend_container" "$postgres_container" >/dev/null 2>&1 || true
    docker network rm "$network" >/dev/null 2>&1 || true
}

wait_for_postgres() {
    for _ in $(seq 1 500); do
        if docker exec "$postgres_container" pg_isready -U synapse -d synapse >/dev/null 2>&1; then
            return 0
        fi
    done
    docker logs "$postgres_container" >&2 || true
    return 1
}

wait_for_backend() {
    docker run --rm --network "$network" curlimages/curl:8.10.1 \
        -sS --retry 45 --retry-delay 1 --retry-connrefused --retry-all-errors --max-time 5 \
        -w '\nHTTP_STATUS:%{http_code}\n' \
        http://"$backend_container":8080/api/health
}

trap cleanup EXIT

cd "$repo_root"

./mvnw -q -DskipTests package -Dquarkus.profile=prod

docker build -f src/main/docker/Dockerfile.jvm -t "$image" . >/dev/null

docker network rm "$network" >/dev/null 2>&1 || true
docker network create "$network" >/dev/null

docker rm -f "$postgres_container" "$backend_container" >/dev/null 2>&1 || true

docker run -d \
    --name "$postgres_container" \
    --network "$network" \
    -e POSTGRES_DB=synapse \
    -e POSTGRES_USER=synapse \
    -e POSTGRES_PASSWORD="$postgres_password" \
    postgres:16-alpine >/dev/null

wait_for_postgres

docker run -d \
    --name "$backend_container" \
    --network "$network" \
    -e QUARKUS_PROFILE=prod \
    -e SYNAPSE_DISCORD_BOT_ENABLED=false \
    -e SYNAPSE_DATASOURCE_AUTOMIGRATE=true \
    -e SYNAPSE_DISCORD_TOKEN=disabled-for-smoke \
    -e SYNAPSE_DISCORD_GUILD_ID=0 \
    -e SYNAPSE_DB_URL=jdbc:postgresql://"$postgres_container":5432/synapse \
    -e SYNAPSE_DB_USERNAME=synapse \
    -e SYNAPSE_DB_PASSWORD="$postgres_password" \
    "$image" >/dev/null

health_output="$(wait_for_backend)"
printf '%s\n' "$health_output"
if [[ "$health_output" != *'HTTP_STATUS:200'* ]]; then
    docker logs "$backend_container" >&2 || true
    exit 1
fi

table_count="$(docker exec "$postgres_container" psql -U synapse -d synapse -Atc \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('guild_metadata', 'events', 'messages', 'reward_ledger', 'historical_scan_jobs', 'reward_replay_jobs');")"
if [[ "$table_count" != "6" ]]; then
    docker exec "$postgres_container" psql -U synapse -d synapse -c "\dt" >&2 || true
    exit 1
fi

if docker logs "$backend_container" 2>&1 | grep -Eiq 'ERROR|Failed to start|Exception'; then
    docker logs "$backend_container" >&2 || true
    exit 1
fi

printf 'PostgreSQL prod smoke passed: health=200 required_tables=%s bot_enabled=false\n' "$table_count"
