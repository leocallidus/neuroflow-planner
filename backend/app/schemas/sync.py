from __future__ import annotations

import uuid
from datetime import datetime
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator


class SyncEntityCode(StrEnum):
    TASK = "TASK"
    TASK_DEPENDENCY = "TASK_DEPENDENCY"
    TIME_SESSION = "TIME_SESSION"
    TASK_TEMPLATE = "TASK_TEMPLATE"
    GOAL = "GOAL"
    GOAL_PROGRESS_ENTRY = "GOAL_PROGRESS_ENTRY"
    MOOD_ENTRY = "MOOD_ENTRY"


class SyncOperationCode(StrEnum):
    UPSERT = "UPSERT"
    DELETE = "DELETE"
    RESTORE = "RESTORE"


class SyncBootstrapRequest(BaseModel):
    limit: int = Field(default=500, ge=1, le=2000)


class SyncPullRequest(BaseModel):
    since_change_id: int = Field(default=0, ge=0)
    limit: int = Field(default=500, ge=1, le=2000)


class ClientSyncChange(BaseModel):
    client_change_id: uuid.UUID
    entity_type: SyncEntityCode
    operation: SyncOperationCode
    entity_id: uuid.UUID | None = None
    payload: dict[str, Any] | None = None

    @model_validator(mode="after")
    def validate_payload_requirements(self) -> ClientSyncChange:
        if self.operation in {SyncOperationCode.UPSERT, SyncOperationCode.RESTORE} and not self.payload:
            raise ValueError("payload is required for UPSERT/RESTORE operations.")
        if self.operation == SyncOperationCode.DELETE and self.entity_id is None and not self.payload:
            raise ValueError("DELETE requires entity_id or payload with a natural key.")
        return self


class SyncPushRequest(BaseModel):
    since_change_id: int = Field(default=0, ge=0)
    pull_limit: int = Field(default=500, ge=1, le=2000)
    changes: list[ClientSyncChange] = Field(default_factory=list, max_length=1000)


class ServerSyncChange(BaseModel):
    change_id: int
    entity_type: SyncEntityCode
    entity_id: uuid.UUID
    operation: SyncOperationCode
    committed_at: datetime
    payload: dict[str, Any] | None = None


class PushAcceptedChange(BaseModel):
    client_change_id: uuid.UUID
    entity_type: SyncEntityCode
    entity_id: uuid.UUID
    operation: SyncOperationCode
    server_change_id: int
    idempotent_replay: bool = False


class SyncPullResponse(BaseModel):
    model_config = ConfigDict(use_enum_values=True)

    since_change_id: int
    next_change_id: int
    latest_change_id: int
    has_more: bool
    changes: list[ServerSyncChange]


class SyncBootstrapResponse(BaseModel):
    model_config = ConfigDict(use_enum_values=True)

    user_id: uuid.UUID
    device_id: uuid.UUID
    latest_change_id: int
    next_change_id: int
    has_more: bool
    supported_entity_types: list[SyncEntityCode]
    changes: list[ServerSyncChange]


class SyncPushResponse(BaseModel):
    model_config = ConfigDict(use_enum_values=True)

    accepted: list[PushAcceptedChange]
    remote_since_change_id: int
    remote_next_change_id: int
    latest_change_id: int
    has_more_remote_changes: bool
    remote_changes: list[ServerSyncChange]
