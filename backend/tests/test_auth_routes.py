from __future__ import annotations

import uuid
from datetime import UTC, datetime

import jwt

from app.api.error_handlers import ApiError
from app.api.deps import get_auth_service
from app.schemas.auth import (
    AuthenticatedUserResponse,
    DeviceSessionResponse,
    TokenBundleResponse,
)
from app.schemas.device import DeviceListItemResponse, DeviceListResponse
from app.services.auth_service import AuthenticatedRequestContext
from tests.test_utils import build_test_app, make_api_client


class StubAuthService:
    def __init__(self) -> None:
        self.logout_called_with: str | None = None
        self.revoke_device_called_with: tuple[AuthenticatedRequestContext, uuid.UUID] | None = None
        self._bundle = TokenBundleResponse(
            access_token="access-token",
            refresh_token="refresh-token",
            expires_in_seconds=1800,
            refresh_session_id=uuid.uuid4(),
            user=AuthenticatedUserResponse(
                id=uuid.uuid4(),
                email="user@example.com",
                display_name="Test User",
                is_active=True,
            ),
            device=DeviceSessionResponse(
                id=uuid.uuid4(),
                device_label="Leo Desktop",
                platform="linux",
                app_version="0.1.0",
                registered_at=datetime.now(tz=UTC),
                last_seen_at=datetime.now(tz=UTC),
                revoked_at=None,
            ),
        )

    async def register_user(self, *_args, **_kwargs) -> TokenBundleResponse:
        return self._bundle

    async def login_user(self, *_args, **_kwargs) -> TokenBundleResponse:
        return self._bundle

    async def refresh_session_token(self, *_args, **_kwargs) -> TokenBundleResponse:
        return self._bundle

    async def logout(self, refresh_token: str) -> None:
        self.logout_called_with = refresh_token

    async def list_devices(self, _auth: AuthenticatedRequestContext) -> DeviceListResponse:
        return DeviceListResponse(
            devices=[
                DeviceListItemResponse(
                    id=self._bundle.device.id,
                    device_label=self._bundle.device.device_label,
                    platform=self._bundle.device.platform,
                    app_version=self._bundle.device.app_version,
                    registered_at=self._bundle.device.registered_at,
                    last_seen_at=self._bundle.device.last_seen_at,
                    revoked_at=None,
                    active_refresh_session_count=1,
                    is_current_device=True,
                )
            ]
        )

    async def revoke_device(
        self,
        *,
        auth: AuthenticatedRequestContext,
        device_id: uuid.UUID,
    ):
        self.revoke_device_called_with = (auth, device_id)
        return {
            "id": device_id,
            "revoked_at": datetime.now(tz=UTC),
            "revoked_refresh_session_count": 1,
        }


def make_auth_context() -> AuthenticatedRequestContext:
    return AuthenticatedRequestContext(
        user_id=uuid.uuid4(),
        device_id=uuid.uuid4(),
        refresh_session_id=uuid.uuid4(),
    )


class StubRollbackSession:
    def __init__(self) -> None:
        self.rollback_calls = 0

    async def rollback(self) -> None:
        self.rollback_calls += 1


class ExpiredTokenSecurityManager:
    def decode_access_token(self, _token: str):
        raise jwt.ExpiredSignatureError("expired")


class ExpiredTokenAuthService:
    def __init__(self) -> None:
        self.security_manager = ExpiredTokenSecurityManager()
        self.session = StubRollbackSession()

    async def get_authenticated_context(self, **_kwargs) -> AuthenticatedRequestContext:
        raise AssertionError("get_authenticated_context should not be called for expired tokens")


class InvalidRefreshAuthService(StubAuthService):
    async def refresh_session_token(self, *_args, **_kwargs) -> TokenBundleResponse:
        raise ApiError(
            status_code=401,
            code="invalid_refresh_token",
            message="Refresh token is invalid.",
            category="auth",
            retryable=False,
        )

async def test_register_endpoint_returns_created_token_bundle() -> None:
    app = build_test_app()
    auth_service = StubAuthService()

    async def override_auth_service() -> StubAuthService:
        return auth_service

    app.dependency_overrides[get_auth_service] = override_auth_service

    async with make_api_client(app) as client:
        response = await client.post(
            "/auth/register",
            json={
                "email": "user@example.com",
                "password": "supersecret123",
                "display_name": "Test User",
                "device": {
                    "device_label": "Leo Desktop",
                    "platform": "linux",
                    "app_version": "0.1.0",
                },
            },
        )

    assert response.status_code == 201
    payload = response.json()
    assert payload["token_type"] == "bearer"
    assert payload["user"]["email"] == "user@example.com"


async def test_login_endpoint_returns_token_bundle() -> None:
    app = build_test_app()
    auth_service = StubAuthService()

    async def override_auth_service() -> StubAuthService:
        return auth_service

    app.dependency_overrides[get_auth_service] = override_auth_service

    async with make_api_client(app) as client:
        response = await client.post(
            "/auth/login",
            json={
                "email": "user@example.com",
                "password": "supersecret123",
                "device": {
                    "device_label": "Leo Desktop",
                    "platform": "linux",
                    "app_version": "0.1.0",
                },
            },
        )

    assert response.status_code == 200
    payload = response.json()
    assert payload["access_token"] == "access-token"
    assert payload["user"]["display_name"] == "Test User"


async def test_logout_endpoint_returns_204() -> None:
    app = build_test_app()
    auth_service = StubAuthService()

    async def override_auth_service() -> StubAuthService:
        return auth_service

    app.dependency_overrides[get_auth_service] = override_auth_service

    async with make_api_client(app) as client:
        response = await client.post(
            "/auth/logout",
            json={"refresh_token": "refresh-token-value-long-enough-for-validation"},
        )

    assert response.status_code == 204
    assert auth_service.logout_called_with == "refresh-token-value-long-enough-for-validation"


async def test_refresh_endpoint_returns_token_bundle() -> None:
    app = build_test_app()
    auth_service = StubAuthService()

    async def override_auth_service() -> StubAuthService:
        return auth_service

    app.dependency_overrides[get_auth_service] = override_auth_service

    async with make_api_client(app) as client:
        response = await client.post(
            "/auth/refresh",
            json={"refresh_token": "refresh-token-value-long-enough-for-validation"},
        )

    assert response.status_code == 200
    payload = response.json()
    assert payload["access_token"] == "access-token"
    assert payload["refresh_token"] == "refresh-token"


async def test_refresh_endpoint_returns_auth_error_envelope_for_invalid_token() -> None:
    app = build_test_app()
    auth_service = InvalidRefreshAuthService()

    async def override_auth_service() -> InvalidRefreshAuthService:
        return auth_service

    app.dependency_overrides[get_auth_service] = override_auth_service

    async with make_api_client(app) as client:
        response = await client.post(
            "/auth/refresh",
            json={"refresh_token": "refresh-token-value-long-enough-for-validation"},
        )

    assert response.status_code == 401
    payload = response.json()
    assert payload["error"]["code"] == "invalid_refresh_token"
    assert payload["error"]["category"] == "auth"
    assert payload["error"]["retryable"] is False


async def test_devices_endpoint_requires_bearer_token_and_uses_error_envelope() -> None:
    app = build_test_app()
    auth_service = StubAuthService()

    async def override_auth_service() -> StubAuthService:
        return auth_service

    app.dependency_overrides[get_auth_service] = override_auth_service

    async with make_api_client(app) as client:
        response = await client.get("/devices")

    assert response.status_code == 401
    payload = response.json()
    assert payload["error"]["status"] == 401
    assert payload["error"]["code"] == "http_error"
    assert payload["error"]["message"] == "Not authenticated"
    assert payload["error"]["category"] == "auth"
    assert payload["error"]["retryable"] is False


async def test_devices_endpoint_returns_device_list_for_authenticated_context() -> None:
    from app.api.deps import get_current_auth_context

    app = build_test_app()
    auth_service = StubAuthService()
    auth_context = make_auth_context()

    async def override_auth_service() -> StubAuthService:
        return auth_service

    async def override_auth_context() -> AuthenticatedRequestContext:
        return auth_context

    app.dependency_overrides[get_auth_service] = override_auth_service
    app.dependency_overrides[get_current_auth_context] = override_auth_context

    async with make_api_client(app) as client:
        response = await client.get("/devices")

    assert response.status_code == 200
    payload = response.json()
    assert payload["devices"][0]["device_label"] == "Leo Desktop"
    assert payload["devices"][0]["is_current_device"] is True


async def test_revoke_device_endpoint_returns_revocation_summary() -> None:
    from app.api.deps import get_current_auth_context

    app = build_test_app()
    auth_service = StubAuthService()
    auth_context = make_auth_context()
    device_id = uuid.uuid4()

    async def override_auth_service() -> StubAuthService:
        return auth_service

    async def override_auth_context() -> AuthenticatedRequestContext:
        return auth_context

    app.dependency_overrides[get_auth_service] = override_auth_service
    app.dependency_overrides[get_current_auth_context] = override_auth_context

    async with make_api_client(app) as client:
        response = await client.post(f"/devices/{device_id}/revoke")

    assert response.status_code == 200
    payload = response.json()
    assert payload["id"] == str(device_id)
    assert payload["revoked_refresh_session_count"] == 1
    assert auth_service.revoke_device_called_with == (auth_context, device_id)


async def test_devices_endpoint_returns_invalid_access_token_envelope_for_expired_token() -> None:
    app = build_test_app()
    expired_auth_service = ExpiredTokenAuthService()

    async def override_auth_service() -> ExpiredTokenAuthService:
        return expired_auth_service

    app.dependency_overrides[get_auth_service] = override_auth_service

    async with make_api_client(app) as client:
        response = await client.get("/devices", headers={"Authorization": "Bearer expired-token"})

    assert response.status_code == 401
    payload = response.json()
    assert payload["error"]["code"] == "invalid_access_token"
    assert payload["error"]["category"] == "auth"
    assert payload["error"]["details"]["reason"] == "ExpiredSignatureError"
    assert expired_auth_service.session.rollback_calls == 1
