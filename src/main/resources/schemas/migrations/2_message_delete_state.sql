-- Add message delete-state columns for existing databases that predate
-- message mutation support.

ALTER TABLE messages ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0;
ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS messages_is_deleted_idx ON messages (is_deleted);