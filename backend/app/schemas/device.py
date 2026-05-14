from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel


class DeviceListItemResponse(BaseModel):
    id: uuid.UUID
    device_label: str
    platform: str
    app_version: str | None
    registered_at: datetime
    last_seen_at: datetime | None
    revoked_at: datetime | None
    active_refresh_session_count: int
    is_current_device: bool


class DeviceListResponse(BaseModel):
    devices: list[DeviceListItemResponse]


class DeviceRevokeResponse(BaseModel):
    id: uuid.UUID
    revoked_at: datetime
    revoked_refresh_session_count: int
