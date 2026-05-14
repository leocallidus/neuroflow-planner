from __future__ import annotations

import uuid
from datetime import date, datetime, time
from decimal import Decimal

from sqlalchemy import DateTime, Index, PrimaryKeyConstraint
from sqlalchemy.orm import Mapped, relationship

from app.db.base import (
    UUID,
    Base,
    CheckConstraint,
    CreatedAtMixin,
    Date,
    ForeignKey,
    Integer,
    Numeric,
    SmallInteger,
    Text,
    Time,
    UniqueConstraint,
    UpdatedAtMixin,
    UUIDPrimaryKeyMixin,
    mapped_column,
    text,
)


class Tag(UUIDPrimaryKeyMixin, CreatedAtMixin, Base):
    __tablename__ = "tags"
    __table_args__ = (
        UniqueConstraint("user_id", "name", name="uq_tags_user_name"),
        Index("ix_tags_user_id", "user_id"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)


class Task(UUIDPrimaryKeyMixin, CreatedAtMixin, UpdatedAtMixin, Base):
    __tablename__ = "tasks"
    __table_args__ = (
        CheckConstraint("complexity >= 0", name="complexity_non_negative"),
        Index("ix_tasks_user_id", "user_id"),
        Index("ix_tasks_parent_task_id", "parent_task_id"),
        Index("ix_tasks_updated_at", "updated_at"),
        Index("ix_tasks_archived_at", "archived_at"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    parent_task_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("tasks.id", ondelete="CASCADE"),
    )
    title: Mapped[str] = mapped_column(Text, nullable=False)
    description: Mapped[str | None] = mapped_column(Text)
    start_date: Mapped[date | None] = mapped_column(Date)
    start_time: Mapped[time | None] = mapped_column(Time)
    deadline_date: Mapped[date | None] = mapped_column(Date)
    deadline_time: Mapped[time | None] = mapped_column(Time)
    complexity: Mapped[int] = mapped_column(Integer, nullable=False)
    smart_priority: Mapped[Decimal | None] = mapped_column(Numeric(10, 4))
    ai_insight: Mapped[str | None] = mapped_column(Text)
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    archived_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    recurrence_rule: Mapped[TaskRecurrenceRule | None] = relationship(
        "TaskRecurrenceRule",
        back_populates="task",
        uselist=False,
    )
    time_sessions: Mapped[list[TimeSession]] = relationship("TimeSession", back_populates="task")


class TaskRecurrenceRule(Base):
    __tablename__ = "task_recurrence_rules"
    __table_args__ = (
        CheckConstraint("interval_value > 0", name="interval_positive"),
        CheckConstraint(
            "by_weekday IS NULL OR by_weekday BETWEEN 1 AND 7",
            name="weekday_range",
        ),
        CheckConstraint(
            "day_of_month IS NULL OR day_of_month BETWEEN 1 AND 31",
            name="day_of_month_range",
        ),
        CheckConstraint(
            "occurrence_limit IS NULL OR occurrence_limit > 0",
            name="occurrence_limit_positive",
        ),
    )

    task_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("tasks.id", ondelete="CASCADE"),
        primary_key=True,
    )
    frequency_code: Mapped[str] = mapped_column(Text, nullable=False)
    interval_value: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("1"))
    by_weekday: Mapped[int | None] = mapped_column(SmallInteger)
    day_of_month: Mapped[int | None] = mapped_column(SmallInteger)
    end_date: Mapped[date | None] = mapped_column(Date)
    occurrence_limit: Mapped[int | None] = mapped_column(Integer)

    task: Mapped[Task] = relationship("Task", back_populates="recurrence_rule")


class TaskTag(Base):
    __tablename__ = "task_tags"
    __table_args__ = (PrimaryKeyConstraint("task_id", "tag_id", name="pk_task_tags"),)

    task_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("tasks.id", ondelete="CASCADE"),
        nullable=False,
    )
    tag_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("tags.id", ondelete="CASCADE"),
        nullable=False,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=text("now()"),
    )


class TaskDependency(Base):
    __tablename__ = "task_dependencies"
    __table_args__ = (
        PrimaryKeyConstraint("dependent_task_id", "blocker_task_id", name="pk_task_dependencies"),
        CheckConstraint("dependent_task_id <> blocker_task_id", name="no_self_loop"),
        Index("ix_task_dependencies_blocker_task_id", "blocker_task_id"),
    )

    dependent_task_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("tasks.id", ondelete="CASCADE"),
        nullable=False,
    )
    blocker_task_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("tasks.id", ondelete="CASCADE"),
        nullable=False,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=text("now()"),
    )


class TimeSession(UUIDPrimaryKeyMixin, CreatedAtMixin, Base):
    __tablename__ = "time_sessions"
    __table_args__ = (
        CheckConstraint("ended_at > started_at", name="ends_after_start"),
        Index("ix_time_sessions_task_started", "task_id", "started_at"),
    )

    task_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("tasks.id", ondelete="CASCADE"),
        nullable=False,
    )
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    ended_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    task: Mapped[Task] = relationship("Task", back_populates="time_sessions")


class TaskTemplate(UUIDPrimaryKeyMixin, CreatedAtMixin, UpdatedAtMixin, Base):
    __tablename__ = "task_templates"
    __table_args__ = (
        UniqueConstraint("user_id", "name", name="uq_task_templates_user_name"),
        CheckConstraint("default_complexity >= 0", name="complexity_non_negative"),
        CheckConstraint(
            "default_deadline_offset_days >= 0",
            name="deadline_offset_non_negative",
        ),
        Index("ix_task_templates_user_id", "user_id"),
        Index("ix_task_templates_updated_at", "updated_at"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    name: Mapped[str] = mapped_column(Text, nullable=False)
    title_template: Mapped[str] = mapped_column(Text, nullable=False)
    description_template: Mapped[str | None] = mapped_column(Text)
    default_complexity: Mapped[int] = mapped_column(Integer, nullable=False)
    default_deadline_offset_days: Mapped[int] = mapped_column(
        Integer,
        nullable=False,
        server_default=text("7"),
    )

    recurrence_rule: Mapped[TaskTemplateRecurrenceRule | None] = relationship(
        "TaskTemplateRecurrenceRule",
        back_populates="task_template",
        uselist=False,
    )


class TaskTemplateRecurrenceRule(Base):
    __tablename__ = "task_template_recurrence_rules"
    __table_args__ = (
        CheckConstraint("interval_value > 0", name="interval_positive"),
        CheckConstraint(
            "by_weekday IS NULL OR by_weekday BETWEEN 1 AND 7",
            name="weekday_range",
        ),
        CheckConstraint(
            "day_of_month IS NULL OR day_of_month BETWEEN 1 AND 31",
            name="day_of_month_range",
        ),
        CheckConstraint(
            "occurrence_limit IS NULL OR occurrence_limit > 0",
            name="occurrence_limit_positive",
        ),
    )

    task_template_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("task_templates.id", ondelete="CASCADE"),
        primary_key=True,
    )
    frequency_code: Mapped[str] = mapped_column(Text, nullable=False)
    interval_value: Mapped[int] = mapped_column(Integer, nullable=False, server_default=text("1"))
    by_weekday: Mapped[int | None] = mapped_column(SmallInteger)
    day_of_month: Mapped[int | None] = mapped_column(SmallInteger)
    end_date: Mapped[date | None] = mapped_column(Date)
    occurrence_limit: Mapped[int | None] = mapped_column(Integer)

    task_template: Mapped[TaskTemplate] = relationship(
        "TaskTemplate",
        back_populates="recurrence_rule",
    )


class TaskTemplateTag(Base):
    __tablename__ = "task_template_tags"
    __table_args__ = (
        PrimaryKeyConstraint("task_template_id", "tag_id", name="pk_task_template_tags"),
    )

    task_template_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("task_templates.id", ondelete="CASCADE"),
        nullable=False,
    )
    tag_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("tags.id", ondelete="CASCADE"),
        nullable=False,
    )
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=text("now()"),
    )


class GoalPeriodType(Base):
    __tablename__ = "goal_period_types"

    id: Mapped[int] = mapped_column(SmallInteger, primary_key=True)
    code: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    name: Mapped[str] = mapped_column(Text, nullable=False, unique=True)


class Goal(UUIDPrimaryKeyMixin, CreatedAtMixin, UpdatedAtMixin, Base):
    __tablename__ = "goals"
    __table_args__ = (
        CheckConstraint("period_end >= period_start", name="period_range"),
        CheckConstraint("target_value >= 0", name="target_non_negative"),
        Index("ix_goals_user_id", "user_id"),
        Index("ix_goals_updated_at", "updated_at"),
        Index("ix_goals_user_period", "user_id", "period_type_id", "period_start", "period_end"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    period_type_id: Mapped[int] = mapped_column(
        SmallInteger,
        ForeignKey("goal_period_types.id", ondelete="RESTRICT"),
        nullable=False,
    )
    title: Mapped[str] = mapped_column(Text, nullable=False)
    period_start: Mapped[date] = mapped_column(Date, nullable=False)
    period_end: Mapped[date] = mapped_column(Date, nullable=False)
    target_value: Mapped[Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    archived_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class GoalProgressEntry(UUIDPrimaryKeyMixin, CreatedAtMixin, Base):
    __tablename__ = "goal_progress_entries"
    __table_args__ = (Index("ix_goal_progress_entries_goal_recorded", "goal_id", "recorded_at"),)

    goal_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("goals.id", ondelete="CASCADE"),
        nullable=False,
    )
    value_delta: Mapped[Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    note: Mapped[str | None] = mapped_column(Text)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)


class MoodEntry(UUIDPrimaryKeyMixin, CreatedAtMixin, Base):
    __tablename__ = "mood_entries"
    __table_args__ = (
        CheckConstraint("score BETWEEN 1 AND 10", name="score_range"),
        Index("ix_mood_entries_user_id", "user_id"),
        Index("ix_mood_entries_user_recorded", "user_id", "recorded_at"),
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        nullable=False,
    )
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    score: Mapped[int] = mapped_column(SmallInteger, nullable=False)
    note: Mapped[str | None] = mapped_column(Text)
    analysis_label: Mapped[str | None] = mapped_column(Text)
    analysis_text: Mapped[str | None] = mapped_column(Text)
