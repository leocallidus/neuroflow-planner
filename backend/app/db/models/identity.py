from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, Index
from sqlalchemy.orm import Mapped, relationship

from app.db.base import (
    CITEXT,
    INET,
    UUID,
    Base,
    CheckConstraint,
    CreatedAtMixin,
    ForeignKey,
    Text,
    UpdatedAtMixin,
    UUIDPrimaryKeyMixin,
    mapped_column,
    text,
)


class User(UUIDPrimaryKeyMixin, CreatedAtMixin, UpdatedAtMixin, Base):
    __tablename__ = "users"
    __table_args__ = (Index("ix_users_updated_at", "updated_at"),)

    email: Mapped[str] = mapped_column(CITEXT(), nullable=False, unique=True)
    password_hash: Mapped[str] = mapped_column(Text, nullable=False)
    display_name: Mapped[str | None] = mapped_column(Text)
    is_active: Mapped[bool] = mapped_column(Boolean, nullable=False, server_default=text("true"))
    last_login_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    devices: Mapped[list[Device]] = relationship("Device", back_populates="user")


class Device(UUIDPrimaryKeyMixin, Base):
    __tablename__ = "devices"
    __table_args__ = (
        CheckConstraint(
            "revoked_at IS NULL OR revoked_at >= registered_at",
            name="revoked_after_registered",
        ),
        Index("ix_devices_user_id", "user_id"),
        Index("ix_devices_last_seen_at", "last_seen_at"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    device_label: Mapped[str] = mapped_column(Text, nullable=False)
    platform: Mapped[str] = mapped_column(Text, nullable=False)
    app_version: Mapped[str | None] = mapped_column(Text)
    registered_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=text("now()"),
    )
    last_seen_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    user: Mapped[User] = relationship("User", back_populates="devices")
    refresh_sessions: Mapped[list[RefreshSession]] = relationship(
        "RefreshSession",
        back_populates="device",
    )


class RefreshSession(UUIDPrimaryKeyMixin, Base):
    __tablename__ = "refresh_sessions"
    __table_args__ = (
        CheckConstraint("expires_at > issued_at", name="expiry"),
        CheckConstraint(
            "revoked_at IS NULL OR revoked_at >= issued_at",
            name="revoked_after_issued",
        ),
        Index("ix_refresh_sessions_device_id", "device_id"),
        Index("ix_refresh_sessions_expires_at", "expires_at"),
    )

    device_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("devices.id", ondelete="CASCADE"),
        nullable=False,
    )
    token_hash: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    issued_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=text("now()"),
    )
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    revoked_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    issued_from_ip: Mapped[str | None] = mapped_column(INET)
    user_agent: Mapped[str | None] = mapped_column(Text)

    device: Mapped[Device] = relationship("Device", back_populates="refresh_sessions")
