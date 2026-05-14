from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, Request, status
from fastapi.responses import Response

from app.api.deps import get_auth_service
from app.schemas.auth import (
    LoginRequest,
    LogoutRequest,
    RefreshRequest,
    RegisterRequest,
    TokenBundleResponse,
)
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=TokenBundleResponse, status_code=status.HTTP_201_CREATED)
async def register(
    payload: RegisterRequest,
    request: Request,
    auth_service: Any = Depends(get_auth_service),
) -> TokenBundleResponse:
    response = await auth_service.register_user(
        payload,
        client_ip=_get_client_ip(request),
        user_agent=request.headers.get("User-Agent"),
    )
    request.state.audit_user_id = str(response.user.id)
    request.state.audit_device_id = str(response.device.id)
    request.state.audit_refresh_session_id = str(response.refresh_session_id)
    return response


@router.post("/login", response_model=TokenBundleResponse)
async def login(
    payload: LoginRequest,
    request: Request,
    auth_service: Any = Depends(get_auth_service),
) -> TokenBundleResponse:
    response = await auth_service.login_user(
        payload,
        client_ip=_get_client_ip(request),
        user_agent=request.headers.get("User-Agent"),
    )
    request.state.audit_user_id = str(response.user.id)
    request.state.audit_device_id = str(response.device.id)
    request.state.audit_refresh_session_id = str(response.refresh_session_id)
    return response


@router.post("/refresh", response_model=TokenBundleResponse)
async def refresh(
    payload: RefreshRequest,
    request: Request,
    auth_service: Any = Depends(get_auth_service),
) -> TokenBundleResponse:
    response = await auth_service.refresh_session_token(
        payload.refresh_token,
        client_ip=_get_client_ip(request),
        user_agent=request.headers.get("User-Agent"),
    )
    request.state.audit_user_id = str(response.user.id)
    request.state.audit_device_id = str(response.device.id)
    request.state.audit_refresh_session_id = str(response.refresh_session_id)
    return response


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
async def logout(
    payload: LogoutRequest,
    request: Request,
    auth_service: Any = Depends(get_auth_service),
) -> Response:
    await auth_service.logout(payload.refresh_token)
    request.state.audit_refresh_session_id = "logout-by-refresh-token"
    return Response(status_code=status.HTTP_204_NO_CONTENT)


def _get_client_ip(request: Request) -> str:
    forwarded_for = request.headers.get("X-Forwarded-For")
    if forwarded_for:
        return forwarded_for.split(",", maxsplit=1)[0].strip()
    return request.client.host if request.client is not None else "unknown"
