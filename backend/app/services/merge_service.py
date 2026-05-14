from __future__ import annotations

import hashlib
import json
import uuid
from dataclasses import dataclass
from datetime import UTC, date, datetime, time
from decimal import Decimal
from typing import Any

from sqlalchemy import ScalarResult, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.error_handlers import ApiError
from app.db.models.planning import (
    Goal,
    GoalPeriodType,
    GoalProgressEntry,
    MoodEntry,
    Tag,
    Task,
    TaskDependency,
    TaskRecurrenceRule,
    TaskTag,
    TaskTemplate,
    TaskTemplateRecurrenceRule,
    TaskTemplateTag,
    TimeSession,
)
from app.db.models.sync import (
    DeviceSyncState,
    SyncChangeLog,
    SyncPushReceipt,
    Tombstone,
)
from app.schemas.sync import (
    ClientSyncChange,
    PushAcceptedChange,
    ServerSyncChange,
    SyncEntityCode,
    SyncOperationCode,
)

SYNC_ENTITY_IDS: dict[SyncEntityCode, int] = {
    SyncEntityCode.TASK: 1,
    SyncEntityCode.TASK_DEPENDENCY: 2,
    SyncEntityCode.TIME_SESSION: 3,
    SyncEntityCode.TASK_TEMPLATE: 4,
    SyncEntityCode.GOAL: 5,
    SyncEntityCode.GOAL_PROGRESS_ENTRY: 6,
    SyncEntityCode.MOOD_ENTRY: 7,
}

SYNC_OPERATION_IDS: dict[SyncOperationCode, int] = {
    SyncOperationCode.UPSERT: 1,
    SyncOperationCode.DELETE: 2,
    SyncOperationCode.RESTORE: 3,
}

TASK_DEPENDENCY_NAMESPACE = uuid.UUID("f9c38594-0626-4a85-a33f-59ddafb411e3")


@dataclass(slots=True)
class PullBatch:
    changes: list[ServerSyncChange]
    latest_change_id: int
    next_change_id: int
    has_more: bool


class MergeService:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session

    async def apply_client_change(
        self,
        *,
        user_id: uuid.UUID,
        device_id: uuid.UUID,
        change: ClientSyncChange,
    ) -> PushAcceptedChange:
        existing_receipt = await self.session.scalar(
            select(SyncPushReceipt).where(
                SyncPushReceipt.device_id == device_id,
                SyncPushReceipt.client_change_id == change.client_change_id,
            )
        )
        if existing_receipt is not None:
            return PushAcceptedChange(
                client_change_id=change.client_change_id,
                entity_type=change.entity_type,
                entity_id=existing_receipt.entity_id,
                operation=SyncOperationCode(change.operation.value),
                server_change_id=existing_receipt.server_change_id,
                idempotent_replay=True,
            )

        entity_type = change.entity_type
        if change.operation == SyncOperationCode.DELETE:
            entity_id, server_change_id = await self._delete_entity(
                user_id=user_id,
                device_id=device_id,
                entity_type=entity_type,
                change=change,
            )
            applied_operation = SyncOperationCode.DELETE
        else:
            entity_id, server_change_id, applied_operation = await self._upsert_entity(
                user_id=user_id,
                device_id=device_id,
                entity_type=entity_type,
                change=change,
            )

        receipt = SyncPushReceipt(
            user_id=user_id,
            device_id=device_id,
            client_change_id=change.client_change_id,
            entity_type_id=SYNC_ENTITY_IDS[entity_type],
            entity_id=entity_id,
            operation_type_id=SYNC_OPERATION_IDS[applied_operation],
            server_change_id=server_change_id,
        )
        self.session.add(receipt)
        await self.session.flush()

        return PushAcceptedChange(
            client_change_id=change.client_change_id,
            entity_type=entity_type,
            entity_id=entity_id,
            operation=applied_operation,
            server_change_id=server_change_id,
            idempotent_replay=False,
        )

    async def get_incremental_changes(
        self,
        *,
        user_id: uuid.UUID,
        since_change_id: int,
        limit: int,
        exclude_change_ids: set[int] | None = None,
    ) -> PullBatch:
        exclude_change_ids = exclude_change_ids or set()
        latest_change_id = await self._latest_change_id(user_id)

        latest_per_entity = (
            select(
                SyncChangeLog.entity_type_id.label("entity_type_id"),
                SyncChangeLog.entity_id.label("entity_id"),
                func.max(SyncChangeLog.id).label("change_id"),
            )
            .where(
                SyncChangeLog.user_id == user_id,
                SyncChangeLog.id > since_change_id,
                SyncChangeLog.entity_type_id.in_(tuple(SYNC_ENTITY_IDS.values())),
            )
            .group_by(SyncChangeLog.entity_type_id, SyncChangeLog.entity_id)
            .subquery()
        )

        result = await self.session.execute(
            select(SyncChangeLog)
            .join(latest_per_entity, SyncChangeLog.id == latest_per_entity.c.change_id)
            .where(~SyncChangeLog.id.in_(exclude_change_ids) if exclude_change_ids else True)
            .order_by(SyncChangeLog.id.asc())
            .limit(limit + 1)
        )
        logs = list(result.scalars())
        has_more = len(logs) > limit
        if has_more:
            logs = logs[:limit]

        changes = [await self._build_server_change(user_id=user_id, log=log) for log in logs]
        next_change_id = changes[-1].change_id if changes else since_change_id
        return PullBatch(
            changes=changes,
            latest_change_id=latest_change_id,
            next_change_id=next_change_id,
            has_more=has_more,
        )

    async def advance_device_cursor(
        self,
        *,
        device_id: uuid.UUID,
        next_change_id: int,
    ) -> None:
        if next_change_id <= 0:
            return

        now = self._now()
        for _entity_type, entity_type_id in SYNC_ENTITY_IDS.items():
            state = await self.session.scalar(
                select(DeviceSyncState).where(
                    DeviceSyncState.device_id == device_id,
                    DeviceSyncState.entity_type_id == entity_type_id,
                )
            )
            if state is None:
                state = DeviceSyncState(
                    device_id=device_id,
                    entity_type_id=entity_type_id,
                    last_pulled_change_id=next_change_id,
                    updated_at=now,
                )
                self.session.add(state)
            else:
                state.last_pulled_change_id = next_change_id
                state.updated_at = now
        await self.session.flush()

    async def _upsert_entity(
        self,
        *,
        user_id: uuid.UUID,
        device_id: uuid.UUID,
        entity_type: SyncEntityCode,
        change: ClientSyncChange,
    ) -> tuple[uuid.UUID, int, SyncOperationCode]:
        payload = change.payload or {}
        match entity_type:
            case SyncEntityCode.TASK:
                entity_id = await self._apply_task(user_id, payload, change.entity_id)
                payload_snapshot = await self._serialize_task(user_id, entity_id)
            case SyncEntityCode.TASK_DEPENDENCY:
                entity_id = await self._apply_task_dependency(user_id, payload)
                payload_snapshot = await self._serialize_task_dependency(user_id, entity_id)
            case SyncEntityCode.TIME_SESSION:
                entity_id = await self._apply_time_session(user_id, payload, change.entity_id)
                payload_snapshot = await self._serialize_time_session(user_id, entity_id)
            case SyncEntityCode.TASK_TEMPLATE:
                entity_id = await self._apply_task_template(user_id, payload, change.entity_id)
                payload_snapshot = await self._serialize_task_template(user_id, entity_id)
            case SyncEntityCode.GOAL:
                entity_id = await self._apply_goal(user_id, payload, change.entity_id)
                payload_snapshot = await self._serialize_goal(user_id, entity_id)
            case SyncEntityCode.GOAL_PROGRESS_ENTRY:
                entity_id = await self._apply_goal_progress_entry(user_id, payload, change.entity_id)
                payload_snapshot = await self._serialize_goal_progress_entry(user_id, entity_id)
            case SyncEntityCode.MOOD_ENTRY:
                entity_id = await self._apply_mood_entry(user_id, payload, change.entity_id)
                payload_snapshot = await self._serialize_mood_entry(user_id, entity_id)

        tombstone = await self.session.scalar(
            select(Tombstone).where(
                Tombstone.user_id == user_id,
                Tombstone.entity_type_id == SYNC_ENTITY_IDS[entity_type],
                Tombstone.entity_id == entity_id,
            )
        )
        applied_operation = SyncOperationCode.RESTORE if tombstone is not None else SyncOperationCode.UPSERT
        if tombstone is not None:
            await self.session.delete(tombstone)
            await self.session.flush()

        server_change_id = await self._record_change(
            user_id=user_id,
            device_id=device_id,
            entity_type=entity_type,
            entity_id=entity_id,
            operation=applied_operation,
            payload=payload_snapshot,
        )
        return entity_id, server_change_id, applied_operation

    async def _delete_entity(
        self,
        *,
        user_id: uuid.UUID,
        device_id: uuid.UUID,
        entity_type: SyncEntityCode,
        change: ClientSyncChange,
    ) -> tuple[uuid.UUID, int]:
        entity_id, natural_key_payload = await self._delete_entity_row(
            user_id=user_id,
            entity_type=entity_type,
            change=change,
        )
        delete_payload = await self._upsert_tombstone(
            user_id=user_id,
            entity_type=entity_type,
            entity_id=entity_id,
            natural_key_payload=natural_key_payload,
        )
        server_change_id = await self._record_change(
            user_id=user_id,
            device_id=device_id,
            entity_type=entity_type,
            entity_id=entity_id,
            operation=SyncOperationCode.DELETE,
            payload=delete_payload,
        )
        tombstone = await self.session.scalar(
            select(Tombstone).where(
                Tombstone.user_id == user_id,
                Tombstone.entity_type_id == SYNC_ENTITY_IDS[entity_type],
                Tombstone.entity_id == entity_id,
            )
        )
        if tombstone is not None:
            tombstone.deleted_change_id = server_change_id
            await self.session.flush()
        return entity_id, server_change_id

    async def _delete_entity_row(
        self,
        *,
        user_id: uuid.UUID,
        entity_type: SyncEntityCode,
        change: ClientSyncChange,
    ) -> tuple[uuid.UUID, dict[str, Any] | None]:
        payload = change.payload or {}

        match entity_type:
            case SyncEntityCode.TASK:
                entity_id = self._require_entity_id(change.entity_id, payload)
                row = await self.session.scalar(
                    select(Task).where(Task.id == entity_id, Task.user_id == user_id)
                )
                if row is not None:
                    await self.session.delete(row)
                    await self.session.flush()
                return entity_id, None
            case SyncEntityCode.TASK_DEPENDENCY:
                dependency = await self._find_task_dependency(
                    user_id=user_id,
                    entity_id=change.entity_id,
                    payload=payload,
                )
                if dependency is None:
                    entity_id = self._derive_task_dependency_id(
                        self._require_uuid(payload, "dependent_task_id"),
                        self._require_uuid(payload, "blocker_task_id"),
                    )
                    return entity_id, {
                        "dependent_task_id": str(self._require_uuid(payload, "dependent_task_id")),
                        "blocker_task_id": str(self._require_uuid(payload, "blocker_task_id")),
                    }
                entity_id = self._derive_task_dependency_id(
                    dependency.dependent_task_id,
                    dependency.blocker_task_id,
                )
                natural_key_payload = {
                    "dependent_task_id": str(dependency.dependent_task_id),
                    "blocker_task_id": str(dependency.blocker_task_id),
                }
                await self.session.delete(dependency)
                await self.session.flush()
                return entity_id, natural_key_payload
            case SyncEntityCode.TIME_SESSION:
                entity_id = self._require_entity_id(change.entity_id, payload)
                row = await self.session.scalar(select(TimeSession).where(TimeSession.id == entity_id))
                if row is not None:
                    await self._assert_task_belongs_to_user(user_id, row.task_id)
                    await self.session.delete(row)
                    await self.session.flush()
                return entity_id, None
            case SyncEntityCode.TASK_TEMPLATE:
                entity_id = self._require_entity_id(change.entity_id, payload)
                row = await self.session.scalar(
                    select(TaskTemplate).where(TaskTemplate.id == entity_id, TaskTemplate.user_id == user_id)
                )
                if row is not None:
                    await self.session.delete(row)
                    await self.session.flush()
                return entity_id, None
            case SyncEntityCode.GOAL:
                entity_id = self._require_entity_id(change.entity_id, payload)
                row = await self.session.scalar(select(Goal).where(Goal.id == entity_id, Goal.user_id == user_id))
                if row is not None:
                    await self.session.delete(row)
                    await self.session.flush()
                return entity_id, None
            case SyncEntityCode.GOAL_PROGRESS_ENTRY:
                entity_id = self._require_entity_id(change.entity_id, payload)
                row = await self.session.scalar(
                    select(GoalProgressEntry).where(GoalProgressEntry.id == entity_id)
                )
                if row is not None:
                    await self._assert_goal_belongs_to_user(user_id, row.goal_id)
                    await self.session.delete(row)
                    await self.session.flush()
                return entity_id, None
            case SyncEntityCode.MOOD_ENTRY:
                entity_id = self._require_entity_id(change.entity_id, payload)
                row = await self.session.scalar(
                    select(MoodEntry).where(MoodEntry.id == entity_id, MoodEntry.user_id == user_id)
                )
                if row is not None:
                    await self.session.delete(row)
                    await self.session.flush()
                return entity_id, None

    async def _record_change(
        self,
        *,
        user_id: uuid.UUID,
        device_id: uuid.UUID,
        entity_type: SyncEntityCode,
        entity_id: uuid.UUID,
        operation: SyncOperationCode,
        payload: dict[str, Any] | None,
    ) -> int:
        payload_checksum = self._compute_checksum(payload)
        log = SyncChangeLog(
            user_id=user_id,
            device_id=device_id,
            entity_type_id=SYNC_ENTITY_IDS[entity_type],
            entity_id=entity_id,
            operation_type_id=SYNC_OPERATION_IDS[operation],
            payload_checksum=payload_checksum,
        )
        self.session.add(log)
        await self.session.flush()
        return log.id

    async def _upsert_tombstone(
        self,
        *,
        user_id: uuid.UUID,
        entity_type: SyncEntityCode,
        entity_id: uuid.UUID,
        natural_key_payload: dict[str, Any] | None,
    ) -> dict[str, Any]:
        now = self._now()
        tombstone = await self.session.scalar(
            select(Tombstone).where(
                Tombstone.user_id == user_id,
                Tombstone.entity_type_id == SYNC_ENTITY_IDS[entity_type],
                Tombstone.entity_id == entity_id,
            )
        )
        if tombstone is None:
            tombstone = Tombstone(
                user_id=user_id,
                entity_type_id=SYNC_ENTITY_IDS[entity_type],
                entity_id=entity_id,
                deleted_at=now,
            )
            self.session.add(tombstone)
        else:
            tombstone.deleted_at = now
        await self.session.flush()

        payload: dict[str, Any] = {"deleted_at": self._serialize_datetime(tombstone.deleted_at)}
        if natural_key_payload:
            payload.update(natural_key_payload)
        return payload

    async def _build_server_change(
        self,
        *,
        user_id: uuid.UUID,
        log: SyncChangeLog,
    ) -> ServerSyncChange:
        entity_type = self._entity_type_from_id(log.entity_type_id)
        operation = self._operation_from_id(log.operation_type_id)
        if operation == SyncOperationCode.DELETE:
            payload = await self._serialize_delete_payload(user_id, entity_type, log.entity_id)
        else:
            payload = await self._serialize_entity(user_id, entity_type, log.entity_id)
        return ServerSyncChange(
            change_id=log.id,
            entity_type=entity_type,
            entity_id=log.entity_id,
            operation=operation,
            committed_at=log.committed_at,
            payload=payload,
        )

    async def _serialize_delete_payload(
        self,
        user_id: uuid.UUID,
        entity_type: SyncEntityCode,
        entity_id: uuid.UUID,
    ) -> dict[str, Any]:
        tombstone = await self.session.scalar(
            select(Tombstone).where(
                Tombstone.user_id == user_id,
                Tombstone.entity_type_id == SYNC_ENTITY_IDS[entity_type],
                Tombstone.entity_id == entity_id,
            )
        )
        payload: dict[str, Any] = {
            "deleted_at": self._serialize_datetime(tombstone.deleted_at if tombstone else self._now()),
        }
        if entity_type == SyncEntityCode.TASK_DEPENDENCY and tombstone is None:
            payload.update(await self._task_dependency_natural_key_from_entity_id(user_id, entity_id))
        return payload

    async def _serialize_entity(
        self,
        user_id: uuid.UUID,
        entity_type: SyncEntityCode,
        entity_id: uuid.UUID,
    ) -> dict[str, Any]:
        match entity_type:
            case SyncEntityCode.TASK:
                return await self._serialize_task(user_id, entity_id)
            case SyncEntityCode.TASK_DEPENDENCY:
                return await self._serialize_task_dependency(user_id, entity_id)
            case SyncEntityCode.TIME_SESSION:
                return await self._serialize_time_session(user_id, entity_id)
            case SyncEntityCode.TASK_TEMPLATE:
                return await self._serialize_task_template(user_id, entity_id)
            case SyncEntityCode.GOAL:
                return await self._serialize_goal(user_id, entity_id)
            case SyncEntityCode.GOAL_PROGRESS_ENTRY:
                return await self._serialize_goal_progress_entry(user_id, entity_id)
            case SyncEntityCode.MOOD_ENTRY:
                return await self._serialize_mood_entry(user_id, entity_id)

    async def _apply_task(
        self,
        user_id: uuid.UUID,
        payload: dict[str, Any],
        entity_id: uuid.UUID | None,
    ) -> uuid.UUID:
        task_id = self._require_entity_id(entity_id, payload)
        task = await self.session.scalar(select(Task).where(Task.id == task_id, Task.user_id == user_id))
        if task is None:
            task = Task(id=task_id, user_id=user_id, title="", complexity=0)
            self.session.add(task)

        parent_task_id = self._optional_uuid(payload.get("parent_task_id"))
        if parent_task_id is not None:
            await self._assert_task_belongs_to_user(user_id, parent_task_id)
            if parent_task_id == task_id:
                raise ApiError(status_code=409, code="invalid_task_parent", message="Task cannot parent itself.")

        task.parent_task_id = parent_task_id
        task.title = self._require_str(payload, "title")
        task.description = self._optional_str(payload.get("description"))
        task.start_date = self._optional_date(payload.get("start_date"))
        task.start_time = self._optional_time(payload.get("start_time"))
        task.deadline_date = self._optional_date(payload.get("deadline_date"))
        task.deadline_time = self._optional_time(payload.get("deadline_time"))
        task.complexity = self._require_int(payload, "complexity")
        task.smart_priority = self._optional_decimal(payload.get("smart_priority"))
        task.ai_insight = self._optional_str(payload.get("ai_insight"))
        task.completed_at = self._optional_datetime(payload.get("completed_at"))
        task.archived_at = self._optional_datetime(payload.get("archived_at"))
        task.updated_at = self._now()
        await self.session.flush()

        await self._sync_task_recurrence_rule(task.id, payload.get("recurrence_rule"))
        await self._sync_task_tags(user_id, task.id, self._parse_string_list(payload.get("tags")))
        await self.session.flush()
        return task.id

    async def _apply_task_dependency(
        self,
        user_id: uuid.UUID,
        payload: dict[str, Any],
    ) -> uuid.UUID:
        dependent_task_id = self._require_uuid(payload, "dependent_task_id")
        blocker_task_id = self._require_uuid(payload, "blocker_task_id")
        await self._assert_task_belongs_to_user(user_id, dependent_task_id)
        await self._assert_task_belongs_to_user(user_id, blocker_task_id)
        if dependent_task_id == blocker_task_id:
            raise ApiError(
                status_code=409,
                code="invalid_task_dependency",
                message="Task dependency cannot self-reference.",
            )
        dependency = await self.session.scalar(
            select(TaskDependency).where(
                TaskDependency.dependent_task_id == dependent_task_id,
                TaskDependency.blocker_task_id == blocker_task_id,
            )
        )
        if dependency is None:
            dependency = TaskDependency(
                dependent_task_id=dependent_task_id,
                blocker_task_id=blocker_task_id,
            )
            self.session.add(dependency)
        await self.session.flush()
        return self._derive_task_dependency_id(dependent_task_id, blocker_task_id)

    async def _apply_time_session(
        self,
        user_id: uuid.UUID,
        payload: dict[str, Any],
        entity_id: uuid.UUID | None,
    ) -> uuid.UUID:
        session_id = self._require_entity_id(entity_id, payload)
        task_id = self._require_uuid(payload, "task_id")
        await self._assert_task_belongs_to_user(user_id, task_id)

        row = await self.session.scalar(select(TimeSession).where(TimeSession.id == session_id))
        if row is None:
            row = TimeSession(id=session_id, task_id=task_id, started_at=self._now(), ended_at=self._now())
            self.session.add(row)

        row.task_id = task_id
        row.started_at = self._require_datetime(payload, "started_at")
        row.ended_at = self._require_datetime(payload, "ended_at")
        await self.session.flush()
        return row.id

    async def _apply_task_template(
        self,
        user_id: uuid.UUID,
        payload: dict[str, Any],
        entity_id: uuid.UUID | None,
    ) -> uuid.UUID:
        template_id = self._require_entity_id(entity_id, payload)
        template = await self.session.scalar(
            select(TaskTemplate).where(TaskTemplate.id == template_id, TaskTemplate.user_id == user_id)
        )
        if template is None:
            template = TaskTemplate(
                id=template_id,
                user_id=user_id,
                name="",
                title_template="",
                default_complexity=0,
            )
            self.session.add(template)

        template.name = self._require_str(payload, "name")
        template.title_template = self._require_str(payload, "title_template")
        template.description_template = self._optional_str(payload.get("description_template"))
        template.default_complexity = self._require_int(payload, "default_complexity")
        template.default_deadline_offset_days = self._require_int(
            payload,
            "default_deadline_offset_days",
        )
        template.updated_at = self._now()
        await self.session.flush()

        await self._sync_task_template_recurrence_rule(
            template.id,
            payload.get("recurrence_rule"),
        )
        await self._sync_task_template_tags(
            user_id,
            template.id,
            self._parse_string_list(payload.get("tags")),
        )
        await self.session.flush()
        return template.id

    async def _apply_goal(
        self,
        user_id: uuid.UUID,
        payload: dict[str, Any],
        entity_id: uuid.UUID | None,
    ) -> uuid.UUID:
        goal_id = self._require_entity_id(entity_id, payload)
        period_type_code = self._require_str(payload, "period_type_code")
        period_type = await self.session.scalar(
            select(GoalPeriodType).where(GoalPeriodType.code == period_type_code)
        )
        if period_type is None:
            raise ApiError(
                status_code=422,
                code="invalid_goal_period_type",
                message="Unknown goal period type.",
            )

        goal = await self.session.scalar(select(Goal).where(Goal.id == goal_id, Goal.user_id == user_id))
        if goal is None:
            goal = Goal(
                id=goal_id,
                user_id=user_id,
                period_type_id=period_type.id,
                title="",
                period_start=self._now().date(),
                period_end=self._now().date(),
                target_value=Decimal("0"),
            )
            self.session.add(goal)

        goal.period_type_id = period_type.id
        goal.title = self._require_str(payload, "title")
        goal.period_start = self._require_date(payload, "period_start")
        goal.period_end = self._require_date(payload, "period_end")
        goal.target_value = self._require_decimal(payload, "target_value")
        goal.archived_at = self._optional_datetime(payload.get("archived_at"))
        goal.updated_at = self._now()
        await self.session.flush()
        return goal.id

    async def _apply_goal_progress_entry(
        self,
        user_id: uuid.UUID,
        payload: dict[str, Any],
        entity_id: uuid.UUID | None,
    ) -> uuid.UUID:
        entry_id = self._require_entity_id(entity_id, payload)
        goal_id = self._require_uuid(payload, "goal_id")
        await self._assert_goal_belongs_to_user(user_id, goal_id)

        entry = await self.session.scalar(select(GoalProgressEntry).where(GoalProgressEntry.id == entry_id))
        if entry is None:
            entry = GoalProgressEntry(
                id=entry_id,
                goal_id=goal_id,
                value_delta=Decimal("0"),
                recorded_at=self._now(),
            )
            self.session.add(entry)

        entry.goal_id = goal_id
        entry.value_delta = self._require_decimal(payload, "value_delta")
        entry.note = self._optional_str(payload.get("note"))
        entry.recorded_at = self._require_datetime(payload, "recorded_at")
        await self.session.flush()
        return entry.id

    async def _apply_mood_entry(
        self,
        user_id: uuid.UUID,
        payload: dict[str, Any],
        entity_id: uuid.UUID | None,
    ) -> uuid.UUID:
        entry_id = self._require_entity_id(entity_id, payload)
        entry = await self.session.scalar(
            select(MoodEntry).where(MoodEntry.id == entry_id, MoodEntry.user_id == user_id)
        )
        if entry is None:
            entry = MoodEntry(
                id=entry_id,
                user_id=user_id,
                recorded_at=self._now(),
                score=1,
            )
            self.session.add(entry)

        entry.recorded_at = self._require_datetime(payload, "recorded_at")
        entry.score = self._require_int(payload, "score")
        entry.note = self._optional_str(payload.get("note"))
        entry.analysis_label = self._optional_str(payload.get("analysis_label"))
        entry.analysis_text = self._optional_str(payload.get("analysis_text"))
        await self.session.flush()
        return entry.id

    async def _serialize_task(self, user_id: uuid.UUID, entity_id: uuid.UUID) -> dict[str, Any]:
        task = await self.session.scalar(select(Task).where(Task.id == entity_id, Task.user_id == user_id))
        if task is None:
            raise ApiError(status_code=404, code="task_not_found", message="Task not found.")
        tag_names = await self._tag_names_for_task(task.id)
        recurrence_rule = await self.session.scalar(
            select(TaskRecurrenceRule).where(TaskRecurrenceRule.task_id == task.id)
        )
        return {
            "id": str(task.id),
            "parent_task_id": self._serialize_uuid(task.parent_task_id),
            "title": task.title,
            "description": task.description,
            "start_date": self._serialize_date(task.start_date),
            "start_time": self._serialize_time(task.start_time),
            "deadline_date": self._serialize_date(task.deadline_date),
            "deadline_time": self._serialize_time(task.deadline_time),
            "complexity": task.complexity,
            "smart_priority": self._serialize_decimal(task.smart_priority),
            "ai_insight": task.ai_insight,
            "completed_at": self._serialize_datetime(task.completed_at),
            "archived_at": self._serialize_datetime(task.archived_at),
            "created_at": self._serialize_datetime(task.created_at),
            "updated_at": self._serialize_datetime(task.updated_at),
            "tags": tag_names,
            "recurrence_rule": self._serialize_recurrence_rule(recurrence_rule),
        }

    async def _serialize_task_dependency(
        self,
        user_id: uuid.UUID,
        entity_id: uuid.UUID,
    ) -> dict[str, Any]:
        dependency = await self._find_task_dependency(user_id=user_id, entity_id=entity_id, payload=None)
        if dependency is None:
            raise ApiError(
                status_code=404,
                code="task_dependency_not_found",
                message="Task dependency not found.",
            )
        return {
            "dependent_task_id": str(dependency.dependent_task_id),
            "blocker_task_id": str(dependency.blocker_task_id),
            "created_at": self._serialize_datetime(dependency.created_at),
        }

    async def _serialize_time_session(
        self,
        user_id: uuid.UUID,
        entity_id: uuid.UUID,
    ) -> dict[str, Any]:
        row = await self.session.scalar(select(TimeSession).where(TimeSession.id == entity_id))
        if row is None:
            raise ApiError(status_code=404, code="time_session_not_found", message="Time session not found.")
        await self._assert_task_belongs_to_user(user_id, row.task_id)
        return {
            "id": str(row.id),
            "task_id": str(row.task_id),
            "started_at": self._serialize_datetime(row.started_at),
            "ended_at": self._serialize_datetime(row.ended_at),
            "created_at": self._serialize_datetime(row.created_at),
        }

    async def _serialize_task_template(
        self,
        user_id: uuid.UUID,
        entity_id: uuid.UUID,
    ) -> dict[str, Any]:
        row = await self.session.scalar(
            select(TaskTemplate).where(TaskTemplate.id == entity_id, TaskTemplate.user_id == user_id)
        )
        if row is None:
            raise ApiError(
                status_code=404,
                code="task_template_not_found",
                message="Task template not found.",
            )
        tag_names = await self._tag_names_for_task_template(row.id)
        recurrence_rule = await self.session.scalar(
            select(TaskTemplateRecurrenceRule).where(TaskTemplateRecurrenceRule.task_template_id == row.id)
        )
        return {
            "id": str(row.id),
            "name": row.name,
            "title_template": row.title_template,
            "description_template": row.description_template,
            "default_complexity": row.default_complexity,
            "default_deadline_offset_days": row.default_deadline_offset_days,
            "created_at": self._serialize_datetime(row.created_at),
            "updated_at": self._serialize_datetime(row.updated_at),
            "tags": tag_names,
            "recurrence_rule": self._serialize_recurrence_rule(recurrence_rule),
        }

    async def _serialize_goal(self, user_id: uuid.UUID, entity_id: uuid.UUID) -> dict[str, Any]:
        result = await self.session.execute(
            select(Goal, GoalPeriodType)
            .join(GoalPeriodType, Goal.period_type_id == GoalPeriodType.id)
            .where(Goal.id == entity_id, Goal.user_id == user_id)
        )
        row = result.one_or_none()
        if row is None:
            raise ApiError(status_code=404, code="goal_not_found", message="Goal not found.")
        goal, period_type = row
        return {
            "id": str(goal.id),
            "period_type_code": period_type.code,
            "title": goal.title,
            "period_start": self._serialize_date(goal.period_start),
            "period_end": self._serialize_date(goal.period_end),
            "target_value": self._serialize_decimal(goal.target_value),
            "archived_at": self._serialize_datetime(goal.archived_at),
            "created_at": self._serialize_datetime(goal.created_at),
            "updated_at": self._serialize_datetime(goal.updated_at),
        }

    async def _serialize_goal_progress_entry(
        self,
        user_id: uuid.UUID,
        entity_id: uuid.UUID,
    ) -> dict[str, Any]:
        entry = await self.session.scalar(select(GoalProgressEntry).where(GoalProgressEntry.id == entity_id))
        if entry is None:
            raise ApiError(
                status_code=404,
                code="goal_progress_entry_not_found",
                message="Goal progress entry not found.",
            )
        await self._assert_goal_belongs_to_user(user_id, entry.goal_id)
        return {
            "id": str(entry.id),
            "goal_id": str(entry.goal_id),
            "value_delta": self._serialize_decimal(entry.value_delta),
            "note": entry.note,
            "recorded_at": self._serialize_datetime(entry.recorded_at),
            "created_at": self._serialize_datetime(entry.created_at),
        }

    async def _serialize_mood_entry(self, user_id: uuid.UUID, entity_id: uuid.UUID) -> dict[str, Any]:
        row = await self.session.scalar(
            select(MoodEntry).where(MoodEntry.id == entity_id, MoodEntry.user_id == user_id)
        )
        if row is None:
            raise ApiError(status_code=404, code="mood_entry_not_found", message="Mood entry not found.")
        return {
            "id": str(row.id),
            "recorded_at": self._serialize_datetime(row.recorded_at),
            "score": row.score,
            "note": row.note,
            "analysis_label": row.analysis_label,
            "analysis_text": row.analysis_text,
            "created_at": self._serialize_datetime(row.created_at),
        }

    async def _sync_task_recurrence_rule(
        self,
        task_id: uuid.UUID,
        payload: dict[str, Any] | None,
    ) -> None:
        rule = await self.session.scalar(select(TaskRecurrenceRule).where(TaskRecurrenceRule.task_id == task_id))
        if not payload:
            if rule is not None:
                await self.session.delete(rule)
            return
        if rule is None:
            rule = TaskRecurrenceRule(task_id=task_id, frequency_code="DAILY", interval_value=1)
            self.session.add(rule)
        rule.frequency_code = self._require_str(payload, "frequency_code")
        rule.interval_value = self._require_int(payload, "interval_value")
        rule.by_weekday = self._optional_int(payload.get("by_weekday"))
        rule.day_of_month = self._optional_int(payload.get("day_of_month"))
        rule.end_date = self._optional_date(payload.get("end_date"))
        rule.occurrence_limit = self._optional_int(payload.get("occurrence_limit"))

    async def _sync_task_template_recurrence_rule(
        self,
        task_template_id: uuid.UUID,
        payload: dict[str, Any] | None,
    ) -> None:
        rule = await self.session.scalar(
            select(TaskTemplateRecurrenceRule).where(
                TaskTemplateRecurrenceRule.task_template_id == task_template_id
            )
        )
        if not payload:
            if rule is not None:
                await self.session.delete(rule)
            return
        if rule is None:
            rule = TaskTemplateRecurrenceRule(
                task_template_id=task_template_id,
                frequency_code="DAILY",
                interval_value=1,
            )
            self.session.add(rule)
        rule.frequency_code = self._require_str(payload, "frequency_code")
        rule.interval_value = self._require_int(payload, "interval_value")
        rule.by_weekday = self._optional_int(payload.get("by_weekday"))
        rule.day_of_month = self._optional_int(payload.get("day_of_month"))
        rule.end_date = self._optional_date(payload.get("end_date"))
        rule.occurrence_limit = self._optional_int(payload.get("occurrence_limit"))

    async def _sync_task_tags(
        self,
        user_id: uuid.UUID,
        task_id: uuid.UUID,
        tag_names: list[str],
    ) -> None:
        tag_ids = await self._ensure_tags(user_id, tag_names)
        existing = list(
            (
                await self.session.execute(select(TaskTag).where(TaskTag.task_id == task_id))
            ).scalars()
        )
        existing_by_tag = {row.tag_id: row for row in existing}
        desired_ids = set(tag_ids.values())

        for row in existing:
            if row.tag_id not in desired_ids:
                await self.session.delete(row)

        for tag_id in desired_ids:
            if tag_id not in existing_by_tag:
                self.session.add(TaskTag(task_id=task_id, tag_id=tag_id))

    async def _sync_task_template_tags(
        self,
        user_id: uuid.UUID,
        task_template_id: uuid.UUID,
        tag_names: list[str],
    ) -> None:
        tag_ids = await self._ensure_tags(user_id, tag_names)
        existing = list(
            (
                await self.session.execute(
                    select(TaskTemplateTag).where(TaskTemplateTag.task_template_id == task_template_id)
                )
            ).scalars()
        )
        existing_by_tag = {row.tag_id: row for row in existing}
        desired_ids = set(tag_ids.values())

        for row in existing:
            if row.tag_id not in desired_ids:
                await self.session.delete(row)

        for tag_id in desired_ids:
            if tag_id not in existing_by_tag:
                self.session.add(TaskTemplateTag(task_template_id=task_template_id, tag_id=tag_id))

    async def _ensure_tags(self, user_id: uuid.UUID, tag_names: list[str]) -> dict[str, uuid.UUID]:
        normalized = sorted(set(tag_names))
        if not normalized:
            return {}

        existing_tags = list(
            (
                await self.session.execute(
                    select(Tag).where(Tag.user_id == user_id, Tag.name.in_(normalized))
                )
            ).scalars()
        )
        existing_by_name = {tag.name: tag for tag in existing_tags}

        for name in normalized:
            if name not in existing_by_name:
                tag = Tag(user_id=user_id, name=name)
                self.session.add(tag)
                existing_by_name[name] = tag

        await self.session.flush()
        return {name: tag.id for name, tag in existing_by_name.items()}

    async def _tag_names_for_task(self, task_id: uuid.UUID) -> list[str]:
        result = await self.session.execute(
            select(Tag.name)
            .join(TaskTag, TaskTag.tag_id == Tag.id)
            .where(TaskTag.task_id == task_id)
            .order_by(Tag.name.asc())
        )
        return [name for name in result.scalars()]

    async def _tag_names_for_task_template(self, task_template_id: uuid.UUID) -> list[str]:
        result = await self.session.execute(
            select(Tag.name)
            .join(TaskTemplateTag, TaskTemplateTag.tag_id == Tag.id)
            .where(TaskTemplateTag.task_template_id == task_template_id)
            .order_by(Tag.name.asc())
        )
        return [name for name in result.scalars()]

    async def _assert_task_belongs_to_user(self, user_id: uuid.UUID, task_id: uuid.UUID) -> None:
        row = await self.session.scalar(select(Task.id).where(Task.id == task_id, Task.user_id == user_id))
        if row is None:
            raise ApiError(status_code=404, code="task_not_found", message="Task not found.")

    async def _assert_goal_belongs_to_user(self, user_id: uuid.UUID, goal_id: uuid.UUID) -> None:
        row = await self.session.scalar(select(Goal.id).where(Goal.id == goal_id, Goal.user_id == user_id))
        if row is None:
            raise ApiError(status_code=404, code="goal_not_found", message="Goal not found.")

    async def _find_task_dependency(
        self,
        *,
        user_id: uuid.UUID,
        entity_id: uuid.UUID | None,
        payload: dict[str, Any] | None,
    ) -> TaskDependency | None:
        if payload and "dependent_task_id" in payload and "blocker_task_id" in payload:
            dependent_task_id = self._require_uuid(payload, "dependent_task_id")
            blocker_task_id = self._require_uuid(payload, "blocker_task_id")
            await self._assert_task_belongs_to_user(user_id, dependent_task_id)
            await self._assert_task_belongs_to_user(user_id, blocker_task_id)
            return await self.session.scalar(
                select(TaskDependency).where(
                    TaskDependency.dependent_task_id == dependent_task_id,
                    TaskDependency.blocker_task_id == blocker_task_id,
                )
            )

        if entity_id is None:
            return None

        result: ScalarResult[TaskDependency] = (
            await self.session.execute(
                select(TaskDependency)
                .join(Task, Task.id == TaskDependency.dependent_task_id)
                .where(Task.user_id == user_id)
            )
        ).scalars()
        for dependency in result:
            if self._derive_task_dependency_id(
                dependency.dependent_task_id,
                dependency.blocker_task_id,
            ) == entity_id:
                return dependency
        return None

    async def _task_dependency_natural_key_from_entity_id(
        self,
        user_id: uuid.UUID,
        entity_id: uuid.UUID,
    ) -> dict[str, Any]:
        dependency = await self._find_task_dependency(user_id=user_id, entity_id=entity_id, payload=None)
        if dependency is None:
            return {}
        return {
            "dependent_task_id": str(dependency.dependent_task_id),
            "blocker_task_id": str(dependency.blocker_task_id),
        }

    async def _latest_change_id(self, user_id: uuid.UUID) -> int:
        latest = await self.session.scalar(
            select(func.max(SyncChangeLog.id)).where(
                SyncChangeLog.user_id == user_id,
                SyncChangeLog.entity_type_id.in_(tuple(SYNC_ENTITY_IDS.values())),
            )
        )
        return int(latest or 0)

    @staticmethod
    def _entity_type_from_id(entity_type_id: int) -> SyncEntityCode:
        for code, value in SYNC_ENTITY_IDS.items():
            if value == entity_type_id:
                return code
        raise RuntimeError(f"Unknown sync entity type id: {entity_type_id}")

    @staticmethod
    def _operation_from_id(operation_type_id: int) -> SyncOperationCode:
        for code, value in SYNC_OPERATION_IDS.items():
            if value == operation_type_id:
                return code
        raise RuntimeError(f"Unknown sync operation type id: {operation_type_id}")

    @staticmethod
    def _derive_task_dependency_id(
        dependent_task_id: uuid.UUID,
        blocker_task_id: uuid.UUID,
    ) -> uuid.UUID:
        return uuid.uuid5(TASK_DEPENDENCY_NAMESPACE, f"{dependent_task_id}:{blocker_task_id}")

    @staticmethod
    def _compute_checksum(payload: dict[str, Any] | None) -> str | None:
        if payload is None:
            return None
        encoded = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()
        return hashlib.sha256(encoded).hexdigest()

    @staticmethod
    def _require_entity_id(entity_id: uuid.UUID | None, payload: dict[str, Any]) -> uuid.UUID:
        if entity_id is not None:
            return entity_id
        raw_id = payload.get("id")
        if raw_id is None:
            raise ApiError(status_code=422, code="missing_entity_id", message="Entity id is required.")
        return MergeService._coerce_uuid(raw_id)

    @staticmethod
    def _require_uuid(payload: dict[str, Any], key: str) -> uuid.UUID:
        if key not in payload:
            raise ApiError(status_code=422, code="invalid_payload", message=f"Missing field: {key}.")
        return MergeService._coerce_uuid(payload[key])

    @staticmethod
    def _optional_uuid(value: Any) -> uuid.UUID | None:
        if value in (None, ""):
            return None
        return MergeService._coerce_uuid(value)

    @staticmethod
    def _coerce_uuid(value: Any) -> uuid.UUID:
        try:
            return value if isinstance(value, uuid.UUID) else uuid.UUID(str(value))
        except (ValueError, TypeError) as exc:
            raise ApiError(status_code=422, code="invalid_uuid", message="Invalid UUID value.") from exc

    @staticmethod
    def _require_str(payload: dict[str, Any], key: str) -> str:
        value = payload.get(key)
        if value is None or not str(value).strip():
            raise ApiError(status_code=422, code="invalid_payload", message=f"Missing field: {key}.")
        return str(value).strip()

    @staticmethod
    def _optional_str(value: Any) -> str | None:
        if value is None:
            return None
        normalized = str(value).strip()
        return normalized or None

    @staticmethod
    def _require_int(payload: dict[str, Any], key: str) -> int:
        value = payload.get(key)
        if value is None:
            raise ApiError(status_code=422, code="invalid_payload", message=f"Missing field: {key}.")
        try:
            return int(value)
        except (TypeError, ValueError) as exc:
            raise ApiError(status_code=422, code="invalid_integer", message=f"Field {key} must be integer.") from exc

    @staticmethod
    def _optional_int(value: Any) -> int | None:
        if value is None:
            return None
        try:
            return int(value)
        except (TypeError, ValueError) as exc:
            raise ApiError(status_code=422, code="invalid_integer", message="Invalid integer value.") from exc

    @staticmethod
    def _optional_positive_int(value: Any) -> int | None:
        if value in (None, ""):
            return None
        normalized = MergeService._optional_int(value)
        if normalized is None:
            return None
        if normalized <= 0:
            raise ApiError(
                status_code=422,
                code="invalid_integer",
                message="Expected positive integer value.",
            )
        return normalized

    @staticmethod
    def _require_decimal(payload: dict[str, Any], key: str) -> Decimal:
        value = payload.get(key)
        if value is None:
            raise ApiError(status_code=422, code="invalid_payload", message=f"Missing field: {key}.")
        return MergeService._coerce_decimal(value)

    @staticmethod
    def _optional_decimal(value: Any) -> Decimal | None:
        if value is None:
            return None
        return MergeService._coerce_decimal(value)

    @staticmethod
    def _coerce_decimal(value: Any) -> Decimal:
        try:
            return Decimal(str(value))
        except Exception as exc:
            raise ApiError(status_code=422, code="invalid_decimal", message="Invalid decimal value.") from exc

    @staticmethod
    def _require_datetime(payload: dict[str, Any], key: str) -> datetime:
        value = payload.get(key)
        if value is None:
            raise ApiError(status_code=422, code="invalid_payload", message=f"Missing field: {key}.")
        return MergeService._coerce_datetime(value)

    @staticmethod
    def _optional_datetime(value: Any) -> datetime | None:
        if value in (None, ""):
            return None
        return MergeService._coerce_datetime(value)

    @staticmethod
    def _coerce_datetime(value: Any) -> datetime:
        if isinstance(value, datetime):
            return value.astimezone(UTC) if value.tzinfo else value.replace(tzinfo=UTC)
        try:
            normalized = str(value).replace("Z", "+00:00")
            parsed = datetime.fromisoformat(normalized)
            return parsed.astimezone(UTC) if parsed.tzinfo else parsed.replace(tzinfo=UTC)
        except ValueError as exc:
            raise ApiError(status_code=422, code="invalid_datetime", message="Invalid datetime value.") from exc

    @staticmethod
    def _require_date(payload: dict[str, Any], key: str) -> date:
        value = payload.get(key)
        if value is None:
            raise ApiError(status_code=422, code="invalid_payload", message=f"Missing field: {key}.")
        return MergeService._coerce_date(value)

    @staticmethod
    def _optional_date(value: Any) -> date | None:
        if value in (None, ""):
            return None
        return MergeService._coerce_date(value)

    @staticmethod
    def _coerce_date(value: Any) -> date:
        if isinstance(value, date) and not isinstance(value, datetime):
            return value
        try:
            return date.fromisoformat(str(value))
        except ValueError as exc:
            raise ApiError(status_code=422, code="invalid_date", message="Invalid date value.") from exc

    @staticmethod
    def _optional_time(value: Any) -> time | None:
        if value in (None, ""):
            return None
        if isinstance(value, time):
            return value
        try:
            return time.fromisoformat(str(value))
        except ValueError as exc:
            raise ApiError(status_code=422, code="invalid_time", message="Invalid time value.") from exc

    @staticmethod
    def _parse_string_list(value: Any) -> list[str]:
        if value is None:
            return []
        if not isinstance(value, list):
            raise ApiError(status_code=422, code="invalid_payload", message="Expected list of strings.")
        result: list[str] = []
        for item in value:
            normalized = str(item).strip()
            if normalized:
                result.append(normalized)
        return result

    @staticmethod
    def _parse_attachment_list(value: Any) -> list[dict[str, Any]]:
        if value is None:
            return []
        if not isinstance(value, list):
            raise ApiError(status_code=422, code="invalid_payload", message="Expected list of attachments.")
        result: list[dict[str, Any]] = []
        for item in value:
            if not isinstance(item, dict):
                continue
            normalized: dict[str, Any] = {}
            for key in ("id", "kind", "name", "uri", "mimeType"):
                raw = item.get(key)
                if raw in (None, ""):
                    continue
                normalized[key] = str(raw).strip()
            for key in ("fileSize", "width", "height", "duration"):
                raw = item.get(key)
                if raw is None:
                    continue
                try:
                    normalized[key] = int(raw)
                except (TypeError, ValueError):
                    continue
            if normalized:
                result.append(normalized)
        return result

    @staticmethod
    def _parse_json_array(value: str | None) -> list[Any]:
        if not value:
            return []
        try:
            parsed = json.loads(value)
        except json.JSONDecodeError:
            return []
        return parsed if isinstance(parsed, list) else []

    @staticmethod
    def _serialize_uuid(value: uuid.UUID | None) -> str | None:
        return str(value) if value is not None else None

    @staticmethod
    def _serialize_datetime(value: datetime | None) -> str | None:
        if value is None:
            return None
        return value.astimezone(UTC).isoformat().replace("+00:00", "Z")

    @staticmethod
    def _serialize_date(value: date | None) -> str | None:
        return value.isoformat() if value is not None else None

    @staticmethod
    def _serialize_time(value: time | None) -> str | None:
        return value.isoformat() if value is not None else None

    @staticmethod
    def _serialize_decimal(value: Decimal | None) -> str | None:
        return str(value) if value is not None else None

    @staticmethod
    def _serialize_recurrence_rule(
        value: TaskRecurrenceRule | TaskTemplateRecurrenceRule | None,
    ) -> dict[str, Any] | None:
        if value is None:
            return None
        return {
            "frequency_code": value.frequency_code,
            "interval_value": value.interval_value,
            "by_weekday": value.by_weekday,
            "day_of_month": value.day_of_month,
            "end_date": MergeService._serialize_date(value.end_date),
            "occurrence_limit": value.occurrence_limit,
        }

    @staticmethod
    def _now() -> datetime:
        return datetime.now(tz=UTC)
