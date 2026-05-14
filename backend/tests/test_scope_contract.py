from __future__ import annotations

from fastapi.routing import APIRoute

from app.main import create_app
from app.schemas.sync import SyncEntityCode
from tests.test_utils import StubDatabaseRuntime, make_settings


def test_backend_scope_exposes_only_desktop_sync_routes() -> None:
    app = create_app(
        settings_override=make_settings(),
        database_runtime_override=StubDatabaseRuntime(),
    )

    routes = {
        route.path
        for route in app.routes
        if isinstance(route, APIRoute)
    }

    assert "/auth/register" in routes
    assert "/auth/login" in routes
    assert "/auth/refresh" in routes
    assert "/auth/logout" in routes
    assert "/devices" in routes
    assert "/devices/{device_id}/revoke" in routes
    assert "/sync/bootstrap" in routes
    assert "/sync/pull" in routes
    assert "/sync/push" in routes
    assert "/health/live" in routes
    assert "/health/ready" in routes
    assert "/ops/metrics" in routes
    assert all(not path.startswith("/chat") for path in routes)
    assert all(not path.startswith("/ai") for path in routes)


def test_sync_entity_contract_matches_desktop_supported_scope() -> None:
    assert [entity.value for entity in SyncEntityCode] == [
        "TASK",
        "TASK_DEPENDENCY",
        "TIME_SESSION",
        "TASK_TEMPLATE",
        "GOAL",
        "GOAL_PROGRESS_ENTRY",
        "MOOD_ENTRY",
    ]
