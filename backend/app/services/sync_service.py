from __future__ import annotations

from contextlib import AbstractAsyncContextManager

from sqlalchemy.ext.asyncio import AsyncSession

from app.api.error_handlers import ApiError
from app.schemas.sync import (
    SyncBootstrapRequest,
    SyncBootstrapResponse,
    SyncEntityCode,
    SyncPullRequest,
    SyncPullResponse,
    SyncPushRequest,
    SyncPushResponse,
)
from app.services.auth_service import AuthenticatedRequestContext
from app.services.merge_service import MergeService


class SyncService:
    def __init__(self, session: AsyncSession) -> None:
        self.session = session
        self.merge_service = MergeService(session)

    async def bootstrap(
        self,
        auth: AuthenticatedRequestContext,
        request: SyncBootstrapRequest,
    ) -> SyncBootstrapResponse:
        try:
            async with self._transaction():
                batch = await self.merge_service.get_incremental_changes(
                    user_id=auth.user_id,
                    since_change_id=0,
                    limit=request.limit,
                )
                await self.merge_service.advance_device_cursor(
                    device_id=auth.device_id,
                    next_change_id=batch.next_change_id,
                )
        except ApiError as exc:
            raise _as_sync_error(exc) from exc

        return SyncBootstrapResponse(
            user_id=auth.user_id,
            device_id=auth.device_id,
            latest_change_id=batch.latest_change_id,
            next_change_id=batch.next_change_id,
            has_more=batch.has_more,
            supported_entity_types=list(SyncEntityCode),
            changes=batch.changes,
        )

    async def pull(
        self,
        auth: AuthenticatedRequestContext,
        request: SyncPullRequest,
    ) -> SyncPullResponse:
        try:
            async with self._transaction():
                batch = await self.merge_service.get_incremental_changes(
                    user_id=auth.user_id,
                    since_change_id=request.since_change_id,
                    limit=request.limit,
                )
                await self.merge_service.advance_device_cursor(
                    device_id=auth.device_id,
                    next_change_id=batch.next_change_id,
                )
        except ApiError as exc:
            raise _as_sync_error(exc) from exc

        return SyncPullResponse(
            since_change_id=request.since_change_id,
            next_change_id=batch.next_change_id,
            latest_change_id=batch.latest_change_id,
            has_more=batch.has_more,
            changes=batch.changes,
        )

    async def push(
        self,
        auth: AuthenticatedRequestContext,
        request: SyncPushRequest,
    ) -> SyncPushResponse:
        accepted = []
        try:
            async with self._transaction():
                for change in request.changes:
                    accepted_change = await self.merge_service.apply_client_change(
                        user_id=auth.user_id,
                        device_id=auth.device_id,
                        change=change,
                    )
                    accepted.append(accepted_change)

            remote_batch = await self.merge_service.get_incremental_changes(
                user_id=auth.user_id,
                since_change_id=request.since_change_id,
                limit=request.pull_limit,
                exclude_change_ids={item.server_change_id for item in accepted},
            )
        except ApiError as exc:
            raise _as_sync_error(exc) from exc

        return SyncPushResponse(
            accepted=accepted,
            remote_since_change_id=request.since_change_id,
            remote_next_change_id=remote_batch.next_change_id,
            latest_change_id=remote_batch.latest_change_id,
            has_more_remote_changes=remote_batch.has_more,
            remote_changes=remote_batch.changes,
        )

    def _transaction(self) -> AbstractAsyncContextManager[object]:
        if self.session.in_transaction():
            return self.session.begin_nested()
        return self.session.begin()


def _as_sync_error(exc: ApiError) -> ApiError:
    return ApiError(
        status_code=exc.status_code,
        code=exc.code,
        message=exc.message,
        details=exc.details,
        headers=exc.headers,
        category="sync" if exc.category == "application" else exc.category,
        retryable=exc.retryable,
    )
