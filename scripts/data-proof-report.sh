#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
default_db_path="$repo_root/data/synapse.sqlite"
if [[ ! -f "$default_db_path" && -f "$repo_root/target/synapse.sqlite" ]]; then
    default_db_path="$repo_root/target/synapse.sqlite"
fi

db_path="${1:-$default_db_path}"
stale_before_ts="${2:-$(date -u +"%Y-%m-%dT%H:%M:%S")}" 

require_sql_count() {
    local sql="$1"
    local expected="$2"
    local description="$3"
    local actual

    actual="$(sqlite3 "$db_path" "$sql")"
    if [[ "$actual" != "$expected" ]]; then
        printf 'Schema check failed: %s (expected %s, got %s)\n' "$description" "$expected" "$actual" >&2
        exit 1
    fi
}

if [[ ! -f "$db_path" ]]; then
    printf 'Database not found: %s\n' "$db_path" >&2
    exit 1
fi

if ! command -v sqlite3 >/dev/null 2>&1; then
    printf 'sqlite3 is required but was not found in PATH.\n' >&2
    exit 1
fi

require_sql_count "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='historical_scan_jobs';" "1" "historical_scan_jobs table"
require_sql_count "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='historical_scan_checkpoints';" "1" "historical_scan_checkpoints table"
require_sql_count "SELECT COUNT(*) FROM pragma_table_info('messages') WHERE name='is_deleted';" "1" "messages.is_deleted column"
require_sql_count "SELECT COUNT(*) FROM pragma_table_info('messages') WHERE name='deleted_at';" "1" "messages.deleted_at column"

printf 'Synapse Data Proof Report\n'
printf 'Database: %s\n' "$db_path"
printf 'Stale cutoff: %s\n' "$stale_before_ts"

printf '\n== Guild ==\n'
sqlite3 -header -column "$db_path" "
SELECT ext_id AS guild_ext_id,
       name,
       created_at,
       updated_at
FROM guild_metadata;
"

printf '\n== Migrations ==\n'
sqlite3 -header -column "$db_path" "
SELECT id,
       name,
       succeeded,
       occurred_at
FROM migrations
ORDER BY id;
"

printf '\n== Stored Volume ==\n'
sqlite3 -header -column "$db_path" "
SELECT 'events' AS table_name, COUNT(*) AS row_count FROM events
UNION ALL
SELECT 'messages', COUNT(*) FROM messages
UNION ALL
SELECT 'message_reactions', COUNT(*) FROM message_reactions
UNION ALL
SELECT 'threads', COUNT(*) FROM threads
UNION ALL
SELECT 'members', COUNT(*) FROM members
UNION ALL
SELECT 'channels', COUNT(*) FROM channels
UNION ALL
SELECT 'voice_sessions', COUNT(*) FROM voice_sessions
UNION ALL
SELECT 'historical_scan_jobs', COUNT(*) FROM historical_scan_jobs
UNION ALL
SELECT 'historical_scan_checkpoints', COUNT(*) FROM historical_scan_checkpoints;
"

printf '\n== Message Window ==\n'
sqlite3 -header -column "$db_path" "
SELECT MIN(created_at) AS first_message_at,
       MAX(created_at) AS last_message_at,
       COUNT(*) AS stored_messages
FROM messages;
"

printf '\n== Top Channels ==\n'
sqlite3 -header -column "$db_path" "
SELECT c.ext_id AS channel_ext_id,
       c.name,
       c.type,
       COUNT(m.id) AS message_rows
FROM channels c
JOIN events e ON e.channel_id = c.id
JOIN messages m ON m.event_id = e.id
GROUP BY c.id, c.ext_id, c.name, c.type
ORDER BY message_rows DESC, c.ext_id
LIMIT 5;
"

printf '\n== Top Members ==\n'
sqlite3 -header -column "$db_path" "
SELECT mem.ext_id AS member_ext_id,
       mem.name,
       mem.global_name,
       mem.nickname,
       COUNT(m.id) AS message_events
FROM members mem
JOIN events e ON e.member_id = mem.id
JOIN messages m ON m.event_id = e.id
GROUP BY mem.id, mem.ext_id, mem.name, mem.global_name, mem.nickname
ORDER BY message_events DESC, mem.ext_id
LIMIT 5;
"

printf '\n== Thread Coverage ==\n'
sqlite3 -header -column "$db_path" "
SELECT t.ext_id AS thread_ext_id,
       t.name,
       t.message_count,
       COUNT(m.id) AS stored_message_rows,
       COUNT(m.id) - t.message_count AS row_gap
FROM threads t
LEFT JOIN messages m ON m.thread_id = t.id
GROUP BY t.id, t.ext_id, t.name, t.message_count
ORDER BY t.ext_id;
"

printf '\n== Reconciliation Counts ==\n'
sqlite3 -header -column "$db_path" "
SELECT 'missing_message_rows' AS check_name,
       COUNT(*) AS row_count
FROM (
    SELECT 1
    FROM events e
    LEFT JOIN messages m ON m.event_id = e.id
    WHERE e.event_type = 'MESSAGE_CREATE'
      AND m.id IS NULL
)
UNION ALL
SELECT 'orphan_or_invalid_message_rows',
       COUNT(*)
FROM (
    SELECT 1
    FROM messages m
    LEFT JOIN events e ON e.id = m.event_id
    WHERE e.id IS NULL
       OR e.event_type != 'MESSAGE_CREATE'
)
UNION ALL
SELECT 'orphan_reaction_rows',
       COUNT(*)
FROM (
    SELECT 1
    FROM message_reactions r
    LEFT JOIN messages m ON m.id = r.message_id
    WHERE m.id IS NULL
)
UNION ALL
SELECT 'orphan_thread_parent_rows',
       COUNT(*)
FROM (
    SELECT 1
    FROM threads t
    LEFT JOIN channels c ON c.id = t.channel_id
    WHERE c.id IS NULL
)
UNION ALL
SELECT 'thread_row_gap_gt_one',
       COUNT(*)
FROM (
    SELECT 1
    FROM threads t
    LEFT JOIN messages m ON m.thread_id = t.id
    GROUP BY t.id
    HAVING ABS(COUNT(m.id) - t.message_count) > 1
)
UNION ALL
SELECT 'stale_open_voice_sessions',
       COUNT(*)
FROM voice_sessions
WHERE left_at IS NULL
  AND joined_at < '$stale_before_ts'
UNION ALL
SELECT 'deleted_messages', COUNT(*)
FROM messages
WHERE is_deleted = 1
UNION ALL
SELECT 'inactive_channels', COUNT(*)
FROM channels
WHERE is_active = 0
UNION ALL
SELECT 'inactive_threads', COUNT(*)
FROM threads
WHERE is_active = 0
UNION ALL
SELECT 'inactive_members', COUNT(*)
FROM members
WHERE is_active = 0
UNION ALL
SELECT 'inactive_roles', COUNT(*)
FROM roles
WHERE is_active = 0;
"

printf '\n== Duplicate And Reaction Integrity ==\n'
sqlite3 -header -column "$db_path" "
SELECT 'duplicate_message_ext_ids' AS check_name,
       COUNT(*) AS row_count
FROM (
    SELECT ext_id
    FROM messages
    GROUP BY ext_id
    HAVING COUNT(*) > 1
)
UNION ALL
SELECT 'reaction_aggregate_mismatches',
       COUNT(*)
FROM (
    SELECT 1
    FROM messages m
    LEFT JOIN message_reactions r ON r.message_id = m.id
    GROUP BY m.id, m.reaction_count
    HAVING m.reaction_count != COALESCE(SUM(r.count), 0)
);
"

printf '\n== Thread Row Gaps Beyond Expected Starter Message ==\n'
sqlite3 -header -column "$db_path" "
SELECT t.ext_id AS thread_ext_id,
       t.name,
       t.message_count,
       COUNT(m.id) AS stored_message_rows,
       COUNT(m.id) - t.message_count AS row_gap,
       t.is_archived,
       t.is_locked,
       t.is_active
FROM threads t
LEFT JOIN messages m ON m.thread_id = t.id
GROUP BY t.id, t.ext_id, t.name, t.message_count, t.is_archived, t.is_locked, t.is_active
HAVING ABS(COUNT(m.id) - t.message_count) > 1
ORDER BY ABS(COUNT(m.id) - t.message_count) DESC, t.ext_id;
"