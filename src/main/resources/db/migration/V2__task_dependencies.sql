-- Normalized graph storage for task dependencies.
-- Edge semantic:
--   dependent_task_id depends on blocker_task_id.

CREATE TABLE task_dependencies (
    dependent_task_id TEXT NOT NULL,
    blocker_task_id TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    PRIMARY KEY (dependent_task_id, blocker_task_id),
    CHECK (dependent_task_id <> blocker_task_id),
    FOREIGN KEY (dependent_task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    FOREIGN KEY (blocker_task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE INDEX idx_task_dependencies_dependent
    ON task_dependencies(dependent_task_id);

CREATE INDEX idx_task_dependencies_blocker
    ON task_dependencies(blocker_task_id);
