from __future__ import annotations

import asyncio
from typing import Any

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from app.api.deps import get_database_runtime, get_settings
from app.core.config import Settings
from app.core.request_context import get_request_id
from app.db.session import DatabaseRuntime
from app.schemas.health import DependencyStatus, LivenessResponse, ReadinessResponse

router = APIRouter(prefix="/health", tags=["health"])


@router.get("/live", response_model=LivenessResponse)
async def live(settings: Any = Depends(get_settings)) -> LivenessResponse:
    return LivenessResponse(
        status="alive",
        service=settings.api.title,
        environment=settings.environment,
        version=settings.api.version,
        request_id=get_request_id(),
    )


@router.get("/ready", response_model=ReadinessResponse)
async def ready(
    settings: Any = Depends(get_settings),
    database_runtime: Any = Depends(get_database_runtime),
) -> JSONResponse:
    database_dependency = DependencyStatus(status="unknown")
    http_status = 200

    try:
        await asyncio.wait_for(
            database_runtime.ping(),
            timeout=settings.database.readiness_timeout_seconds,
        )
        database_dependency = DependencyStatus(
            status="ready",
            details={"database_url": settings.database.safe_url},
        )
    except Exception as exc:
        database_dependency = DependencyStatus(
            status="not_ready",
            details={
                "database_url": settings.database.safe_url,
                "reason": str(exc),
            },
        )
        http_status = 503

    payload = ReadinessResponse(
        status="ready" if http_status == 200 else "not_ready",
        service=settings.api.title,
        environment=settings.environment,
        version=settings.api.version,
        request_id=get_request_id(),
        database=database_dependency,
    )
    return JSONResponse(status_code=http_status, content=payload.model_dump())
