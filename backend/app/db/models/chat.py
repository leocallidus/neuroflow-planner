from __future__ import annotations

"""Legacy chat schema models kept only for historical migration context.

These tables were introduced during the cancelled mobile/server-AI expansion.
They are intentionally not imported into `app.db.models.__all__`, so the active
backend runtime no longer includes them in ORM metadata or startup schema
verification.
"""

import uuid
from datetime import datetime

from sqlalchemy import DateTime, Index
from sqlalchemy.orm import Mapped

from app.db.base import (
    UUID,
    Base,
    CheckConstraint,
    CreatedAtMixin,
    ForeignKey,
    Integer,
    Text,
    UniqueConstraint,
    UUIDPrimaryKeyMixin,
    UpdatedAtMixin,
    mapped_column,
)


class ChatConversation(UUIDPrimaryKeyMixin, CreatedAtMixin, UpdatedAtMixin, Base):
    __tablename__ = "chat_conversations"
    __table_args__ = (
        Index("ix_chat_conversations_user_id", "user_id"),
        Index("ix_chat_conversations_updated_at", "updated_at"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    title: Mapped[str] = mapped_column(Text, nullable=False)
    context_summary: Mapped[str] = mapped_column(Text, nullable=False, default="")
    pinned_facts_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]")
    context_reset_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    archived_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class ChatMessage(UUIDPrimaryKeyMixin, Base):
    __tablename__ = "chat_messages"
    __table_args__ = (
        CheckConstraint("conversation_seq > 0", name="conversation_seq_positive"),
        CheckConstraint("client_seq IS NULL OR client_seq > 0", name="client_seq_positive"),
        UniqueConstraint("conversation_id", "conversation_seq", name="uq_chat_messages_conversation_seq"),
        Index("ix_chat_messages_conversation_seq", "conversation_id", "conversation_seq"),
        Index("ix_chat_messages_device_id", "device_id"),
    )

    conversation_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("chat_conversations.id", ondelete="CASCADE"),
        nullable=False,
    )
    device_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("devices.id", ondelete="CASCADE"),
        nullable=False,
    )
    role_code: Mapped[str] = mapped_column(Text, nullable=False)
    conversation_seq: Mapped[int] = mapped_column(Integer, nullable=False)
    client_seq: Mapped[int | None] = mapped_column(Integer)
    content: Mapped[str] = mapped_column(Text, nullable=False)
    attachments_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    edited_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
