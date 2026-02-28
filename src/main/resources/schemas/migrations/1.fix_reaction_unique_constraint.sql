-- UNIQUE (message_id, emoji_name, emoji_ext_id) allowed duplicate rows for
-- standard emoji (emoji_ext_id IS NULL). Replace with an expression-based
-- unique index using COALESCE to match the DAO's ON CONFLICT clause.

-- Deduplicate any existing rows caused by the bug. Keep the row with the
-- highest count for each logical (message_id, emoji_name, coalesced ext_id).
DELETE FROM message_reactions
WHERE id NOT IN (
    SELECT MIN(id) FROM message_reactions
    GROUP BY message_id, emoji_name, COALESCE(emoji_ext_id, 0)
);

-- The old autoindex from the inline UNIQUE may still exist. SQLite auto-names
-- these sqlite_autoindex_<table>_<n>. We cannot DROP autoindexes directly, but
-- creating the new expression index is safe alongside it — the DAO's ON CONFLICT
-- will match the expression index, not the column-based autoindex.
CREATE UNIQUE INDEX IF NOT EXISTS message_reactions_uq
    ON message_reactions (message_id, emoji_name, COALESCE(emoji_ext_id, 0));
