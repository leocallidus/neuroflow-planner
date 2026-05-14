ALTER TABLE tasks ADD COLUMN updated_at TEXT NOT NULL DEFAULT '';
ALTER TABLE tasks ADD COLUMN deleted_at TEXT NOT NULL DEFAULT '';
ALTER TABLE tasks ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY';
ALTER TABLE tasks ADD COLUMN last_synced_at TEXT NOT NULL DEFAULT '';
ALTER TABLE tasks ADD COLUMN server_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tasks ADD COLUMN last_modified_by_device TEXT NOT NULL DEFAULT '';
UPDATE tasks
SET updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE updated_at = '';
CREATE INDEX idx_tasks_sync_status ON tasks(sync_status);
CREATE INDEX idx_tasks_deleted_at ON tasks(deleted_at);

ALTER TABLE task_dependencies ADD COLUMN updated_at TEXT NOT NULL DEFAULT '';
ALTER TABLE task_dependencies ADD COLUMN deleted_at TEXT NOT NULL DEFAULT '';
ALTER TABLE task_dependencies ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY';
ALTER TABLE task_dependencies ADD COLUMN last_synced_at TEXT NOT NULL DEFAULT '';
ALTER TABLE task_dependencies ADD COLUMN server_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE task_dependencies ADD COLUMN last_modified_by_device TEXT NOT NULL DEFAULT '';
UPDATE task_dependencies
SET updated_at = COALESCE(NULLIF(created_at, ''), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
WHERE updated_at = '';
CREATE INDEX idx_task_dependencies_sync_status ON task_dependencies(sync_status);
CREATE INDEX idx_task_dependencies_deleted_at ON task_dependencies(deleted_at);

ALTER TABLE time_sessions ADD COLUMN updated_at TEXT NOT NULL DEFAULT '';
ALTER TABLE time_sessions ADD COLUMN deleted_at TEXT NOT NULL DEFAULT '';
ALTER TABLE time_sessions ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY';
ALTER TABLE time_sessions ADD COLUMN last_synced_at TEXT NOT NULL DEFAULT '';
ALTER TABLE time_sessions ADD COLUMN server_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE time_sessions ADD COLUMN last_modified_by_device TEXT NOT NULL DEFAULT '';
UPDATE time_sessions
SET updated_at = COALESCE(NULLIF(started_at, ''), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
WHERE updated_at = '';
CREATE INDEX idx_time_sessions_sync_status ON time_sessions(sync_status);
CREATE INDEX idx_time_sessions_deleted_at ON time_sessions(deleted_at);

ALTER TABLE task_templates ADD COLUMN updated_at TEXT NOT NULL DEFAULT '';
ALTER TABLE task_templates ADD COLUMN deleted_at TEXT NOT NULL DEFAULT '';
ALTER TABLE task_templates ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY';
ALTER TABLE task_templates ADD COLUMN last_synced_at TEXT NOT NULL DEFAULT '';
ALTER TABLE task_templates ADD COLUMN server_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE task_templates ADD COLUMN last_modified_by_device TEXT NOT NULL DEFAULT '';
UPDATE task_templates
SET updated_at = strftime('%Y-%m-%dT%H:%M:%fZ', 'now')
WHERE updated_at = '';
CREATE INDEX idx_task_templates_sync_status ON task_templates(sync_status);
CREATE INDEX idx_task_templates_deleted_at ON task_templates(deleted_at);

ALTER TABLE mood_entries ADD COLUMN updated_at TEXT NOT NULL DEFAULT '';
ALTER TABLE mood_entries ADD COLUMN deleted_at TEXT NOT NULL DEFAULT '';
ALTER TABLE mood_entries ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY';
ALTER TABLE mood_entries ADD COLUMN last_synced_at TEXT NOT NULL DEFAULT '';
ALTER TABLE mood_entries ADD COLUMN server_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE mood_entries ADD COLUMN last_modified_by_device TEXT NOT NULL DEFAULT '';
UPDATE mood_entries
SET updated_at = COALESCE(NULLIF(timestamp, ''), strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
WHERE updated_at = '';
CREATE INDEX idx_mood_entries_sync_status ON mood_entries(sync_status);
CREATE INDEX idx_mood_entries_deleted_at ON mood_entries(deleted_at);

ALTER TABLE goals ADD COLUMN deleted_at TEXT NOT NULL DEFAULT '';
ALTER TABLE goals ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY';
ALTER TABLE goals ADD COLUMN last_synced_at TEXT NOT NULL DEFAULT '';
ALTER TABLE goals ADD COLUMN server_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE goals ADD COLUMN last_modified_by_device TEXT NOT NULL DEFAULT '';
CREATE INDEX idx_goals_sync_status ON goals(sync_status);
CREATE INDEX idx_goals_deleted_at ON goals(deleted_at);
