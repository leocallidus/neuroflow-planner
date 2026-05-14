from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass

import httpx

import app.main as app_main
from app.core.config import (
    ApiSettings,
    DatabaseSettings,
    LoggingSettings,
    SecuritySettings,
    Settings,
)
from app.core.metrics import MetricsRegistry
from app.core.security import AuthFailureRateLimiter, SecurityManager
from app.main import create_app


@dataclass
class StubDatabaseRuntime:
    should_fail: bool = False
    failure_message: str = "database unavailable"

    async def ping(self) -> None:
        if self.should_fail:
            raise RuntimeError(self.failure_message)

    async def close(self) -> None:
        return None


def make_settings() -> Settings:
    return Settings.model_construct(
        environment="test",
        api=ApiSettings(title="NeuroFlow Test Backend", version="test", metrics_enabled=True),
        logging=LoggingSettings(level="DEBUG", json_logs=False),
        security=SecuritySettings(
            jwt_secret="test-secret-key-with-at-least-thirty-two-bytes",
            refresh_token_pepper="test-pepper-at-least-thirty-two-bytes",
        ),
        database=DatabaseSettings(
            url="postgresql+asyncpg://neuroflow:neuroflow@localhost:5432/neuroflow_sync",
            check_on_startup=False,
        ),
    )


def build_test_app(
    *,
    settings: Settings | None = None,
    database_runtime: StubDatabaseRuntime | None = None,
):
    effective_settings = settings or make_settings()
    effective_database_runtime = database_runtime or StubDatabaseRuntime()
    app = create_app(
        settings_override=effective_settings,
        database_runtime_override=effective_database_runtime,
    )
    app.state.settings = effective_settings
    app.state.database_runtime = effective_database_runtime
    app.state.security_manager = SecurityManager(effective_settings.security)
    app.state.auth_rate_limiter = AuthFailureRateLimiter(effective_settings.security)
    app.state.metrics_registry = MetricsRegistry()
    return app


@asynccontextmanager
async def make_api_client(app) -> AsyncIterator[httpx.AsyncClient]:
    original_http_info = app_main.HTTP_LOG.info
    original_http_exception = app_main.HTTP_LOG.exception
    original_audit_info = app_main.AUDIT_LOG.info
    app_main.HTTP_LOG.info = lambda *args, **kwargs: None
    app_main.HTTP_LOG.exception = lambda *args, **kwargs: None
    app_main.AUDIT_LOG.info = lambda *args, **kwargs: None
    try:
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            yield client
    finally:
        app_main.HTTP_LOG.info = original_http_info
        app_main.HTTP_LOG.exception = original_http_exception
        app_main.AUDIT_LOG.info = original_audit_info
