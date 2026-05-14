-- Persisted image generation jobs for resume/retry across sessions.

CREATE TABLE image_job_state (
    job_id TEXT PRIMARY KEY,
    conversation_id TEXT,
    request_id TEXT NOT NULL DEFAULT '',
    requested_model TEXT NOT NULL DEFAULT '',
    active_model TEXT NOT NULL DEFAULT '',
    prompt TEXT NOT NULL DEFAULT '',
    prompt_hash TEXT NOT NULL DEFAULT '',
    size TEXT NOT NULL DEFAULT '',
    aspect_ratio TEXT NOT NULL DEFAULT '',
    resolution TEXT NOT NULL DEFAULT '',
    stage TEXT NOT NULL DEFAULT 'QUEUED',
    attempt INTEGER NOT NULL DEFAULT 1,
    user_retry_count INTEGER NOT NULL DEFAULT 0,
    remote_url TEXT NOT NULL DEFAULT '',
    saved_path TEXT NOT NULL DEFAULT '',
    last_message TEXT NOT NULL DEFAULT '',
    last_error TEXT NOT NULL DEFAULT '',
    pause_requested INTEGER NOT NULL DEFAULT 0,
    cancel_requested INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
);

CREATE INDEX idx_image_job_state_conversation_updated
    ON image_job_state(conversation_id, updated_at DESC);

CREATE INDEX idx_image_job_state_stage_updated
    ON image_job_state(stage, updated_at DESC);
