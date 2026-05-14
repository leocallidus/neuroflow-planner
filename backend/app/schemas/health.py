from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class DependencyStatus(BaseModel):
    status: Literal["ready", "not_ready", "unknown"]
    details: dict[str, str] = Field(default_factory=dict)


class LivenessResponse(BaseModel):
    status: Literal["alive"]
    service: str
    environment: str
    version: str
    request_id: str


class ReadinessResponse(BaseModel):
    status: Literal["ready", "not_ready"]
    service: str
    environment: str
    version: str
    request_id: str
    database: DependencyStatus
