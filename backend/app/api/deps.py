from __future__ import annotations

from collections.abc import AsyncIterator
from typing import Any

import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.error_handlers import ApiError
from app.core.config import Settings
from app.core.metrics import MetricsRegistry
from app.core.security import AuthFailureRateLimiter, SecurityManager, sanitize_jwt_error
from app.db.session import DatabaseRuntime
from app.services.auth_service import AuthenticatedRequestContext, AuthService
from app.services.sync_service import SyncService

oauth2_bearer = OAuth2PasswordBearer(tokenUrl="/auth/login")


def get_settings(request: Request) -> Settings:
    return request.app.state.settings


def get_database_runtime(request: Request) -> DatabaseRuntime:
    return request.app.state.database_runtime


def get_security_manager(request: Request) -> SecurityManager:
    return request.app.state.security_manager


def get_auth_rate_limiter(request: Request) -> AuthFailureRateLimiter:
    return request.app.state.auth_rate_limiter


def get_metrics_registry(request: Request) -> MetricsRegistry:
    return request.app.state.metrics_registry


async def get_db_session(request: Request) -> AsyncIterator[AsyncSession]:
    runtime = get_database_runtime(request)
    async with runtime.session_factory() as session:
        yield session


def get_auth_service(
    settings: Any = Depends(get_settings),
    session: Any = Depends(get_db_session),
    security_manager: Any = Depends(get_security_manager),
    rate_limiter: Any = Depends(get_auth_rate_limiter),
) -> AuthService:
    return AuthService(
        session=session,
        security_manager=security_manager,
        rate_limiter=rate_limiter,
        security_settings=settings.security,
    )


def get_sync_service(
    session: Any = Depends(get_db_session),
) -> SyncService:
    return SyncService(session=session)


async def get_current_auth_context(
    request: Request,
    token: str = Depends(oauth2_bearer),
    auth_service: Any = Depends(get_auth_service),
) -> AuthenticatedRequestContext:
    try:
        claims = auth_service.security_manager.decode_access_token(token)
        context = await auth_service.get_authenticated_context(
            user_id=claims.user_id,
            device_id=claims.device_id,
            session_id=claims.session_id,
        )
        request.state.audit_user_id = str(context.user_id)
        request.state.audit_device_id = str(context.device_id)
        request.state.audit_refresh_session_id = str(context.refresh_session_id)
        await auth_service.session.rollback()
        return context
    except jwt.PyJWTError as exc:
        await auth_service.session.rollback()
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                "code": "invalid_access_token",
                "message": "Access token is invalid.",
                "details": sanitize_jwt_error(exc),
            },
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc
    except ApiError as exc:
        await auth_service.session.rollback()
        raise HTTPException(
            status_code=exc.status_code,
            detail={
                "code": exc.code,
                "message": exc.message,
                "details": exc.details,
            },
            headers={"WWW-Authenticate": "Bearer", **exc.headers},
        ) from exc
    except HTTPException:
        await auth_service.session.rollback()
        raise
    except Exception as exc:
        await auth_service.session.rollback()
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail={
                "code": "invalid_access_token",
                "message": "Access token is invalid.",
                "details": {"reason": exc.__class__.__name__},
            },
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc
