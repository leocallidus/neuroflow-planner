"""Create stage 3 schema foundation for sync backend.

Revision ID: 20260323_01
Revises:
Create Date: 2026-03-23 00:00:00
"""

from __future__ import annotations

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

# revision identifiers, used by Alembic.
revision = "20260323_01"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute('CREATE EXTENSION IF NOT EXISTS "pgcrypto"')
    op.execute('CREATE EXTENSION IF NOT EXISTS "citext"')

    op.create_table(
        "goal_period_types",
        sa.Column("id", sa.SmallInteger(), nullable=False),
        sa.Column("code", sa.Text(), nullable=False),
        sa.Column("name", sa.Text(), nullable=False),
        sa.PrimaryKeyConstraint("id", name="pk_goal_period_types"),
        sa.UniqueConstraint("code", name="uq_goal_period_types_code"),
        sa.UniqueConstraint("name", name="uq_goal_period_types_name"),
    )

    op.create_table(
        "sync_entity_types",
        sa.Column("id", sa.SmallInteger(), nullable=False),
        sa.Column("code", sa.Text(), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.PrimaryKeyConstraint("id", name="pk_sync_entity_types"),
        sa.UniqueConstraint("code", name="uq_sync_entity_types_code"),
    )

    op.create_table(
        "sync_operation_types",
        sa.Column("id", sa.SmallInteger(), nullable=False),
        sa.Column("code", sa.Text(), nullable=False),
        sa.Column("description", sa.Text(), nullable=False),
        sa.PrimaryKeyConstraint("id", name="pk_sync_operation_types"),
        sa.UniqueConstraint("code", name="uq_sync_operation_types_code"),
    )

    op.create_table(
        "users",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("email", postgresql.CITEXT(), nullable=False),
        sa.Column("password_hash", sa.Text(), nullable=False),
        sa.Column("display_name", sa.Text(), nullable=True),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.text("true")),
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
        sa.Column("last_login_at", sa.DateTime(timezone=True), nullable=True),
        sa.PrimaryKeyConstraint("id", name="pk_users"),
        sa.UniqueConstraint("email", name="uq_users_email"),
    )
    op.create_index("ix_users_updated_at", "users", ["updated_at"], unique=False)

    op.create_table(
        "devices",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("device_label", sa.Text(), nullable=False),
        sa.Column("platform", sa.Text(), nullable=False),
        sa.Column("app_version", sa.Text(), nullable=True),
        sa.Column(
            "registered_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint(
            "revoked_at IS NULL OR revoked_at >= registered_at",
            name="ck_devices_revoked_after_registered",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name="fk_devices_user_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_devices"),
    )
    op.create_index("ix_devices_user_id", "devices", ["user_id"], unique=False)
    op.create_index("ix_devices_last_seen_at", "devices", ["last_seen_at"], unique=False)

    op.create_table(
        "refresh_sessions",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("device_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("token_hash", sa.Text(), nullable=False),
        sa.Column(
            "issued_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("issued_from_ip", postgresql.INET(), nullable=True),
        sa.Column("user_agent", sa.Text(), nullable=True),
        sa.CheckConstraint("expires_at > issued_at", name="ck_refresh_sessions_expiry"),
        sa.CheckConstraint(
            "revoked_at IS NULL OR revoked_at >= issued_at",
            name="ck_refresh_sessions_revoked_after_issued",
        ),
        sa.ForeignKeyConstraint(
            ["device_id"],
            ["devices.id"],
            name="fk_refresh_sessions_device_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_refresh_sessions"),
        sa.UniqueConstraint("token_hash", name="uq_refresh_sessions_token_hash"),
    )
    op.create_index(
        "ix_refresh_sessions_device_id", "refresh_sessions", ["device_id"], unique=False
    )
    op.create_index(
        "ix_refresh_sessions_expires_at", "refresh_sessions", ["expires_at"], unique=False
    )

    op.create_table(
        "tags",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("name", sa.Text(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name="fk_tags_user_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_tags"),
        sa.UniqueConstraint("user_id", "name", name="uq_tags_user_name"),
    )
    op.create_index("ix_tags_user_id", "tags", ["user_id"], unique=False)

    op.create_table(
        "tasks",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("parent_task_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("title", sa.Text(), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("start_date", sa.Date(), nullable=True),
        sa.Column("start_time", sa.Time(), nullable=True),
        sa.Column("deadline_date", sa.Date(), nullable=True),
        sa.Column("deadline_time", sa.Time(), nullable=True),
        sa.Column("complexity", sa.Integer(), nullable=False),
        sa.Column("smart_priority", sa.Numeric(10, 4), nullable=True),
        sa.Column("ai_insight", sa.Text(), nullable=True),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
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
        sa.CheckConstraint("complexity >= 0", name="ck_tasks_complexity_non_negative"),
        sa.ForeignKeyConstraint(
            ["parent_task_id"],
            ["tasks.id"],
            name="fk_tasks_parent_task_id",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name="fk_tasks_user_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_tasks"),
    )
    op.create_index("ix_tasks_user_id", "tasks", ["user_id"], unique=False)
    op.create_index("ix_tasks_parent_task_id", "tasks", ["parent_task_id"], unique=False)
    op.create_index("ix_tasks_updated_at", "tasks", ["updated_at"], unique=False)
    op.create_index("ix_tasks_archived_at", "tasks", ["archived_at"], unique=False)

    op.create_table(
        "task_recurrence_rules",
        sa.Column("task_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("frequency_code", sa.Text(), nullable=False),
        sa.Column("interval_value", sa.Integer(), nullable=False, server_default=sa.text("1")),
        sa.Column("by_weekday", sa.SmallInteger(), nullable=True),
        sa.Column("day_of_month", sa.SmallInteger(), nullable=True),
        sa.Column("end_date", sa.Date(), nullable=True),
        sa.Column("occurrence_limit", sa.Integer(), nullable=True),
        sa.CheckConstraint("interval_value > 0", name="ck_task_recurrence_rules_interval_positive"),
        sa.CheckConstraint(
            "by_weekday IS NULL OR by_weekday BETWEEN 1 AND 7",
            name="ck_task_recurrence_rules_weekday_range",
        ),
        sa.CheckConstraint(
            "day_of_month IS NULL OR day_of_month BETWEEN 1 AND 31",
            name="ck_task_recurrence_rules_day_of_month_range",
        ),
        sa.CheckConstraint(
            "occurrence_limit IS NULL OR occurrence_limit > 0",
            name="ck_task_recurrence_rules_occurrence_limit_positive",
        ),
        sa.ForeignKeyConstraint(
            ["task_id"],
            ["tasks.id"],
            name="fk_task_recurrence_rules_task_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("task_id", name="pk_task_recurrence_rules"),
    )

    op.create_table(
        "task_tags",
        sa.Column("task_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("tag_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.ForeignKeyConstraint(
            ["tag_id"],
            ["tags.id"],
            name="fk_task_tags_tag_id",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["task_id"],
            ["tasks.id"],
            name="fk_task_tags_task_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("task_id", "tag_id", name="pk_task_tags"),
    )

    op.create_table(
        "task_dependencies",
        sa.Column("dependent_task_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("blocker_task_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.CheckConstraint(
            "dependent_task_id <> blocker_task_id",
            name="ck_task_dependencies_no_self_loop",
        ),
        sa.ForeignKeyConstraint(
            ["blocker_task_id"],
            ["tasks.id"],
            name="fk_task_dependencies_blocker_task_id",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["dependent_task_id"],
            ["tasks.id"],
            name="fk_task_dependencies_dependent_task_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint(
            "dependent_task_id", "blocker_task_id", name="pk_task_dependencies"
        ),
    )
    op.create_index(
        "ix_task_dependencies_blocker_task_id",
        "task_dependencies",
        ["blocker_task_id"],
        unique=False,
    )

    op.create_table(
        "time_sessions",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("task_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("ended_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.CheckConstraint("ended_at > started_at", name="ck_time_sessions_ends_after_start"),
        sa.ForeignKeyConstraint(
            ["task_id"],
            ["tasks.id"],
            name="fk_time_sessions_task_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_time_sessions"),
    )
    op.create_index(
        "ix_time_sessions_task_started", "time_sessions", ["task_id", "started_at"], unique=False
    )

    op.create_table(
        "task_templates",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("name", sa.Text(), nullable=False),
        sa.Column("title_template", sa.Text(), nullable=False),
        sa.Column("description_template", sa.Text(), nullable=True),
        sa.Column("default_complexity", sa.Integer(), nullable=False),
        sa.Column(
            "default_deadline_offset_days",
            sa.Integer(),
            nullable=False,
            server_default=sa.text("7"),
        ),
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
        sa.CheckConstraint(
            "default_complexity >= 0",
            name="ck_task_templates_complexity_non_negative",
        ),
        sa.CheckConstraint(
            "default_deadline_offset_days >= 0",
            name="ck_task_templates_deadline_offset_non_negative",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name="fk_task_templates_user_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_task_templates"),
        sa.UniqueConstraint("user_id", "name", name="uq_task_templates_user_name"),
    )
    op.create_index("ix_task_templates_user_id", "task_templates", ["user_id"], unique=False)
    op.create_index("ix_task_templates_updated_at", "task_templates", ["updated_at"], unique=False)

    op.create_table(
        "task_template_recurrence_rules",
        sa.Column("task_template_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("frequency_code", sa.Text(), nullable=False),
        sa.Column("interval_value", sa.Integer(), nullable=False, server_default=sa.text("1")),
        sa.Column("by_weekday", sa.SmallInteger(), nullable=True),
        sa.Column("day_of_month", sa.SmallInteger(), nullable=True),
        sa.Column("end_date", sa.Date(), nullable=True),
        sa.Column("occurrence_limit", sa.Integer(), nullable=True),
        sa.CheckConstraint(
            "interval_value > 0",
            name="ck_task_template_recurrence_rules_interval_positive",
        ),
        sa.CheckConstraint(
            "by_weekday IS NULL OR by_weekday BETWEEN 1 AND 7",
            name="ck_task_template_recurrence_rules_weekday_range",
        ),
        sa.CheckConstraint(
            "day_of_month IS NULL OR day_of_month BETWEEN 1 AND 31",
            name="ck_task_template_recurrence_rules_day_of_month_range",
        ),
        sa.CheckConstraint(
            "occurrence_limit IS NULL OR occurrence_limit > 0",
            name="ck_task_template_recurrence_rules_occurrence_limit_positive",
        ),
        sa.ForeignKeyConstraint(
            ["task_template_id"],
            ["task_templates.id"],
            name="fk_task_template_recurrence_rules_task_template_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("task_template_id", name="pk_task_template_recurrence_rules"),
    )

    op.create_table(
        "task_template_tags",
        sa.Column("task_template_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("tag_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.ForeignKeyConstraint(
            ["tag_id"],
            ["tags.id"],
            name="fk_task_template_tags_tag_id",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["task_template_id"],
            ["task_templates.id"],
            name="fk_task_template_tags_task_template_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("task_template_id", "tag_id", name="pk_task_template_tags"),
    )

    op.create_table(
        "goals",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("period_type_id", sa.SmallInteger(), nullable=False),
        sa.Column("title", sa.Text(), nullable=False),
        sa.Column("period_start", sa.Date(), nullable=False),
        sa.Column("period_end", sa.Date(), nullable=False),
        sa.Column("target_value", sa.Numeric(12, 2), nullable=False),
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
        sa.CheckConstraint("period_end >= period_start", name="ck_goals_period_range"),
        sa.CheckConstraint("target_value >= 0", name="ck_goals_target_non_negative"),
        sa.ForeignKeyConstraint(
            ["period_type_id"],
            ["goal_period_types.id"],
            name="fk_goals_period_type_id",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name="fk_goals_user_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_goals"),
    )
    op.create_index("ix_goals_user_id", "goals", ["user_id"], unique=False)
    op.create_index("ix_goals_updated_at", "goals", ["updated_at"], unique=False)
    op.create_index(
        "ix_goals_user_period",
        "goals",
        ["user_id", "period_type_id", "period_start", "period_end"],
        unique=False,
    )

    op.create_table(
        "goal_progress_entries",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("goal_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("value_delta", sa.Numeric(12, 2), nullable=False),
        sa.Column("note", sa.Text(), nullable=True),
        sa.Column("recorded_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.ForeignKeyConstraint(
            ["goal_id"],
            ["goals.id"],
            name="fk_goal_progress_entries_goal_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_goal_progress_entries"),
    )
    op.create_index(
        "ix_goal_progress_entries_goal_recorded",
        "goal_progress_entries",
        ["goal_id", "recorded_at"],
        unique=False,
    )

    op.create_table(
        "mood_entries",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("recorded_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("score", sa.SmallInteger(), nullable=False),
        sa.Column("note", sa.Text(), nullable=True),
        sa.Column("analysis_label", sa.Text(), nullable=True),
        sa.Column("analysis_text", sa.Text(), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.CheckConstraint("score BETWEEN 1 AND 10", name="ck_mood_entries_score_range"),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name="fk_mood_entries_user_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_mood_entries"),
    )
    op.create_index("ix_mood_entries_user_id", "mood_entries", ["user_id"], unique=False)
    op.create_index(
        "ix_mood_entries_user_recorded",
        "mood_entries",
        ["user_id", "recorded_at"],
        unique=False,
    )

    op.create_table(
        "sync_change_log",
        sa.Column(
            "id",
            sa.BigInteger(),
            sa.Identity(always=True),
            nullable=False,
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("device_id", postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column("entity_type_id", sa.SmallInteger(), nullable=False),
        sa.Column("entity_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("operation_type_id", sa.SmallInteger(), nullable=False),
        sa.Column(
            "committed_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.Column("payload_checksum", sa.Text(), nullable=True),
        sa.ForeignKeyConstraint(
            ["device_id"],
            ["devices.id"],
            name="fk_sync_change_log_device_id",
            ondelete="SET NULL",
        ),
        sa.ForeignKeyConstraint(
            ["entity_type_id"],
            ["sync_entity_types.id"],
            name="fk_sync_change_log_entity_type_id",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["operation_type_id"],
            ["sync_operation_types.id"],
            name="fk_sync_change_log_operation_type_id",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name="fk_sync_change_log_user_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_sync_change_log"),
    )
    op.create_index(
        "ix_sync_change_log_user_id",
        "sync_change_log",
        ["user_id"],
        unique=False,
    )
    op.create_index(
        "ix_sync_change_log_user_id_id", "sync_change_log", ["user_id", "id"], unique=False
    )
    op.create_index(
        "ix_sync_change_log_device_id",
        "sync_change_log",
        ["device_id"],
        unique=False,
    )
    op.create_index(
        "ix_sync_change_log_entity",
        "sync_change_log",
        ["user_id", "entity_type_id", "entity_id", "id"],
        unique=False,
    )

    op.create_table(
        "device_sync_state",
        sa.Column("device_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("entity_type_id", sa.SmallInteger(), nullable=False),
        sa.Column("last_pulled_change_id", sa.BigInteger(), nullable=True),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.ForeignKeyConstraint(
            ["device_id"],
            ["devices.id"],
            name="fk_device_sync_state_device_id",
            ondelete="CASCADE",
        ),
        sa.ForeignKeyConstraint(
            ["entity_type_id"],
            ["sync_entity_types.id"],
            name="fk_device_sync_state_entity_type_id",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["last_pulled_change_id"],
            ["sync_change_log.id"],
            name="fk_device_sync_state_last_pulled_change_id",
            ondelete="SET NULL",
        ),
        sa.PrimaryKeyConstraint("device_id", "entity_type_id", name="pk_device_sync_state"),
    )

    op.create_table(
        "tombstones",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            nullable=False,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("user_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("entity_type_id", sa.SmallInteger(), nullable=False),
        sa.Column("entity_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("deleted_change_id", sa.BigInteger(), nullable=True),
        sa.Column(
            "deleted_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.ForeignKeyConstraint(
            ["deleted_change_id"],
            ["sync_change_log.id"],
            name="fk_tombstones_deleted_change_id",
            ondelete="SET NULL",
        ),
        sa.ForeignKeyConstraint(
            ["entity_type_id"],
            ["sync_entity_types.id"],
            name="fk_tombstones_entity_type_id",
            ondelete="RESTRICT",
        ),
        sa.ForeignKeyConstraint(
            ["user_id"],
            ["users.id"],
            name="fk_tombstones_user_id",
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id", name="pk_tombstones"),
        sa.UniqueConstraint("user_id", "entity_type_id", "entity_id", name="uq_tombstones_entity"),
    )
    op.create_index(
        "ix_tombstones_user_deleted_at",
        "tombstones",
        ["user_id", "deleted_at"],
        unique=False,
    )

    goal_period_types = sa.table(
        "goal_period_types",
        sa.column("id", sa.SmallInteger()),
        sa.column("code", sa.Text()),
        sa.column("name", sa.Text()),
    )
    op.bulk_insert(
        goal_period_types,
        [
            {"id": 1, "code": "WEEK", "name": "Weekly"},
            {"id": 2, "code": "MONTH", "name": "Monthly"},
            {"id": 3, "code": "QUARTER", "name": "Quarterly"},
            {"id": 4, "code": "YEAR", "name": "Yearly"},
        ],
    )

    sync_entity_types = sa.table(
        "sync_entity_types",
        sa.column("id", sa.SmallInteger()),
        sa.column("code", sa.Text()),
        sa.column("description", sa.Text()),
    )
    op.bulk_insert(
        sync_entity_types,
        [
            {"id": 1, "code": "TASK", "description": "Task aggregate root"},
            {"id": 2, "code": "TASK_DEPENDENCY", "description": "Task dependency edge"},
            {"id": 3, "code": "TIME_SESSION", "description": "Tracked time session"},
            {"id": 4, "code": "TASK_TEMPLATE", "description": "Task template"},
            {"id": 5, "code": "GOAL", "description": "Goal aggregate root"},
            {"id": 6, "code": "GOAL_PROGRESS_ENTRY", "description": "Goal progress ledger entry"},
            {"id": 7, "code": "MOOD_ENTRY", "description": "Mood entry"},
        ],
    )

    sync_operation_types = sa.table(
        "sync_operation_types",
        sa.column("id", sa.SmallInteger()),
        sa.column("code", sa.Text()),
        sa.column("description", sa.Text()),
    )
    op.bulk_insert(
        sync_operation_types,
        [
            {"id": 1, "code": "UPSERT", "description": "Create or update entity"},
            {"id": 2, "code": "DELETE", "description": "Delete entity"},
            {"id": 3, "code": "RESTORE", "description": "Restore previously deleted entity"},
        ],
    )


def downgrade() -> None:
    op.drop_index("ix_tombstones_user_deleted_at", table_name="tombstones")
    op.drop_table("tombstones")

    op.drop_table("device_sync_state")

    op.drop_index("ix_sync_change_log_entity", table_name="sync_change_log")
    op.drop_index("ix_sync_change_log_device_id", table_name="sync_change_log")
    op.drop_index("ix_sync_change_log_user_id_id", table_name="sync_change_log")
    op.drop_index("ix_sync_change_log_user_id", table_name="sync_change_log")
    op.drop_table("sync_change_log")

    op.drop_index("ix_mood_entries_user_recorded", table_name="mood_entries")
    op.drop_index("ix_mood_entries_user_id", table_name="mood_entries")
    op.drop_table("mood_entries")

    op.drop_index("ix_goal_progress_entries_goal_recorded", table_name="goal_progress_entries")
    op.drop_table("goal_progress_entries")

    op.drop_index("ix_goals_user_period", table_name="goals")
    op.drop_index("ix_goals_updated_at", table_name="goals")
    op.drop_index("ix_goals_user_id", table_name="goals")
    op.drop_table("goals")

    op.drop_table("task_template_tags")
    op.drop_table("task_template_recurrence_rules")
    op.drop_index("ix_task_templates_updated_at", table_name="task_templates")
    op.drop_index("ix_task_templates_user_id", table_name="task_templates")
    op.drop_table("task_templates")

    op.drop_index("ix_time_sessions_task_started", table_name="time_sessions")
    op.drop_table("time_sessions")

    op.drop_index("ix_task_dependencies_blocker_task_id", table_name="task_dependencies")
    op.drop_table("task_dependencies")

    op.drop_table("task_tags")
    op.drop_table("task_recurrence_rules")
    op.drop_index("ix_tasks_archived_at", table_name="tasks")
    op.drop_index("ix_tasks_updated_at", table_name="tasks")
    op.drop_index("ix_tasks_parent_task_id", table_name="tasks")
    op.drop_index("ix_tasks_user_id", table_name="tasks")
    op.drop_table("tasks")

    op.drop_index("ix_tags_user_id", table_name="tags")
    op.drop_table("tags")

    op.drop_index("ix_refresh_sessions_expires_at", table_name="refresh_sessions")
    op.drop_index("ix_refresh_sessions_device_id", table_name="refresh_sessions")
    op.drop_table("refresh_sessions")

    op.drop_index("ix_devices_last_seen_at", table_name="devices")
    op.drop_index("ix_devices_user_id", table_name="devices")
    op.drop_table("devices")

    op.drop_index("ix_users_updated_at", table_name="users")
    op.drop_table("users")

    op.drop_table("sync_operation_types")
    op.drop_table("sync_entity_types")
    op.drop_table("goal_period_types")
