CREATE TABLE sync_state (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL DEFAULT '',
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE sync_outbox (
    id TEXT PRIMARY KEY,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL,
    payload_json TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT NOT NULL DEFAULT '',
    last_attempt_at TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    UNIQUE(entity_type, entity_id)
);

CREATE INDEX idx_sync_outbox_status_created
    ON sync_outbox(status, created_at);

CREATE TABLE account_link (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    user_id TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL DEFAULT '',
    display_name TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'SIGNED_OUT',
    linked_at TEXT NOT NULL DEFAULT '',
    last_authenticated_at TEXT NOT NULL DEFAULT ''
);

CREATE TABLE device_identity (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    device_id TEXT NOT NULL DEFAULT '',
    device_label TEXT NOT NULL DEFAULT '',
    platform TEXT NOT NULL DEFAULT '',
    app_version TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL DEFAULT '',
    updated_at TEXT NOT NULL DEFAULT ''
);
