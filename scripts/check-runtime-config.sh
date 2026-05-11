#!/usr/bin/env bash

set -euo pipefail

mode="${1:-all}"
env_file="${2:-.env}"
missing=0

read_env_file_value() {
    local name="$1"
    [[ -f "$env_file" ]] || return 1
    awk -F= -v key="$name" '
        /^[[:space:]]*#/ { next }
        /^[[:space:]]*$/ { next }
        {
            candidate=$1
            gsub(/^[[:space:]]+|[[:space:]]+$/, "", candidate)
            if (candidate == key) {
                value=substr($0, index($0, "=") + 1)
                gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
                gsub(/^"|"$/, "", value)
                gsub(/^'"'"'|'"'"'$/, "", value)
                print value
                exit 0
            }
        }
    ' "$env_file"
}

value_for() {
    local name="$1"
    local value="${!name-}"
    if [[ -n "$value" ]]; then
        printf '%s' "$value"
        return 0
    fi
    read_env_file_value "$name" || true
}

require_present() {
    local name="$1"
    shift || true
    local value
    value="$(value_for "$name")"
    if [[ -z "$value" ]]; then
        printf 'MISSING %s\n' "$name"
        missing=1
        return
    fi
    for invalid in "$@"; do
        if [[ "$value" == "$invalid" ]]; then
            printf 'INVALID %s\n' "$name"
            missing=1
            return
        fi
    done
    printf 'OK %s\n' "$name"
}

check_bot() {
    printf '== bot ==\n'
    require_present SYNAPSE_DISCORD_TOKEN replace-me
    require_present SYNAPSE_DISCORD_GUILD_ID 0
}

check_frontend() {
    printf '== frontend ==\n'
    require_present SYNAPSE_DISCORD_CLIENT_ID
    require_present SYNAPSE_DISCORD_CLIENT_SECRET
    require_present SYNAPSE_ADMIN_ROLE_IDS
}

check_prod() {
    printf '== prod ==\n'
    require_present SYNAPSE_DB_URL
    require_present SYNAPSE_DB_USERNAME
    require_present SYNAPSE_DB_PASSWORD
}

case "$mode" in
    bot)
        check_bot
        ;;
    frontend)
        check_frontend
        ;;
    prod)
        check_prod
        ;;
    all)
        check_bot
        check_frontend
        check_prod
        ;;
    *)
        printf 'Usage: %s [bot|frontend|prod|all] [env_file]\n' "$0" >&2
        exit 2
        ;;
esac

if [[ "$missing" -ne 0 ]]; then
    printf 'Runtime config check failed for mode: %s\n' "$mode" >&2
    exit 1
fi

printf 'Runtime config check passed for mode: %s\n' "$mode"
