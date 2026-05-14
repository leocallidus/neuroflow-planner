from __future__ import annotations

import uuid
from datetime import datetime

from sqlalchemy import BigInteger, DateTime, Index, PrimaryKeyConstraint
from sqlalchemy.orm import Mapped

from app.db.base import (
    UUID,
    Base,
    ForeignKey,
    SmallInteger,
    Text,
    UniqueConstraint,
    UUIDPrimaryKeyMixin,
    mapped_column,
    text,
)


class SyncEntityType(Base):
    __tablename__ = "sync_entity_types"

    id: Mapped[int] = mapped_column(SmallInteger, primary_key=True)
    code: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    description: Mapped[str] = mapped_column(Text, nullable=False)


class SyncOperationType(Base):
    __tablename__ = "sync_operation_types"

    id: Mapped[int] = mapped_column(SmallInteger, primary_key=True)
    code: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    description: Mapped[str] = mapped_column(Text, nullable=False)


class SyncChangeLog(Base):
    __tablename__ = "sync_change_log"
    __table_args__ = (
        Index("ix_sync_change_log_user_id", "user_id"),
        Index("ix_sync_change_log_user_id_id", "user_id", "id"),
        Index("ix_sync_change_log_device_id", "device_id"),
        Index("ix_sync_change_log_entity", "user_id", "entity_type_id", "entity_id", "id"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    device_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("devices.id", ondelete="SET NULL"),
    )
    entity_type_id: Mapped[int] = mapped_column(
        SmallInteger,
        ForeignKey("sync_entity_types.id", ondelete="RESTRICT"),
        nullable=False,
    )
    entity_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    operation_type_id: Mapped[int] = mapped_column(
        SmallInteger,
        ForeignKey("sync_operation_types.id", ondelete="RESTRICT"),
        nullable=False,
    )
    committed_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=text("now()"),
    )
    payload_checksum: Mapped[str | None] = mapped_column(Text)


class DeviceSyncState(Base):
    __tablename__ = "device_sync_state"
    __table_args__ = (
        PrimaryKeyConstraint("device_id", "entity_type_id", name="pk_device_sync_state"),
    )

    device_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("devices.id", ondelete="CASCADE"),
        nullable=False,
    )
    entity_type_id: Mapped[int] = mapped_column(
        SmallInteger,
        ForeignKey("sync_entity_types.id", ondelete="RESTRICT"),
        nullable=False,
    )
    last_pulled_change_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey("sync_change_log.id", ondelete="SET NULL"),
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=text("now()"),
    )


class Tombstone(UUIDPrimaryKeyMixin, Base):
    __tablename__ = "tombstones"
    __table_args__ = (
        UniqueConstraint("user_id", "entity_type_id", "entity_id", name="uq_tombstones_entity"),
        Index("ix_tombstones_user_deleted_at", "user_id", "deleted_at"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    entity_type_id: Mapped[int] = mapped_column(
        SmallInteger,
        ForeignKey("sync_entity_types.id", ondelete="RESTRICT"),
        nullable=False,
    )
    entity_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    deleted_change_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey("sync_change_log.id", ondelete="SET NULL"),
    )
    deleted_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=text("now()"),
    )


class SyncPushReceipt(UUIDPrimaryKeyMixin, Base):
    __tablename__ = "sync_push_receipts"
    __table_args__ = (
        UniqueConstraint(
            "device_id",
            "client_change_id",
            name="uq_sync_push_receipts_device_client_change",
        ),
        Index("ix_sync_push_receipts_user_id", "user_id"),
        Index("ix_sync_push_receipts_server_change_id", "server_change_id"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    device_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("devices.id", ondelete="CASCADE"),
        nullable=False,
    )
    client_change_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    entity_type_id: Mapped[int] = mapped_column(
        SmallInteger,
        ForeignKey("sync_entity_types.id", ondelete="RESTRICT"),
        nullable=False,
    )
    entity_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), nullable=False)
    operation_type_id: Mapped[int] = mapped_column(
        SmallInteger,
        ForeignKey("sync_operation_types.id", ondelete="RESTRICT"),
        nullable=False,
    )
    server_change_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("sync_change_log.id", ondelete="CASCADE"),
        nullable=False,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=text("now()"),
    )
