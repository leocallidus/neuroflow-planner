from __future__ import annotations

import uuid

from app.api.deps import get_auth_service, get_current_auth_context, get_sync_service
from app.api.error_handlers import ApiError
from app.schemas.auth import TokenBundleResponse
from app.schemas.sync import SyncPushResponse
from app.services.auth_service import AuthenticatedRequestContext
from tests.test_utils import build_test_app, make_api_client


class FailingAuthService:
    async def login_user(self, *_args, **_kwargs) -> TokenBundleResponse:
        raise ApiError(
            status_code=401,
            code="invalid_credentials",
            message="Invalid email or password.",
            category="auth",
            retryable=False,
        )


class StubSyncService:
    async def push(
        self,
        _auth: AuthenticatedRequestContext,
        _request: object,
    ) -> SyncPushResponse:
        return SyncPushResponse(
            accepted=[],
            remote_since_change_id=0,
            remote_next_change_id=0,
            latest_change_id=0,
            has_more_remote_changes=False,
            remote_changes=[],
        )


def make_auth_context() -> AuthenticatedRequestContext:
    return AuthenticatedRequestContext(
        user_id=uuid.uuid4(),
        device_id=uuid.uuid4(),
        refresh_session_id=uuid.uuid4(),
    )


async def test_metrics_endpoint_tracks_auth_failures() -> None:
    app = build_test_app()
    app.dependency_overrides[get_auth_service] = FailingAuthService

    async with make_api_client(app) as client:
        response = await client.post(
            "/auth/login",
            json={
                "email": "user@example.com",
                "password": "wrong-password",
                "device": {
                    "device_label": "Leo Desktop",
                    "platform": "linux",
                    "app_version": "0.1.0",
                },
            },
        )
        assert response.status_code == 401

        metrics_response = await client.get("/ops/metrics")

    assert metrics_response.status_code == 200
    payload = metrics_response.json()
    counters = payload["metrics"]["counters"]
    assert any(
        item["name"] == "auth_failures_total"
        and item["labels"]["operation"] == "login"
        and item["labels"]["status_code"] == "401"
        and item["value"] == 1
        for item in counters
    )


async def test_metrics_endpoint_tracks_sync_latency_and_push_payload_size() -> None:
    app = build_test_app()
    app.dependency_overrides[get_current_auth_context] = make_auth_context
    app.dependency_overrides[get_sync_service] = StubSyncService

    async with make_api_client(app) as client:
        response = await client.post(
            "/sync/push",
            json={
                "since_change_id": 0,
                "pull_limit": 50,
                "changes": [
                    {
                        "client_change_id": str(uuid.uuid4()),
                        "entity_type": "TASK",
                        "operation": "UPSERT",
                        "entity_id": str(uuid.uuid4()),
                        "payload": {"id": str(uuid.uuid4()), "title": "Task"},
                    }
                ],
            },
        )
        assert response.status_code == 200

        metrics_response = await client.get("/ops/metrics")

    assert metrics_response.status_code == 200
    payload = metrics_response.json()
    distributions = payload["metrics"]["distributions"]
    assert any(
        item["name"] == "sync_latency_ms"
        and item["labels"]["operation"] == "push"
        and item["labels"]["status_code"] == "200"
        and item["count"] >= 1
        for item in distributions
    )
    assert any(
        item["name"] == "sync_push_payload_bytes"
        and item["labels"]["operation"] == "push"
        and item["count"] >= 1
        and item["max"] > 0
        for item in distributions
    )
