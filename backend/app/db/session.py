from __future__ import annotations

from dataclasses import dataclass

from sqlalchemy import text
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.core.config import DatabaseSettings
from app.db import Base

SCHEMA_FOUNDATION_REVISION = "20260323_01"


@dataclass(slots=True)
class DatabaseSchemaMismatchError(RuntimeError):
    missing_tables: tuple[str, ...]
    missing_columns: tuple[str, ...]
    alembic_version_present: bool

    def __str__(self) -> str:
        parts = ["Database schema check failed."]
        if self.missing_tables:
            parts.append(f"Missing tables: {', '.join(self.missing_tables)}.")
        if self.missing_columns:
            parts.append(f"Missing columns: {', '.join(self.missing_columns)}.")
        if not self.alembic_version_present:
            parts.append("Missing alembic_version table.")
        parts.append("Run `uv run alembic current` to inspect the current revision.")
        if not self.alembic_version_present:
            parts.append(
                "If this is an existing Stage 3 database created before Alembic tracking, "
                f"run `uv run alembic stamp {SCHEMA_FOUNDATION_REVISION}` and then "
                "`uv run alembic upgrade head`."
            )
        else:
            parts.append("Run `uv run alembic upgrade head`.")
        return " ".join(parts)


@dataclass(slots=True)
class DatabaseRuntime:
    engine: AsyncEngine
    session_factory: async_sessionmaker[AsyncSession]

    async def ping(self) -> None:
        async with self.engine.connect() as connection:
            await connection.execute(text("SELECT 1"))
            await self._verify_schema(connection)

    async def _verify_schema(self, connection) -> None:
        existing_tables = {
            row[0]
            for row in (
                await connection.execute(
                    text(
                        """
                        SELECT tablename
                        FROM pg_tables
                        WHERE schemaname = 'public'
                        """
                    )
                )
            )
        }
        existing_columns_by_table: dict[str, set[str]] = {}
        for table_name, column_name in (
            await connection.execute(
                text(
                    """
                    SELECT table_name, column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                    """
                )
            )
        ):
            existing_columns_by_table.setdefault(table_name, set()).add(column_name)

        required_tables = set(Base.metadata.tables.keys())
        missing_tables = tuple(sorted(required_tables - existing_tables))
        missing_columns = tuple(
            sorted(
                f"{table_name}.{column_name}"
                for table_name, table in Base.metadata.tables.items()
                if table_name in existing_tables
                for column_name in table.columns.keys()
                if column_name not in existing_columns_by_table.get(table_name, set())
            )
        )
        alembic_version_present = "alembic_version" in existing_tables

        if missing_tables or missing_columns or not alembic_version_present:
            raise DatabaseSchemaMismatchError(
                missing_tables=missing_tables,
                missing_columns=missing_columns,
                alembic_version_present=alembic_version_present,
            )

    async def close(self) -> None:
        await self.engine.dispose()


def build_database_runtime(settings: DatabaseSettings) -> DatabaseRuntime:
    engine = create_async_engine(
        settings.url,
        echo=settings.echo,
        pool_pre_ping=True,
        pool_size=settings.pool_size,
        max_overflow=settings.max_overflow,
        connect_args={"timeout": settings.connect_timeout_seconds},
    )
    session_factory = async_sessionmaker(engine, expire_on_commit=False)
    return DatabaseRuntime(engine=engine, session_factory=session_factory)
