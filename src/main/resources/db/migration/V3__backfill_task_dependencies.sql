-- Backfill normalized dependency edges from legacy tasks.depends_on CSV.
--
-- Parsing rules:
-- - tokens are comma-separated;
-- - surrounding spaces are trimmed;
-- - empty tokens are skipped and logged;
-- - non-existing blocker ids are skipped and logged;
-- - self-loops are skipped and logged;
-- - duplicate valid edges are deduplicated (logged once per duplicated pair).
--
-- Diagnostic/rollback helpers after this migration:
-- 1) Inspect imported edges:
--      SELECT * FROM task_dependencies ORDER BY dependent_task_id, blocker_task_id;
-- 2) Inspect skipped edges:
--      SELECT * FROM task_dependency_backfill_log ORDER BY id;
-- 3) Rollback imported edges only (keeps schema):
--      DELETE FROM task_dependencies;
--      DELETE FROM task_dependency_backfill_log;

CREATE TABLE task_dependency_backfill_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    dependent_task_id TEXT NOT NULL,
    blocker_task_id TEXT,
    reason TEXT NOT NULL,
    logged_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE INDEX idx_task_dependency_backfill_log_dep
    ON task_dependency_backfill_log(dependent_task_id);

INSERT INTO task_dependency_backfill_log (dependent_task_id, blocker_task_id, reason)
WITH RECURSIVE split(dependent_task_id, remaining, blocker_task_id) AS (
    SELECT
        id,
        TRIM(COALESCE(depends_on, '')),
        NULL
    FROM tasks
    UNION ALL
    SELECT
        dependent_task_id,
        CASE
            WHEN instr(remaining, ',') > 0 THEN LTRIM(substr(remaining, instr(remaining, ',') + 1))
            ELSE ''
        END,
        TRIM(
            CASE
                WHEN instr(remaining, ',') > 0 THEN substr(remaining, 1, instr(remaining, ',') - 1)
                ELSE remaining
            END
        )
    FROM split
    WHERE remaining <> ''
),
tokens AS (
    SELECT
        dependent_task_id,
        COALESCE(blocker_task_id, '') AS blocker_task_id
    FROM split
    WHERE blocker_task_id IS NOT NULL
),
classified AS (
    SELECT
        tokens.dependent_task_id,
        tokens.blocker_task_id,
        CASE
            WHEN tokens.blocker_task_id = '' THEN 'empty_token'
            WHEN tokens.blocker_task_id = tokens.dependent_task_id THEN 'self_loop'
            WHEN blocker.id IS NULL THEN 'missing_blocker'
            ELSE 'valid'
        END AS status
    FROM tokens
    LEFT JOIN tasks blocker ON blocker.id = tokens.blocker_task_id
)
SELECT
    dependent_task_id,
    blocker_task_id,
    status
FROM classified
WHERE status <> 'valid';

INSERT INTO task_dependency_backfill_log (dependent_task_id, blocker_task_id, reason)
WITH RECURSIVE split(dependent_task_id, remaining, blocker_task_id) AS (
    SELECT
        id,
        TRIM(COALESCE(depends_on, '')),
        NULL
    FROM tasks
    UNION ALL
    SELECT
        dependent_task_id,
        CASE
            WHEN instr(remaining, ',') > 0 THEN LTRIM(substr(remaining, instr(remaining, ',') + 1))
            ELSE ''
        END,
        TRIM(
            CASE
                WHEN instr(remaining, ',') > 0 THEN substr(remaining, 1, instr(remaining, ',') - 1)
                ELSE remaining
            END
        )
    FROM split
    WHERE remaining <> ''
),
tokens AS (
    SELECT
        dependent_task_id,
        COALESCE(blocker_task_id, '') AS blocker_task_id
    FROM split
    WHERE blocker_task_id IS NOT NULL
),
valid_tokens AS (
    SELECT
        tokens.dependent_task_id,
        tokens.blocker_task_id
    FROM tokens
    JOIN tasks blocker ON blocker.id = tokens.blocker_task_id
    WHERE tokens.blocker_task_id <> ''
      AND tokens.blocker_task_id <> tokens.dependent_task_id
),
duplicates AS (
    SELECT
        dependent_task_id,
        blocker_task_id,
        COUNT(*) AS duplicate_count
    FROM valid_tokens
    GROUP BY dependent_task_id, blocker_task_id
    HAVING COUNT(*) > 1
)
SELECT
    dependent_task_id,
    blocker_task_id,
    'duplicate_edge(count=' || duplicate_count || ')'
FROM duplicates;

INSERT OR IGNORE INTO task_dependencies (dependent_task_id, blocker_task_id)
WITH RECURSIVE split(dependent_task_id, remaining, blocker_task_id) AS (
    SELECT
        id,
        TRIM(COALESCE(depends_on, '')),
        NULL
    FROM tasks
    UNION ALL
    SELECT
        dependent_task_id,
        CASE
            WHEN instr(remaining, ',') > 0 THEN LTRIM(substr(remaining, instr(remaining, ',') + 1))
            ELSE ''
        END,
        TRIM(
            CASE
                WHEN instr(remaining, ',') > 0 THEN substr(remaining, 1, instr(remaining, ',') - 1)
                ELSE remaining
            END
        )
    FROM split
    WHERE remaining <> ''
),
tokens AS (
    SELECT
        dependent_task_id,
        COALESCE(blocker_task_id, '') AS blocker_task_id
    FROM split
    WHERE blocker_task_id IS NOT NULL
)
SELECT DISTINCT
    tokens.dependent_task_id,
    tokens.blocker_task_id
FROM tokens
JOIN tasks blocker ON blocker.id = tokens.blocker_task_id
WHERE tokens.blocker_task_id <> ''
  AND tokens.blocker_task_id <> tokens.dependent_task_id;
