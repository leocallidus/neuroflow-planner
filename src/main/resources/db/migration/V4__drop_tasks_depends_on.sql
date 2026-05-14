-- Stage-7 cleanup: remove legacy tasks.depends_on column.
-- Dependency source of truth is task_dependencies table.

ALTER TABLE tasks DROP COLUMN depends_on;
