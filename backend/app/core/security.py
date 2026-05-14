from __future__ import annotations

import base64
import hashlib
import hmac
import secrets
import uuid
from collections import defaultdict, deque
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from time import monotonic
from typing import Any

import jwt
from jwt import InvalidTokenError

from app.core.config import SecuritySettings


@dataclass(slots=True)
class AccessTokenClaims:
    user_id: uuid.UUID
    device_id: uuid.UUID
    session_id: uuid.UUID


@dataclass(slots=True)
class RateLimitResult:
    retry_after_seconds: int


class SecurityManager:
    def __init__(self, settings: SecuritySettings) -> None:
        self._settings = settings

    @property
    def access_token_ttl(self) -> timedelta:
        return timedelta(minutes=self._settings.access_token_ttl_minutes)

    @property
    def refresh_token_ttl(self) -> timedelta:
        return timedelta(days=self._settings.refresh_token_ttl_days)

    def hash_password(self, password: str) -> str:
        salt = secrets.token_bytes(16)
        derived = hashlib.scrypt(
            password.encode("utf-8"),
            salt=salt,
            n=self._settings.password_scrypt_n,
            r=self._settings.password_scrypt_r,
            p=self._settings.password_scrypt_p,
            dklen=self._settings.password_scrypt_dklen,
        )
        encoded_salt = base64.urlsafe_b64encode(salt).decode("ascii")
        encoded_hash = base64.urlsafe_b64encode(derived).decode("ascii")
        return (
            f"scrypt${self._settings.password_scrypt_n}${self._settings.password_scrypt_r}"
            f"${self._settings.password_scrypt_p}${self._settings.password_scrypt_dklen}"
            f"${encoded_salt}${encoded_hash}"
        )

    def verify_password(self, password: str, stored_hash: str) -> bool:
        try:
            algorithm, n, r, p, dklen, encoded_salt, encoded_hash = stored_hash.split(
                "$", maxsplit=6
            )
        except ValueError:
            return False

        if algorithm != "scrypt":
            return False

        salt = self._decode_b64(encoded_salt)
        expected = self._decode_b64(encoded_hash)
        if salt is None or expected is None:
            return False

        candidate = hashlib.scrypt(
            password.encode("utf-8"),
            salt=salt,
            n=int(n),
            r=int(r),
            p=int(p),
            dklen=int(dklen),
        )
        return hmac.compare_digest(candidate, expected)

    def create_access_token(
        self,
        *,
        user_id: uuid.UUID,
        device_id: uuid.UUID,
        session_id: uuid.UUID,
    ) -> tuple[str, int]:
        now = datetime.now(tz=UTC)
        expires_at = now + self.access_token_ttl
        payload = {
            "sub": str(user_id),
            "typ": "access",
            "did": str(device_id),
            "sid": str(session_id),
            "iat": now,
            "exp": expires_at,
            "iss": self._settings.jwt_issuer,
            "aud": self._settings.jwt_audience,
        }
        token = jwt.encode(
            payload, self._settings.jwt_secret, algorithm=self._settings.jwt_algorithm
        )
        return token, int(self.access_token_ttl.total_seconds())

    def decode_access_token(self, token: str) -> AccessTokenClaims:
        payload = jwt.decode(
            token,
            self._settings.jwt_secret,
            algorithms=[self._settings.jwt_algorithm],
            audience=self._settings.jwt_audience,
            issuer=self._settings.jwt_issuer,
            options={"require": ["sub", "exp", "iat", "typ", "did", "sid"]},
        )
        if payload.get("typ") != "access":
            raise InvalidTokenError("Unsupported token type.")
        return AccessTokenClaims(
            user_id=uuid.UUID(str(payload["sub"])),
            device_id=uuid.UUID(str(payload["did"])),
            session_id=uuid.UUID(str(payload["sid"])),
        )

    def create_refresh_token(self) -> str:
        return secrets.token_urlsafe(self._settings.refresh_token_bytes)

    def hash_refresh_token(self, refresh_token: str) -> str:
        digest = hashlib.sha256(
            f"{self._settings.refresh_token_pepper}:{refresh_token}".encode()
        ).hexdigest()
        return digest

    @staticmethod
    def _decode_b64(value: str) -> bytes | None:
        padding = "=" * (-len(value) % 4)
        try:
            return base64.urlsafe_b64decode(f"{value}{padding}".encode("ascii"))
        except Exception:
            return None


class AuthFailureRateLimiter:
    def __init__(self, settings: SecuritySettings) -> None:
        self._settings = settings
        self._failures: dict[str, deque[float]] = defaultdict(deque)

    def ensure_allowed(self, key: str, limit: int) -> RateLimitResult | None:
        now = monotonic()
        attempts = self._failures[key]
        self._prune(attempts, now)
        if len(attempts) < limit:
            return None
        retry_after_seconds = max(
            1,
            int(self._settings.auth_failure_window_seconds - (now - attempts[0])),
        )
        return RateLimitResult(retry_after_seconds=retry_after_seconds)

    def record_failure(self, key: str) -> None:
        now = monotonic()
        attempts = self._failures[key]
        self._prune(attempts, now)
        attempts.append(now)

    def clear(self, key: str) -> None:
        self._failures.pop(key, None)

    def _prune(self, attempts: deque[float], now: float) -> None:
        while attempts and now - attempts[0] >= self._settings.auth_failure_window_seconds:
            attempts.popleft()


def sanitize_jwt_error(exc: Exception) -> dict[str, Any]:
    if isinstance(exc, InvalidTokenError):
        return {"reason": exc.__class__.__name__}
    return {"reason": exc.__class__.__name__}
