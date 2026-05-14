from __future__ import annotations

import uuid
from datetime import UTC, datetime

from app.api.deps import get_current_auth_context, get_sync_service
from app.schemas.sync import (
    PushAcceptedChange,
    ServerSyncChange,
    SyncBootstrapResponse,
    SyncEntityCode,
    SyncOperationCode,
    SyncPullResponse,
    SyncPushResponse,
)
from app.services.auth_service import AuthenticatedRequestContext
from tests.test_utils import build_test_app, make_api_client


class StubSyncService:
    def __init__(self) -> None:
        self.bootstrap_called_with: tuple[AuthenticatedRequestContext, object] | None = None
        self.pull_called_with: tuple[AuthenticatedRequestContext, object] | None = None
        self.push_called_with: tuple[AuthenticatedRequestContext, object] | None = None
        self.user_id = uuid.uuid4()
        self.device_id = uuid.uuid4()
        self.task_id = uuid.uuid4()
        self.client_change_id = uuid.uuid4()

    async def bootstrap(
        self,
        auth: AuthenticatedRequestContext,
        request: object,
    ) -> SyncBootstrapResponse:
        self.bootstrap_called_with = (auth, request)
        return SyncBootstrapResponse(
            user_id=self.user_id,
            device_id=self.device_id,
            latest_change_id=7,
            next_change_id=7,
            has_more=False,
            supported_entity_types=[SyncEntityCode.TASK, SyncEntityCode.GOAL],
            changes=[
                ServerSyncChange(
                    change_id=7,
                    entity_type=SyncEntityCode.TASK,
                    entity_id=self.task_id,
                    operation=SyncOperationCode.UPSERT,
                    committed_at=datetime.now(tz=UTC),
                    payload={"id": str(self.task_id), "title": "Sync Task"},
                )
            ],
        )

    async def pull(
        self,
        auth: AuthenticatedRequestContext,
        request: object,
    ) -> SyncPullResponse:
        self.pull_called_with = (auth, request)
        return SyncPullResponse(
            since_change_id=5,
            next_change_id=7,
            latest_change_id=7,
            has_more=False,
            changes=[],
        )

    async def push(
        self,
        auth: AuthenticatedRequestContext,
        request: object,
    ) -> SyncPushResponse:
        self.push_called_with = (auth, request)
        return SyncPushResponse(
            accepted=[
                PushAcceptedChange(
                    client_change_id=self.client_change_id,
                    entity_type=SyncEntityCode.TASK,
                    entity_id=self.task_id,
                    operation=SyncOperationCode.UPSERT,
                    server_change_id=8,
                    idempotent_replay=True,
                )
            ],
            remote_since_change_id=5,
            remote_next_change_id=8,
            latest_change_id=8,
            has_more_remote_changes=False,
            remote_changes=[],
        )


def make_auth_context() -> AuthenticatedRequestContext:
    return AuthenticatedRequestContext(
        user_id=uuid.uuid4(),
        device_id=uuid.uuid4(),
        refresh_session_id=uuid.uuid4(),
    )


async def test_sync_bootstrap_endpoint_returns_contract_payload() -> None:
    app = build_test_app()
    auth_context = make_auth_context()
    sync_service = StubSyncService()

    async def override_auth_context() -> AuthenticatedRequestContext:
        return auth_context

    async def override_sync_service() -> StubSyncService:
        return sync_service

    app.dependency_overrides[get_current_auth_context] = override_auth_context
    app.dependency_overrides[get_sync_service] = override_sync_service

    async with make_api_client(app) as client:
        response = await client.post("/sync/bootstrap", json={"limit": 100})

    assert response.status_code == 200
    payload = response.json()
    assert payload["user_id"] == str(sync_service.user_id)
    assert payload["device_id"] == str(sync_service.device_id)
    assert payload["supported_entity_types"] == ["TASK", "GOAL"]
    assert payload["changes"][0]["payload"]["title"] == "Sync Task"
    assert sync_service.bootstrap_called_with is not None
    assert sync_service.bootstrap_called_with[0] == auth_context
    assert sync_service.bootstrap_called_with[1].limit == 100


async def test_sync_pull_endpoint_returns_remote_batch_and_cursor_contract() -> None:
    app = build_test_app()
    auth_context = make_auth_context()
    sync_service = StubSyncService()

    async def override_auth_context() -> AuthenticatedRequestContext:
        return auth_context

    async def override_sync_service() -> StubSyncService:
        return sync_service

    app.dependency_overrides[get_current_auth_context] = override_auth_context
    app.dependency_overrides[get_sync_service] = override_sync_service

    async with make_api_client(app) as client:
        response = await client.post("/sync/pull", json={"since_change_id": 5, "limit": 25})

    assert response.status_code == 200
    payload = response.json()
    assert payload["since_change_id"] == 5
    assert payload["next_change_id"] == 7
    assert payload["latest_change_id"] == 7
    assert payload["has_more"] is False
    assert sync_service.pull_called_with is not None
    assert sync_service.pull_called_with[0] == auth_context
    assert sync_service.pull_called_with[1].limit == 25


async def test_sync_push_endpoint_returns_accepted_changes_and_remote_batch() -> None:
    app = build_test_app()
    auth_context = make_auth_context()
    sync_service = StubSyncService()

    async def override_auth_context() -> AuthenticatedRequestContext:
        return auth_context

    async def override_sync_service() -> StubSyncService:
        return sync_service

    app.dependency_overrides[get_current_auth_context] = override_auth_context
    app.dependency_overrides[get_sync_service] = override_sync_service

    async with make_api_client(app) as client:
        response = await client.post(
            "/sync/push",
            json={
                "since_change_id": 5,
                "pull_limit": 50,
                "changes": [
                    {
                        "client_change_id": str(sync_service.client_change_id),
                        "entity_type": "TASK",
                        "operation": "UPSERT",
                        "entity_id": str(sync_service.task_id),
                        "payload": {
                            "id": str(sync_service.task_id),
                            "title": "Sync Task",
                            "complexity": 1,
                        },
                    }
                ],
            },
        )

    assert response.status_code == 200
    payload = response.json()
    assert payload["accepted"][0]["server_change_id"] == 8
    assert payload["accepted"][0]["idempotent_replay"] is True
    assert payload["remote_since_change_id"] == 5
    assert payload["remote_next_change_id"] == 8
    assert sync_service.push_called_with is not None
    assert sync_service.push_called_with[0] == auth_context
    assert sync_service.push_called_with[1].pull_limit == 50
    assert len(sync_service.push_called_with[1].changes) == 1


async def test_sync_push_requires_payload_for_upsert_operations() -> None:
    app = build_test_app()

    async def override_auth_context() -> AuthenticatedRequestContext:
        return make_auth_context()

    async def override_sync_service() -> StubSyncService:
        return StubSyncService()

    app.dependency_overrides[get_current_auth_context] = override_auth_context
    app.dependency_overrides[get_sync_service] = override_sync_service

    async with make_api_client(app) as client:
        response = await client.post(
            "/sync/push",
            json={
                "changes": [
                    {
                        "client_change_id": str(uuid.uuid4()),
                        "entity_type": "TASK",
                        "operation": "UPSERT",
                        "entity_id": str(uuid.uuid4()),
                    }
                ]
            },
        )

    assert response.status_code == 422
    payload = response.json()
    assert payload["error"]["status"] == 422
    assert payload["error"]["code"] == "validation_error"
    assert payload["error"]["message"] == "Request validation failed."
    assert payload["error"]["category"] == "validation"
    assert payload["error"]["retryable"] is False


async def test_sync_push_rejects_legacy_chat_entity_type() -> None:
    app = build_test_app()

    async def override_auth_context() -> AuthenticatedRequestContext:
        return make_auth_context()

    async def override_sync_service() -> StubSyncService:
        return StubSyncService()

    app.dependency_overrides[get_current_auth_context] = override_auth_context
    app.dependency_overrides[get_sync_service] = override_sync_service

    async with make_api_client(app) as client:
        response = await client.post(
            "/sync/push",
            json={
                "changes": [
                    {
                        "client_change_id": str(uuid.uuid4()),
                        "entity_type": "CHAT_MESSAGE",
                        "operation": "UPSERT",
                        "entity_id": str(uuid.uuid4()),
                        "payload": {"id": str(uuid.uuid4()), "content": "legacy"},
                    }
                ]
            },
        )

    assert response.status_code == 422
    payload = response.json()
    assert payload["error"]["code"] == "validation_error"
    assert payload["error"]["category"] == "validation"
