from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class DeviceBindingInput(BaseModel):
    device_id: uuid.UUID | None = None
    device_label: str = Field(min_length=1, max_length=255)
    platform: str = Field(min_length=1, max_length=100)
    app_version: str | None = Field(default=None, max_length=100)


class RegisterRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)
    password: str = Field(min_length=8, max_length=256)
    display_name: str | None = Field(default=None, max_length=255)
    device: DeviceBindingInput


class LoginRequest(BaseModel):
    email: str = Field(min_length=3, max_length=320)
    password: str = Field(min_length=1, max_length=256)
    device: DeviceBindingInput


class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=32, max_length=2048)


class LogoutRequest(BaseModel):
    refresh_token: str = Field(min_length=32, max_length=2048)


class AuthenticatedUserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    email: str
    display_name: str | None
    is_active: bool


class DeviceSessionResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    device_label: str
    platform: str
    app_version: str | None
    registered_at: datetime
    last_seen_at: datetime | None
    revoked_at: datetime | None


class TokenBundleResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    expires_in_seconds: int
    user: AuthenticatedUserResponse
    device: DeviceSessionResponse
    refresh_session_id: uuid.UUID
