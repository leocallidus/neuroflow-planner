from __future__ import annotations

import uuid

from app.core.config import SecuritySettings
from app.core.security import AuthFailureRateLimiter, SecurityManager


def make_security_settings() -> SecuritySettings:
    return SecuritySettings(
        jwt_secret="test-secret-key-with-at-least-thirty-two-bytes",
        refresh_token_pepper="test-pepper",
        access_token_ttl_minutes=15,
        refresh_token_ttl_days=7,
        failed_login_limit_per_identity=2,
        failed_login_limit_per_ip=2,
        failed_register_limit_per_ip=2,
        failed_refresh_limit_per_ip=2,
        auth_failure_window_seconds=60,
    )


def test_password_hashing_roundtrip() -> None:
    security = SecurityManager(make_security_settings())

    password_hash = security.hash_password("correct horse battery staple")

    assert password_hash != "correct horse battery staple"
    assert security.verify_password("correct horse battery staple", password_hash) is True
    assert security.verify_password("wrong password", password_hash) is False


def test_access_token_roundtrip() -> None:
    security = SecurityManager(make_security_settings())
    user_id = uuid.uuid4()
    device_id = uuid.uuid4()
    session_id = uuid.uuid4()

    token, expires_in = security.create_access_token(
        user_id=user_id,
        device_id=device_id,
        session_id=session_id,
    )
    claims = security.decode_access_token(token)

    assert expires_in == 900
    assert claims.user_id == user_id
    assert claims.device_id == device_id
    assert claims.session_id == session_id


def test_auth_failure_rate_limiter_blocks_after_limit() -> None:
    settings = make_security_settings()
    limiter = AuthFailureRateLimiter(settings)
    key = "login:test@example.com:127.0.0.1"

    assert limiter.ensure_allowed(key, settings.failed_login_limit_per_identity) is None
    limiter.record_failure(key)
    assert limiter.ensure_allowed(key, settings.failed_login_limit_per_identity) is None
    limiter.record_failure(key)

    limited = limiter.ensure_allowed(key, settings.failed_login_limit_per_identity)
    assert limited is not None
    assert limited.retry_after_seconds > 0

    limiter.clear(key)
    assert limiter.ensure_allowed(key, settings.failed_login_limit_per_identity) is None
