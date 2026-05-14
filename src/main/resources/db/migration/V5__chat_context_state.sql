-- Persisted chat context state per conversation:
-- mode + summary snapshot + pinned facts.

CREATE TABLE chat_context_state (
    conversation_id TEXT PRIMARY KEY,
    preferred_mode TEXT NOT NULL DEFAULT 'AUTO',
    summary TEXT NOT NULL DEFAULT '',
    summary_covered_messages INTEGER NOT NULL DEFAULT 0,
    pinned_facts TEXT NOT NULL DEFAULT '',
    updated_at TEXT NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_context_state_updated_at ON chat_context_state(updated_at);
