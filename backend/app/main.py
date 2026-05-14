from __future__ import annotations

import logging
import uuid
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from time import perf_counter

import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.api.error_handlers import register_exception_handlers
from app.api.routes.auth import router as auth_router
from app.api.routes.devices import router as devices_router
from app.api.routes.health import router as health_router
from app.api.routes.ops import router as ops_router
from app.api.routes.sync import router as sync_router
from app.core.config import Settings, get_settings
from app.core.logging import configure_logging
from app.core.metrics import MetricsRegistry
from app.core.request_context import clear_request_id, get_request_id, set_request_id
from app.core.security import AuthFailureRateLimiter, SecurityManager
from app.db.session import DatabaseRuntime, build_database_runtime

LOG = logging.getLogger("app.main")
HTTP_LOG = logging.getLogger("app.http")
AUDIT_LOG = logging.getLogger("app.audit")


def create_app(
    settings_override: Settings | None = None,
    database_runtime_override: DatabaseRuntime | None = None,
) -> FastAPI:
    settings = settings_override or get_settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        configure_logging(settings.logging)
        database_runtime = database_runtime_override or build_database_runtime(settings.database)
        security_manager = SecurityManager(settings.security)
        auth_rate_limiter = AuthFailureRateLimiter(settings.security)
        metrics_registry = MetricsRegistry()

        app.state.settings = settings
        app.state.database_runtime = database_runtime
        app.state.security_manager = security_manager
        app.state.auth_rate_limiter = auth_rate_limiter
        app.state.metrics_registry = metrics_registry

        LOG.info(
            "application.startup",
            extra={
                "environment": settings.environment,
                "database_url": settings.database.safe_url,
                "check_on_startup": settings.database.check_on_startup,
            },
        )

        if settings.database.check_on_startup:
            try:
                await database_runtime.ping()
                LOG.info(
                    "database.startup_check.passed",
                    extra={"database_url": settings.database.safe_url},
                )
            except Exception:
                LOG.exception(
                    "database.startup_check.failed",
                    extra={"database_url": settings.database.safe_url},
                )
                raise

        try:
            yield
        finally:
            if database_runtime_override is None:
                await database_runtime.close()
            LOG.info("application.shutdown")

    app = FastAPI(
        title=settings.api.title,
        version=settings.api.version,
        debug=settings.api.reload,
        root_path=settings.api.root_path,
        lifespan=lifespan,
        docs_url="/docs" if settings.api.docs_enabled else None,
        redoc_url="/redoc" if settings.api.docs_enabled else None,
        openapi_url="/openapi.json" if settings.api.docs_enabled else None,
    )

    register_exception_handlers(app)
    app.include_router(auth_router)
    app.include_router(devices_router)
    app.include_router(health_router)
    if settings.api.metrics_enabled:
        app.include_router(ops_router)
    app.include_router(sync_router)

    @app.middleware("http")
    async def request_context_middleware(request: Request, call_next):
        request_id = request.headers.get("X-Request-ID", str(uuid.uuid4()))
        token = set_request_id(request_id)
        request.state.request_id = request_id
        started = perf_counter()
        request.state.audit_action = _resolve_audit_action(request.method, request.url.path)

        HTTP_LOG.info(
            "request.started",
            extra={
                "request_id": request_id,
                "method": request.method,
                "path": request.url.path,
                "query": request.url.query,
            },
        )

        try:
            response = await call_next(request)
        except Exception:
            HTTP_LOG.exception(
                "request.failed",
                extra={
                    "request_id": request_id,
                    "method": request.method,
                    "path": request.url.path,
                },
            )
            clear_request_id(token)
            raise

        duration_ms = round((perf_counter() - started) * 1000, 2)
        response.headers["X-Request-ID"] = request_id
        _record_metrics(app, request, response.status_code, duration_ms)
        _log_audit_event(request, response.status_code, duration_ms)

        HTTP_LOG.info(
            "request.completed",
            extra={
                "request_id": request_id,
                "method": request.method,
                "path": request.url.path,
                "status_code": response.status_code,
                "duration_ms": duration_ms,
            },
        )
        clear_request_id(token)
        return response

    @app.get("/", include_in_schema=False)
    async def root() -> JSONResponse:
        return JSONResponse(
            {
                "service": settings.api.title,
                "version": settings.api.version,
                "environment": settings.environment,
                "request_id": get_request_id(),
            }
        )

    return app


def _resolve_audit_action(method: str, path: str) -> str | None:
    normalized = path.rstrip("/") or "/"
    action_map = {
        ("POST", "/auth/register"): "auth.register",
        ("POST", "/auth/login"): "auth.login",
        ("POST", "/auth/refresh"): "auth.refresh",
        ("POST", "/auth/logout"): "auth.logout",
        ("GET", "/devices"): "device.list",
        ("POST", "/sync/bootstrap"): "sync.bootstrap",
        ("POST", "/sync/pull"): "sync.pull",
        ("POST", "/sync/push"): "sync.push",
    }
    if method.upper() == "POST" and normalized.startswith("/devices/") and normalized.endswith("/revoke"):
        return "device.revoke"
    return action_map.get((method.upper(), normalized))


def _record_metrics(app: FastAPI, request: Request, status_code: int, duration_ms: float) -> None:
    metrics: MetricsRegistry = app.state.metrics_registry
    path = request.url.path.rstrip("/") or "/"
    method = request.method.upper()
    metrics.increment(
        "http_requests_total",
        method=method,
        path=path,
        status_code=status_code,
    )
    metrics.observe(
        "http_request_latency_ms",
        duration_ms,
        method=method,
        path=path,
        status_code=status_code,
    )
    if path.startswith("/sync/"):
        operation = path.split("/")[-1]
        metrics.observe(
            "sync_latency_ms",
            duration_ms,
            operation=operation,
            status_code=status_code,
        )
        if path == "/sync/push":
            payload_size = int(request.headers.get("content-length", "0") or "0")
            metrics.observe("sync_push_payload_bytes", payload_size, operation="push")
        if status_code == 409:
            metrics.increment("sync_conflicts_total", operation=operation)
    if path.startswith("/auth/") and status_code >= 400:
        operation = path.split("/")[-1]
        metrics.increment("auth_failures_total", operation=operation, status_code=status_code)


def _log_audit_event(request: Request, status_code: int, duration_ms: float) -> None:
    action = getattr(request.state, "audit_action", None)
    if not action:
        return
    AUDIT_LOG.info(
        "audit.event",
        extra={
            "action": action,
            "outcome": "success" if status_code < 400 else "failure",
            "status_code": status_code,
            "duration_ms": duration_ms,
            "request_path": request.url.path,
            "request_method": request.method.upper(),
            "user_id": getattr(request.state, "audit_user_id", ""),
            "device_id": getattr(request.state, "audit_device_id", ""),
            "refresh_session_id": getattr(request.state, "audit_refresh_session_id", ""),
            "attempted_changes": getattr(request.state, "audit_attempted_changes", 0),
            "accepted_changes": getattr(request.state, "audit_accepted_changes", 0),
            "remote_changes": getattr(request.state, "audit_remote_changes", 0),
        },
    )


app = create_app()


def run() -> None:
    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.api.host,
        port=settings.api.port,
        reload=settings.api.reload,
        factory=False,
    )
