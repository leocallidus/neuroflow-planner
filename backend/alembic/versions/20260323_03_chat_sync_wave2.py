"""Add wave 2 chat sync tables and entity types.

Revision ID: 20260323_03
Revises: 20260323_02
Create Date: 2026-03-23 00:00:02
"""

from __future__ import annotations

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op


revision = "20260323_03"
down_revision = "20260323_02"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    existing_tables = set(inspector.get_table_names())

    if "chat_conversations" not in existing_tables:
        op.create_table(
            "chat_conversations",
            sa.Column(
                "id",
                postgresql.UUID(as_uuid=True),
                nullable=False,
                server_default=sa.text("gen_random_uuid()"),
            ),
            sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
            sa.Column("title", sa.Text(), nullable=False),
            sa.Column("archived_at", sa.DateTime(timezone=True), nullable=True),
            sa.Column(
                "created_at",
                sa.DateTime(timezone=True),
                nullable=False,
                server_default=sa.text("now()"),
            ),
            sa.Column(
                "updated_at",
                sa.DateTime(timezone=True),
                nullable=False,
                server_default=sa.text("now()"),
            ),
            sa.ForeignKeyConstraint(
                ["user_id"],
                ["users.id"],
                name="fk_chat_conversations_user_id",
                ondelete="CASCADE",
            ),
            sa.PrimaryKeyConstraint("id", name="pk_chat_conversations"),
        )
        op.create_index(
            "ix_chat_conversations_user_id",
            "chat_conversations",
            ["user_id"],
            unique=False,
        )
        op.create_index(
            "ix_chat_conversations_updated_at",
            "chat_conversations",
            ["updated_at"],
            unique=False,
        )

    if "chat_messages" not in existing_tables:
        op.create_table(
            "chat_messages",
            sa.Column(
                "id",
                postgresql.UUID(as_uuid=True),
                nullable=False,
                server_default=sa.text("gen_random_uuid()"),
            ),
            sa.Column("conversation_id", postgresql.UUID(as_uuid=True), nullable=False),
            sa.Column("device_id", postgresql.UUID(as_uuid=True), nullable=False),
            sa.Column("role_code", sa.Text(), nullable=False),
            sa.Column("conversation_seq", sa.Integer(), nullable=False),
            sa.Column("client_seq", sa.Integer(), nullable=True),
            sa.Column("content", sa.Text(), nullable=False),
            sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
            sa.Column("edited_at", sa.DateTime(timezone=True), nullable=True),
            sa.CheckConstraint("conversation_seq > 0", name="ck_chat_messages_conversation_seq_positive"),
            sa.CheckConstraint("client_seq IS NULL OR client_seq > 0", name="ck_chat_messages_client_seq_positive"),
            sa.ForeignKeyConstraint(
                ["conversation_id"],
                ["chat_conversations.id"],
                name="fk_chat_messages_conversation_id",
                ondelete="CASCADE",
            ),
            sa.ForeignKeyConstraint(
                ["device_id"],
                ["devices.id"],
                name="fk_chat_messages_device_id",
                ondelete="CASCADE",
            ),
            sa.PrimaryKeyConstraint("id", name="pk_chat_messages"),
            sa.UniqueConstraint(
                "conversation_id",
                "conversation_seq",
                name="uq_chat_messages_conversation_seq",
            ),
        )
        op.create_index(
            "ix_chat_messages_conversation_seq",
            "chat_messages",
            ["conversation_id", "conversation_seq"],
            unique=False,
        )
        op.create_index(
            "ix_chat_messages_device_id",
            "chat_messages",
            ["device_id"],
            unique=False,
        )

    sync_entity_types = sa.table(
        "sync_entity_types",
        sa.column("id", sa.SmallInteger()),
        sa.column("code", sa.Text()),
        sa.column("description", sa.Text()),
    )
    existing_codes = {
        row[0]
        for row in bind.execute(
            sa.text(
                "SELECT code FROM sync_entity_types WHERE code IN ('CHAT_CONVERSATION', 'CHAT_MESSAGE')"
            )
        )
    }
    missing_rows = []
    if "CHAT_CONVERSATION" not in existing_codes:
        missing_rows.append(
            {"id": 8, "code": "CHAT_CONVERSATION", "description": "Chat conversation aggregate root"}
        )
    if "CHAT_MESSAGE" not in existing_codes:
        missing_rows.append(
            {"id": 9, "code": "CHAT_MESSAGE", "description": "Chat conversation message"}
        )
    if missing_rows:
        op.bulk_insert(sync_entity_types, missing_rows)


def downgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    existing_tables = set(inspector.get_table_names())

    op.execute("DELETE FROM sync_entity_types WHERE id IN (8, 9)")
    if "chat_messages" in existing_tables:
        existing_indexes = {index["name"] for index in inspector.get_indexes("chat_messages")}
        if "ix_chat_messages_device_id" in existing_indexes:
            op.drop_index("ix_chat_messages_device_id", table_name="chat_messages")
        if "ix_chat_messages_conversation_seq" in existing_indexes:
            op.drop_index("ix_chat_messages_conversation_seq", table_name="chat_messages")
        op.drop_table("chat_messages")
    if "chat_conversations" in existing_tables:
        existing_indexes = {index["name"] for index in inspector.get_indexes("chat_conversations")}
        if "ix_chat_conversations_updated_at" in existing_indexes:
            op.drop_index("ix_chat_conversations_updated_at", table_name="chat_conversations")
        if "ix_chat_conversations_user_id" in existing_indexes:
            op.drop_index("ix_chat_conversations_user_id", table_name="chat_conversations")
        op.drop_table("chat_conversations")
