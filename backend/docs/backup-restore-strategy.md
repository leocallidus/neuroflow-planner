# PostgreSQL Backup and Restore Strategy

Stage 10 baseline for the NeuroFlow cloud sync backend.

## Scope

- Primary datastore: PostgreSQL
- Critical data classes:
  - identity and auth state
  - device/session state
  - sync change journal
  - wave 1 planning data
- Excluded from active scope:
  - chat runtime/API data
  - server-side AI runtime state
  - file/object storage sync for desktop assets

## RPO / RTO target

- Target `RPO`: up to 15 minutes for the first production rollout
- Target `RTO`: up to 60 minutes for the first production rollout

These are pragmatic beta targets, not a final production SLO.

## Baseline strategy

- Daily full logical backup with `pg_dump --format=custom`
- WAL archiving or provider-level point-in-time recovery where available
- Retention:
  - daily backups for 14 days
  - weekly backups for 8 weeks
- Backup artifacts stored outside the database host
- Restore drill at least once per release wave that changes schema or sync logic

## Commands

Full logical backup:

```bash
pg_dump \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file neuroflow_sync_$(date +%F_%H%M%S).dump \
  "$DATABASE_URL"
```

Restore into an empty target database:

```bash
createdb neuroflow_sync_restore
pg_restore \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  --dbname=neuroflow_sync_restore \
  neuroflow_sync_2026-03-23_010000.dump
```

## Restore procedure

1. Stop API writers or switch the backend into maintenance mode.
2. Restore the selected backup into a fresh PostgreSQL database.
3. Run schema validation and application smoke checks.
4. Point the backend to the restored database.
5. Verify:
   - auth works
   - `GET /health/ready` is green
   - `sync bootstrap/pull/push` work for a test account

## Post-restore validation

- row-count spot checks on:
  - `users`
  - `devices`
  - `refresh_sessions`
  - `sync_change_log`
  - `tasks`
  - `goals`
- confirm latest `alembic_version`
- confirm newest `sync_change_log.id` is readable by the API
- do not treat legacy chat tables as restore acceptance criteria for the active backend scope

## Operational notes

- Never treat local SQLite as a server backup source.
- Logical backups must be versioned together with the active Alembic head.
- For managed PostgreSQL, prefer native PITR if the provider offers it.
