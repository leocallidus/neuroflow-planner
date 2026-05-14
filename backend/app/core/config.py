from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict

BASE_DIR = Path(__file__).resolve().parents[2]


class ApiSettings(BaseModel):
    host: str = "0.0.0.0"
    port: int = 8000
    reload: bool = False
    title: str = "NeuroFlow Cloud Backend"
    version: str = "0.1.0"
    root_path: str = ""
    docs_enabled: bool = True
    metrics_enabled: bool = True


class LoggingSettings(BaseModel):
    level: str = "INFO"
    json_logs: bool = True


class SecuritySettings(BaseModel):
    jwt_secret: str = "dev-only-change-me-secret-at-least-32b"
    jwt_algorithm: str = "HS256"
    jwt_issuer: str = "neuroflow-cloud-backend"
    jwt_audience: str = "neuroflow-desktop"
    access_token_ttl_minutes: int = 30
    refresh_token_ttl_days: int = 30
    refresh_token_bytes: int = 48
    refresh_token_pepper: str = "dev-only-refresh-pepper-at-least-32b"
    password_scrypt_n: int = 16384
    password_scrypt_r: int = 8
    password_scrypt_p: int = 1
    password_scrypt_dklen: int = 64
    failed_login_limit_per_identity: int = 5
    failed_login_limit_per_ip: int = 20
    failed_register_limit_per_ip: int = 10
    failed_refresh_limit_per_ip: int = 20
    auth_failure_window_seconds: int = 300


class DatabaseSettings(BaseModel):
    url: str = "postgresql+asyncpg://neuroflow:neuroflow@127.0.0.1:5433/neuroflow_sync"
    echo: bool = False
    pool_size: int = 10
    max_overflow: int = 20
    connect_timeout_seconds: int = 5
    readiness_timeout_seconds: int = 3
    check_on_startup: bool = True

    @property
    def safe_url(self) -> str:
        if "@" not in self.url or "://" not in self.url:
            return self.url
        prefix, suffix = self.url.split("://", maxsplit=1)
        credentials, host = suffix.split("@", maxsplit=1)
        if ":" not in credentials:
            return self.url
        username, _ = credentials.split(":", maxsplit=1)
        return f"{prefix}://{username}:***@{host}"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="NEUROFLOW_",
        env_nested_delimiter="__",
        env_file=BASE_DIR / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    environment: Literal["dev", "test", "prod"] = "dev"
    api: ApiSettings = Field(default_factory=ApiSettings)
    logging: LoggingSettings = Field(default_factory=LoggingSettings)
    security: SecuritySettings = Field(default_factory=SecuritySettings)
    database: DatabaseSettings = Field(default_factory=DatabaseSettings)


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
