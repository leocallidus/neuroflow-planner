"""Backfill chat sync columns for legacy chat tables.

Revision ID: 20260324_05
Revises: 20260324_04
Create Date: 2026-03-24 00:00:05
"""

from __future__ import annotations

import sqlalchemy as sa

from alembic import op


revision = "20260324_05"
down_revision = "20260324_04"
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    inspector = sa.inspect(bind)
    existing_tables = set(inspector.get_table_names())

    if "chat_messages" not in existing_tables:
        return

    message_columns = {column["name"]: column for column in inspector.get_columns("chat_messages")}
    existing_indexes = {index["name"] for index in inspector.get_indexes("chat_messages")}
    existing_unique_constraints = {
        constraint["name"] for constraint in inspector.get_unique_constraints("chat_messages")
    }
    existing_check_constraints = {
        constraint["name"] for constraint in inspector.get_check_constraints("chat_messages")
    }

    if "conversation_seq" not in message_columns:
        op.add_column("chat_messages", sa.Column("conversation_seq", sa.Integer(), nullable=True))
        _resequence_conversation_sequence()
        op.alter_column("chat_messages", "conversation_seq", nullable=False)
    else:
        _resequence_conversation_sequence()
        if message_columns["conversation_seq"].get("nullable", True):
            op.alter_column("chat_messages", "conversation_seq", nullable=False)

    if "client_seq" not in message_columns:
        op.add_column("chat_messages", sa.Column("client_seq", sa.Integer(), nullable=True))

    if "ck_chat_messages_conversation_seq_positive" not in existing_check_constraints and "conversation_seq_positive" not in existing_check_constraints:
        op.create_check_constraint(
            "ck_chat_messages_conversation_seq_positive",
            "chat_messages",
            "conversation_seq > 0",
        )
    if "ck_chat_messages_client_seq_positive" not in existing_check_constraints and "client_seq_positive" not in existing_check_constraints:
        op.create_check_constraint(
            "ck_chat_messages_client_seq_positive",
            "chat_messages",
            "client_seq IS NULL OR client_seq > 0",
        )
    if "uq_chat_messages_conversation_seq" not in existing_unique_constraints:
        op.create_unique_constraint(
            "uq_chat_messages_conversation_seq",
            "chat_messages",
            ["conversation_id", "conversation_seq"],
        )
    if "ix_chat_messages_conversation_seq" not in existing_indexes:
        op.create_index(
            "ix_chat_messages_conversation_seq",
            "chat_messages",
            ["conversation_id", "conversation_seq"],
            unique=False,
        )


def downgrade() -> None:
    # Corrective migration for legacy databases only. Intentionally non-destructive.
    return


def _resequence_conversation_sequence() -> None:
    op.execute(
        sa.text(
            """
            WITH ranked AS (
                SELECT id,
                       row_number() OVER (
                           PARTITION BY conversation_id
                           ORDER BY conversation_seq NULLS LAST, created_at, id
                       ) AS next_seq
                FROM chat_messages
            )
            UPDATE chat_messages AS target
            SET conversation_seq = ranked.next_seq
            FROM ranked
            WHERE target.id = ranked.id
            """
        )
    )
