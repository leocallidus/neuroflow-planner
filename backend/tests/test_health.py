from __future__ import annotations

from tests.test_utils import StubDatabaseRuntime, build_test_app, make_api_client


async def test_live_endpoint_returns_service_metadata() -> None:
    app = build_test_app()

    async with make_api_client(app) as client:
        response = await client.get("/health/live")

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "alive"
    assert payload["service"] == "NeuroFlow Test Backend"
    assert payload["environment"] == "test"


async def test_ready_endpoint_returns_200_when_database_is_reachable() -> None:
    app = build_test_app()

    async with make_api_client(app) as client:
        response = await client.get("/health/ready")

    assert response.status_code == 200
    payload = response.json()
    assert payload["status"] == "ready"
    assert payload["database"]["status"] == "ready"


async def test_ready_endpoint_returns_503_when_database_is_unreachable() -> None:
    app = build_test_app(database_runtime=StubDatabaseRuntime(should_fail=True))

    async with make_api_client(app) as client:
        response = await client.get("/health/ready")

    assert response.status_code == 503
    payload = response.json()
    assert payload["status"] == "not_ready"
    assert payload["database"]["status"] == "not_ready"


async def test_ready_endpoint_returns_schema_mismatch_reason() -> None:
    app = build_test_app(
        database_runtime=StubDatabaseRuntime(
            should_fail=True,
            failure_message="Database schema check failed. Missing tables: sync_push_receipts.",
        )
    )

    async with make_api_client(app) as client:
        response = await client.get("/health/ready")

    assert response.status_code == 503
    payload = response.json()
    assert payload["status"] == "not_ready"
    assert payload["database"]["status"] == "not_ready"
    assert payload["database"]["details"]["reason"] == (
        "Database schema check failed. Missing tables: sync_push_receipts."
    )
