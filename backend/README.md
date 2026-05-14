# NeuroFlow Cloud Backend

Cloud sync backend for the NeuroFlow Planner JavaFX desktop application. It exposes only auth, device management, sync, and health/metrics endpoints required by the desktop client.

## Requirements

- `uv`
- `Python 3.12+`
- `Docker` or `podman` with compose support for local PostgreSQL

## Quick start

```bash
cd backend
cp .env.dev.example .env
docker compose up -d postgres
uv sync --group dev
uv run alembic upgrade head
uv run neuroflow-sync-api
```

Default local API URL: `http://127.0.0.1:8000`

Main endpoints:

- `GET /health/live`
- `GET /health/ready`
- `GET /ops/metrics`
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /devices`
- `POST /devices/{device_id}/revoke`
- `POST /sync/bootstrap`
- `POST /sync/pull`
- `POST /sync/push`
- `GET /docs`

Supported client model:

- one primary client: `JavaFX` desktop
- local-first runtime in desktop `SQLite`
- backend used only for account linking, device management, bootstrap/pull/push sync, and ops visibility

Not part of the supported backend surface:

- server-side AI runtime
- chat API
- file/asset sync for `notes/`, `images/`, or `chat_uploads/`

## Development workflow

Install and lock dependencies:

```bash
uv lock
uv sync --group dev
```

Run tests:

```bash
uv run pytest
```

Run the API:

```bash
uv run neuroflow-sync-api
```

Run migrations:

```bash
uv run alembic upgrade head
uv run alembic current
```

## Desktop Pairing

Typical local dev flow for desktop cloud sync:

```bash
# terminal 1
cd backend
cp .env.dev.example .env
docker compose up -d postgres
uv sync --group dev
uv run alembic upgrade head
uv run neuroflow-sync-api

# terminal 2
cd ..
./mvnw javafx:run
```

Point the JavaFX client to the backend with the cloud sync base URL:

```properties
cloud.sync.enabled=true
cloud.sync.baseUrl=http://127.0.0.1:8000
```

Expected manual smoke path:

1. Start backend and desktop app.
2. Open cloud sync settings in the JavaFX client.
3. Register or login.
4. Run `Sync now`.
5. Verify device list / revoke flow and bootstrap/pull/push behavior.

## Configuration

Settings are loaded from environment variables with prefix `NEUROFLOW_` and nested delimiter `__`.

Examples:

- `NEUROFLOW_API__PORT=8000`
- `NEUROFLOW_SECURITY__JWT_SECRET=dev-only-change-me-secret-at-least-32b`
- `NEUROFLOW_SECURITY__REFRESH_TOKEN_PEPPER=dev-only-refresh-pepper-at-least-32b`
- `NEUROFLOW_DATABASE__URL=postgresql+asyncpg://neuroflow:neuroflow@127.0.0.1:5433/neuroflow_sync`
- `NEUROFLOW_LOGGING__JSON_LOGS=true`
- `NEUROFLOW_API__METRICS_ENABLED=true`

The app loads `.env` from the `backend/` directory by default.

## Observability

- All API errors use a machine-readable envelope:
  - `error.status`
  - `error.code`
  - `error.message`
  - `error.details`
  - `error.category`
  - `error.retryable`
  - `error.request_id`
- Request middleware emits audit-oriented events for:
  - auth actions
  - device actions
  - sync bootstrap, pull and push
- `GET /ops/metrics` exposes in-memory counters and distributions for:
  - HTTP requests
  - sync latency
  - sync push payload size
  - auth failures
  - sync conflicts

## Schema scope

The supported runtime schema is limited to desktop cloud sync:

- identity and session tables
- planning entities included in sync wave 1
- sync journals, cursors, tombstones, and push receipts

Alembic history still contains legacy chat revisions from the cancelled mobile/server-AI branch. They are preserved as historical migrations only and are not part of the active backend runtime contract.

## Layout

```text
backend/
  alembic/
  app/
    api/
    core/
    db/
    repositories/
    schemas/
    services/
    workers/
  tests/
```
