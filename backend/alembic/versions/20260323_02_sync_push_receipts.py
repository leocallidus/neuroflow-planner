"""Add sync push receipts for idempotent push processing.

Revision ID: 20260323_02
Revises: 20260323_01
Create Date: 2026-03-23 00:00:01
"""

from __future__ import annotations

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


revision = "20260323_02"
down_revision = "20260323_01"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "sync_push_receipts",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("device_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("client_change_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("entity_type_id", sa.SmallInteger(), nullable=False),
        sa.Column("entity_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("operation_type_id", sa.SmallInteger(), nullable=False),
        sa.Column("server_change_id", sa.BigInteger(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.ForeignKeyConstraint(
            ["device_id"],
            ["devices.id"],
            name="fk_sync_push_receipts_device_id",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["entity_type_id"],
            ["sync_entity_types.id"],
            name="fk_sync_push_receipts_entity_type_id",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["operation_type_id"],
            ["sync_operation_types.id"],
            name="fk_sync_push_receipts_operation_type_id",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["server_change_id"],
            ["sync_change_log.id"],
            name="fk_sync_push_receipts_server_change_id",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name="fk_sync_push_receipts_user_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_sync_push_receipts"),
        sa.UniqueConstraint(
            "device_id",
            "client_change_id",
            name="uq_sync_push_receipts_device_client_change",
        ),
    )
    op.create_index(
        "ix_sync_push_receipts_user_id",
        "sync_push_receipts",
        ["user_id"],
        unique=False,
    )
    op.create_index(
        "ix_sync_push_receipts_server_change_id",
        "sync_push_receipts",
        ["server_change_id"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_sync_push_receipts_server_change_id", table_name="sync_push_receipts")
    op.drop_index("ix_sync_push_receipts_user_id", table_name="sync_push_receipts")
    op.drop_table("sync_push_receipts")
