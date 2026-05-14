from __future__ import annotations

import uuid
from contextlib import AbstractAsyncContextManager
from dataclasses import dataclass
from datetime import UTC, datetime

from sqlalchemy import Result, func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.error_handlers import ApiError
from app.core.config import SecuritySettings
from app.core.security import AuthFailureRateLimiter, SecurityManager
from app.db.models.identity import Device, RefreshSession, User
from app.schemas.auth import (
    AuthenticatedUserResponse,
    DeviceBindingInput,
    DeviceSessionResponse,
    LoginRequest,
    RegisterRequest,
    TokenBundleResponse,
)
from app.schemas.device import DeviceListItemResponse, DeviceListResponse, DeviceRevokeResponse


@dataclass(slots=True)
class AuthenticatedRequestContext:
    user_id: uuid.UUID
    device_id: uuid.UUID
    refresh_session_id: uuid.UUID


class AuthService:
    def __init__(
        self,
        *,
        session: AsyncSession,
        security_manager: SecurityManager,
        rate_limiter: AuthFailureRateLimiter,
        security_settings: SecuritySettings,
    ) -> None:
        self.session = session
        self.security_manager = security_manager
        self.rate_limiter = rate_limiter
        self.security_settings = security_settings

    async def register_user(
        self,
        payload: RegisterRequest,
        *,
        client_ip: str,
        user_agent: str | None,
    ) -> TokenBundleResponse:
        self._assert_password_policy(payload.password)
        self._ensure_register_allowed(client_ip)
        email = self._normalize_email(payload.email)
        now = self._now()

        async with self._transaction():
            existing_user = await self.session.scalar(select(User).where(User.email == email))
            if existing_user is not None:
                self._record_register_failure(client_ip)
                raise ApiError(
                    status_code=409,
                    code="email_already_registered",
                    message="A user with this email already exists.",
                    category="auth",
                )

            user = User(
                email=email,
                password_hash=self.security_manager.hash_password(payload.password),
                display_name=self._normalize_display_name(payload.display_name),
                is_active=True,
                last_login_at=now,
            )
            self.session.add(user)
            await self.session.flush()

            device = await self._resolve_device_binding(
                user_id=user.id,
                binding=payload.device,
                now=now,
            )
            refresh_session, refresh_token = await self._create_refresh_session(
                device_id=device.id,
                now=now,
                client_ip=client_ip,
                user_agent=user_agent,
            )
            await self.session.flush()

        self.rate_limiter.clear(self._register_ip_key(client_ip))
        return self._build_token_bundle(
            user=user,
            device=device,
            refresh_session=refresh_session,
            refresh_token=refresh_token,
        )

    async def login_user(
        self,
        payload: LoginRequest,
        *,
        client_ip: str,
        user_agent: str | None,
    ) -> TokenBundleResponse:
        email = self._normalize_email(payload.email)
        identity_key = self._login_identity_key(email, client_ip)
        ip_key = self._login_ip_key(client_ip)
        self._ensure_allowed(identity_key, self.security_settings.failed_login_limit_per_identity)
        self._ensure_allowed(ip_key, self.security_settings.failed_login_limit_per_ip)
        now = self._now()

        async with self._transaction():
            user = await self.session.scalar(select(User).where(User.email == email))
            if user is None or not self.security_manager.verify_password(
                payload.password, user.password_hash
            ):
                self._record_login_failure(identity_key, ip_key)
                raise ApiError(
                    status_code=401,
                    code="invalid_credentials",
                    message="Invalid email or password.",
                    category="auth",
                )

            if not user.is_active:
                raise ApiError(
                    status_code=403,
                    code="user_inactive",
                    message="The user account is inactive.",
                    category="auth",
                )

            user.last_login_at = now
            device = await self._resolve_device_binding(
                user_id=user.id,
                binding=payload.device,
                now=now,
            )
            refresh_session, refresh_token = await self._create_refresh_session(
                device_id=device.id,
                now=now,
                client_ip=client_ip,
                user_agent=user_agent,
            )
            await self.session.flush()

        self.rate_limiter.clear(identity_key)
        self.rate_limiter.clear(ip_key)
        return self._build_token_bundle(
            user=user,
            device=device,
            refresh_session=refresh_session,
            refresh_token=refresh_token,
        )

    async def refresh_session_token(
        self,
        refresh_token: str,
        *,
        client_ip: str,
        user_agent: str | None,
    ) -> TokenBundleResponse:
        ip_key = self._refresh_ip_key(client_ip)
        self._ensure_allowed(ip_key, self.security_settings.failed_refresh_limit_per_ip)
        refresh_token_hash = self.security_manager.hash_refresh_token(refresh_token)
        now = self._now()

        async with self._transaction():
            result: Result[tuple[RefreshSession, Device, User]] = await self.session.execute(
                select(RefreshSession, Device, User)
                .join(Device, RefreshSession.device_id == Device.id)
                .join(User, Device.user_id == User.id)
                .where(RefreshSession.token_hash == refresh_token_hash)
            )
            row = result.one_or_none()
            if row is None:
                self._record_refresh_failure(ip_key)
                raise ApiError(
                    status_code=401,
                    code="invalid_refresh_token",
                    message="Refresh token is invalid.",
                    category="auth",
                )

            refresh_session, device, user = row
            if (
                refresh_session.revoked_at is not None
                or refresh_session.expires_at <= now
                or device.revoked_at is not None
                or not user.is_active
            ):
                self._record_refresh_failure(ip_key)
                raise ApiError(
                    status_code=401,
                    code="invalid_refresh_token",
                    message="Refresh token is invalid.",
                    category="auth",
                )

            new_refresh_token = self.security_manager.create_refresh_token()
            refresh_session.token_hash = self.security_manager.hash_refresh_token(new_refresh_token)
            refresh_session.issued_at = now
            refresh_session.expires_at = now + self.security_manager.refresh_token_ttl
            refresh_session.issued_from_ip = client_ip or None
            refresh_session.user_agent = user_agent
            device.last_seen_at = now
            user.last_login_at = now
            await self.session.flush()

        self.rate_limiter.clear(ip_key)
        return self._build_token_bundle(
            user=user,
            device=device,
            refresh_session=refresh_session,
            refresh_token=new_refresh_token,
        )

    async def logout(self, refresh_token: str) -> None:
        refresh_token_hash = self.security_manager.hash_refresh_token(refresh_token)
        now = self._now()

        async with self._transaction():
            refresh_session = await self.session.scalar(
                select(RefreshSession).where(RefreshSession.token_hash == refresh_token_hash)
            )
            if refresh_session is not None and refresh_session.revoked_at is None:
                refresh_session.revoked_at = now

    async def list_devices(self, auth: AuthenticatedRequestContext) -> DeviceListResponse:
        now = self._now()
        active_sessions = func.count(RefreshSession.id).filter(
            RefreshSession.revoked_at.is_(None),
            RefreshSession.expires_at > now,
        )
        result = await self.session.execute(
            select(Device, active_sessions.label("active_refresh_session_count"))
            .outerjoin(RefreshSession, RefreshSession.device_id == Device.id)
            .where(Device.user_id == auth.user_id)
            .group_by(Device.id)
            .order_by(Device.registered_at.desc())
        )

        devices = [
            DeviceListItemResponse(
                id=device.id,
                device_label=device.device_label,
                platform=device.platform,
                app_version=device.app_version,
                registered_at=device.registered_at,
                last_seen_at=device.last_seen_at,
                revoked_at=device.revoked_at,
                active_refresh_session_count=int(active_count or 0),
                is_current_device=device.id == auth.device_id,
            )
            for device, active_count in result.all()
        ]
        return DeviceListResponse(devices=devices)

    async def revoke_device(
        self,
        *,
        auth: AuthenticatedRequestContext,
        device_id: uuid.UUID,
    ) -> DeviceRevokeResponse:
        now = self._now()

        async with self._transaction():
            device = await self.session.scalar(
                select(Device).where(Device.id == device_id, Device.user_id == auth.user_id)
            )
            if device is None:
                raise ApiError(
                    status_code=404,
                    code="device_not_found",
                    message="Device was not found.",
                    category="device",
                )

            revoked_session_count = 0
            if device.revoked_at is None:
                device.revoked_at = now
                revoke_result = await self.session.execute(
                    update(RefreshSession)
                    .where(
                        RefreshSession.device_id == device.id,
                        RefreshSession.revoked_at.is_(None),
                    )
                    .values(revoked_at=now)
                )
                revoked_session_count = int(revoke_result.rowcount or 0)

            await self.session.flush()

        return DeviceRevokeResponse(
            id=device.id,
            revoked_at=device.revoked_at or now,
            revoked_refresh_session_count=revoked_session_count,
        )

    async def get_authenticated_context(
        self,
        *,
        user_id: uuid.UUID,
        device_id: uuid.UUID,
        session_id: uuid.UUID,
    ) -> AuthenticatedRequestContext:
        result: Result[tuple[User, Device, RefreshSession]] = await self.session.execute(
            select(User, Device, RefreshSession)
            .join(Device, Device.user_id == User.id)
            .join(RefreshSession, RefreshSession.device_id == Device.id)
            .where(
                User.id == user_id,
                Device.id == device_id,
                RefreshSession.id == session_id,
            )
        )
        row = result.one_or_none()
        if row is None:
            raise ApiError(
                status_code=401,
                code="invalid_access_token",
                message="Access token is invalid.",
                category="auth",
            )

        user, device, refresh_session = row
        now = self._now()
        if (
            not user.is_active
            or device.revoked_at is not None
            or refresh_session.revoked_at is not None
            or refresh_session.expires_at <= now
        ):
            raise ApiError(
                status_code=401,
                code="invalid_access_token",
                message="Access token is invalid.",
                category="auth",
            )

        return AuthenticatedRequestContext(
            user_id=user.id,
            device_id=device.id,
            refresh_session_id=refresh_session.id,
        )

    async def _resolve_device_binding(
        self,
        *,
        user_id: uuid.UUID,
        binding: DeviceBindingInput,
        now: datetime,
    ) -> Device:
        existing_device = None
        if binding.device_id is not None:
            existing_device = await self.session.scalar(
                select(Device).where(Device.id == binding.device_id, Device.user_id == user_id)
            )

        if existing_device is not None and existing_device.revoked_at is None:
            existing_device.device_label = binding.device_label.strip()
            existing_device.platform = binding.platform.strip()
            existing_device.app_version = self._normalize_optional_text(binding.app_version)
            existing_device.last_seen_at = now
            return existing_device

        device = Device(
            user_id=user_id,
            device_label=binding.device_label.strip(),
            platform=binding.platform.strip(),
            app_version=self._normalize_optional_text(binding.app_version),
            last_seen_at=now,
        )
        self.session.add(device)
        await self.session.flush()
        return device

    async def _create_refresh_session(
        self,
        *,
        device_id: uuid.UUID,
        now: datetime,
        client_ip: str,
        user_agent: str | None,
    ) -> tuple[RefreshSession, str]:
        refresh_token = self.security_manager.create_refresh_token()
        refresh_session = RefreshSession(
            device_id=device_id,
            token_hash=self.security_manager.hash_refresh_token(refresh_token),
            issued_at=now,
            expires_at=now + self.security_manager.refresh_token_ttl,
            issued_from_ip=client_ip or None,
            user_agent=user_agent,
        )
        self.session.add(refresh_session)
        await self.session.flush()
        return refresh_session, refresh_token

    def _build_token_bundle(
        self,
        *,
        user: User,
        device: Device,
        refresh_session: RefreshSession,
        refresh_token: str,
    ) -> TokenBundleResponse:
        access_token, expires_in = self.security_manager.create_access_token(
            user_id=user.id,
            device_id=device.id,
            session_id=refresh_session.id,
        )
        return TokenBundleResponse(
            access_token=access_token,
            refresh_token=refresh_token,
            expires_in_seconds=expires_in,
            refresh_session_id=refresh_session.id,
            user=AuthenticatedUserResponse.model_validate(user),
            device=DeviceSessionResponse.model_validate(device),
        )

    def _assert_password_policy(self, password: str) -> None:
        if len(password) < 8:
            raise ApiError(
                status_code=422,
                code="weak_password",
                message="Password must be at least 8 characters long.",
                category="auth",
            )

    def _ensure_register_allowed(self, client_ip: str) -> None:
        self._ensure_allowed(
            self._register_ip_key(client_ip),
            self.security_settings.failed_register_limit_per_ip,
        )

    def _ensure_allowed(self, key: str, limit: int) -> None:
        result = self.rate_limiter.ensure_allowed(key, limit)
        if result is None:
            return
        raise ApiError(
            status_code=429,
            code="rate_limit_exceeded",
            message="Too many failed authentication attempts. Try again later.",
            details={"retry_after_seconds": result.retry_after_seconds},
            headers={"Retry-After": str(result.retry_after_seconds)},
            category="auth",
            retryable=True,
        )

    def _record_register_failure(self, client_ip: str) -> None:
        self.rate_limiter.record_failure(self._register_ip_key(client_ip))

    def _record_login_failure(self, identity_key: str, ip_key: str) -> None:
        self.rate_limiter.record_failure(identity_key)
        self.rate_limiter.record_failure(ip_key)

    def _record_refresh_failure(self, ip_key: str) -> None:
        self.rate_limiter.record_failure(ip_key)

    @staticmethod
    def _normalize_email(email: str) -> str:
        normalized = email.strip().lower()
        if "@" not in normalized or normalized.startswith("@") or normalized.endswith("@"):
            raise ApiError(
                status_code=422,
                code="invalid_email",
                message="Email format is invalid.",
                category="auth",
            )
        return normalized

    @staticmethod
    def _normalize_display_name(display_name: str | None) -> str | None:
        return AuthService._normalize_optional_text(display_name)

    @staticmethod
    def _normalize_optional_text(value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None

    @staticmethod
    def _now() -> datetime:
        return datetime.now(tz=UTC)

    def _transaction(self) -> AbstractAsyncContextManager[object]:
        if self.session.in_transaction():
            return self.session.begin_nested()
        return self.session.begin()

    @staticmethod
    def _register_ip_key(client_ip: str) -> str:
        return f"register:{client_ip or 'unknown'}"

    @staticmethod
    def _login_identity_key(email: str, client_ip: str) -> str:
        return f"login:identity:{email}:{client_ip or 'unknown'}"

    @staticmethod
    def _login_ip_key(client_ip: str) -> str:
        return f"login:ip:{client_ip or 'unknown'}"

    @staticmethod
    def _refresh_ip_key(client_ip: str) -> str:
        return f"refresh:{client_ip or 'unknown'}"
