from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse

from app.api.deps import get_metrics_registry, get_settings
from app.core.config import Settings
from app.core.metrics import MetricsRegistry
from app.core.request_context import get_request_id

router = APIRouter(prefix="/ops", tags=["ops"])


@router.get("/metrics")
async def metrics(
    settings: Any = Depends(get_settings),
    metrics_registry: Any = Depends(get_metrics_registry),
) -> JSONResponse:
    return JSONResponse(
        {
            "service": settings.api.title,
            "environment": settings.environment,
            "version": settings.api.version,
            "request_id": get_request_id(),
            "metrics": metrics_registry.snapshot(),
        }
    )
