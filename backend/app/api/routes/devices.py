from __future__ import annotations

import uuid
from typing import Any

from fastapi import APIRouter, Depends, Request

from app.api.deps import get_auth_service, get_current_auth_context
from app.schemas.device import DeviceListResponse, DeviceRevokeResponse
from app.services.auth_service import AuthenticatedRequestContext, AuthService

router = APIRouter(prefix="/devices", tags=["devices"])


@router.get("", response_model=DeviceListResponse)
async def list_devices(
    request: Request,
    auth: Any = Depends(get_current_auth_context),
    auth_service: Any = Depends(get_auth_service),
) -> DeviceListResponse:
    request.state.audit_user_id = str(auth.user_id)
    request.state.audit_device_id = str(auth.device_id)
    request.state.audit_refresh_session_id = str(auth.refresh_session_id)
    return await auth_service.list_devices(auth)


@router.post("/{device_id}/revoke", response_model=DeviceRevokeResponse)
async def revoke_device(
    request: Request,
    device_id: uuid.UUID,
    auth: Any = Depends(get_current_auth_context),
    auth_service: Any = Depends(get_auth_service),
) -> DeviceRevokeResponse:
    request.state.audit_user_id = str(auth.user_id)
    request.state.audit_device_id = str(auth.device_id)
    request.state.audit_refresh_session_id = str(auth.refresh_session_id)
    return await auth_service.revoke_device(auth=auth, device_id=device_id)
