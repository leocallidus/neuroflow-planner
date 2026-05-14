"""Add chat runtime persistence metadata columns.

Revision ID: 20260324_04
Revises: 20260323_03
Create Date: 2026-03-24 00:00:04
"""

from __future__ import annotations

import sqlalchemy as sa

from alembic import op


revision = "20260324_04"
down_revision = "20260323_03"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    existing_tables = set(inspector.get_table_names())

    if "chat_conversations" in existing_tables:
        conversation_columns = {column["name"] for column in inspector.get_columns("chat_conversations")}
        if "context_summary" not in conversation_columns:
            op.add_column(
                "chat_conversations",
                sa.Column("context_summary", sa.Text(), nullable=False, server_default=""),
            )
        if "pinned_facts_json" not in conversation_columns:
            op.add_column(
                "chat_conversations",
                sa.Column("pinned_facts_json", sa.Text(), nullable=False, server_default="[]"),
            )
        if "context_reset_at" not in conversation_columns:
            op.add_column(
                "chat_conversations",
                sa.Column("context_reset_at", sa.DateTime(timezone=True), nullable=True),
            )

    if "chat_messages" in existing_tables:
        message_columns = {column["name"] for column in inspector.get_columns("chat_messages")}
        if "attachments_json" not in message_columns:
            op.add_column(
                "chat_messages",
                sa.Column("attachments_json", sa.Text(), nullable=False, server_default="[]"),
            )


def downgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    existing_tables = set(inspector.get_table_names())

    if "chat_messages" in existing_tables:
        message_columns = {column["name"] for column in inspector.get_columns("chat_messages")}
        if "attachments_json" in message_columns:
            op.drop_column("chat_messages", "attachments_json")

    if "chat_conversations" in existing_tables:
        conversation_columns = {column["name"] for column in inspector.get_columns("chat_conversations")}
        if "context_reset_at" in conversation_columns:
            op.drop_column("chat_conversations", "context_reset_at")
        if "pinned_facts_json" in conversation_columns:
            op.drop_column("chat_conversations", "pinned_facts_json")
        if "context_summary" in conversation_columns:
            op.drop_column("chat_conversations", "context_summary")
