from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends, Request

from app.api.deps import get_current_auth_context, get_sync_service
from app.schemas.sync import (
    SyncBootstrapRequest,
    SyncBootstrapResponse,
    SyncPullRequest,
    SyncPullResponse,
    SyncPushRequest,
    SyncPushResponse,
)
from app.services.auth_service import AuthenticatedRequestContext
from app.services.sync_service import SyncService

router = APIRouter(prefix="/sync", tags=["sync"])


@router.post("/bootstrap", response_model=SyncBootstrapResponse)
async def bootstrap(
    request: Request,
    payload: SyncBootstrapRequest,
    auth: Any = Depends(get_current_auth_context),
    sync_service: Any = Depends(get_sync_service),
) -> SyncBootstrapResponse:
    request.state.audit_user_id = str(auth.user_id)
    request.state.audit_device_id = str(auth.device_id)
    request.state.audit_refresh_session_id = str(auth.refresh_session_id)
    response = await sync_service.bootstrap(auth, payload)
    request.state.audit_remote_changes = len(response.changes)
    return response


@router.post("/pull", response_model=SyncPullResponse)
async def pull(
    request: Request,
    payload: SyncPullRequest,
    auth: Any = Depends(get_current_auth_context),
    sync_service: Any = Depends(get_sync_service),
) -> SyncPullResponse:
    request.state.audit_user_id = str(auth.user_id)
    request.state.audit_device_id = str(auth.device_id)
    request.state.audit_refresh_session_id = str(auth.refresh_session_id)
    response = await sync_service.pull(auth, payload)
    request.state.audit_remote_changes = len(response.changes)
    return response


@router.post("/push", response_model=SyncPushResponse)
async def push(
    request: Request,
    payload: SyncPushRequest,
    auth: Any = Depends(get_current_auth_context),
    sync_service: Any = Depends(get_sync_service),
) -> SyncPushResponse:
    request.state.audit_user_id = str(auth.user_id)
    request.state.audit_device_id = str(auth.device_id)
    request.state.audit_refresh_session_id = str(auth.refresh_session_id)
    request.state.audit_attempted_changes = len(payload.changes)
    response = await sync_service.push(auth, payload)
    request.state.audit_accepted_changes = len(response.accepted)
    request.state.audit_remote_changes = len(response.remote_changes)
    return response
