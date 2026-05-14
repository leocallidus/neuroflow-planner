from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

from fastapi import FastAPI, Request
from fastapi import HTTPException as FastAPIHTTPException
from fastapi.encoders import jsonable_encoder
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette import status

from app.core.request_context import get_request_id

LOG = logging.getLogger("app.errors")


@dataclass(slots=True)
class ApiError(Exception):
    status_code: int
    code: str
    message: str
    details: dict[str, Any] = field(default_factory=dict)
    headers: dict[str, str] = field(default_factory=dict)
    category: str = "application"
    retryable: bool = False


def _is_retryable_status(status_code: int) -> bool:
    return status_code in {
        status.HTTP_408_REQUEST_TIMEOUT,
        status.HTTP_429_TOO_MANY_REQUESTS,
        status.HTTP_502_BAD_GATEWAY,
        status.HTTP_503_SERVICE_UNAVAILABLE,
        status.HTTP_504_GATEWAY_TIMEOUT,
    }


def _error_payload(
    *,
    status_code: int,
    code: str,
    message: str,
    details: dict[str, Any],
    category: str,
    retryable: bool,
) -> dict[str, Any]:
    return {
        "error": {
            "status": status_code,
            "code": code,
            "message": message,
            "details": details,
            "category": category,
            "retryable": retryable,
            "request_id": get_request_id(),
        }
    }


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def api_error_handler(_: Request, exc: ApiError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content=_error_payload(
                status_code=exc.status_code,
                code=exc.code,
                message=exc.message,
                details=exc.details,
                category=exc.category,
                retryable=exc.retryable,
            ),
            headers={"X-Error-Code": exc.code, "X-Error-Category": exc.category, **exc.headers},
        )

    @app.exception_handler(FastAPIHTTPException)
    async def http_exception_handler(_: Request, exc: FastAPIHTTPException) -> JSONResponse:
        detail = exc.detail if isinstance(exc.detail, dict) else {"message": str(exc.detail)}
        code = detail.get("code", "http_error")
        category = detail.get("category")
        if not category:
            if exc.status_code == status.HTTP_401_UNAUTHORIZED:
                category = "auth"
            elif exc.status_code == status.HTTP_422_UNPROCESSABLE_CONTENT:
                category = "validation"
            elif exc.status_code >= 500:
                category = "internal"
            else:
                category = "http"
        retryable = bool(detail.get("retryable", _is_retryable_status(exc.status_code)))
        return JSONResponse(
            status_code=exc.status_code,
            content=_error_payload(
                status_code=exc.status_code,
                code=code,
                message=detail.get("message", str(exc.detail)),
                details=detail.get("details", {}),
                category=category,
                retryable=retryable,
            ),
            headers={"X-Error-Code": code, "X-Error-Category": category, **(exc.headers or {})},
        )

    @app.exception_handler(RequestValidationError)
    async def validation_error_handler(_: Request, exc: RequestValidationError) -> JSONResponse:
        serialized_errors = jsonable_encoder(exc.errors())
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            content=_error_payload(
                status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
                code="validation_error",
                message="Request validation failed.",
                details={"errors": serialized_errors},
                category="validation",
                retryable=False,
            ),
            headers={"X-Error-Code": "validation_error", "X-Error-Category": "validation"},
        )

    @app.exception_handler(Exception)
    async def unhandled_exception_handler(_: Request, exc: Exception) -> JSONResponse:
        LOG.exception("unhandled_exception", extra={"request_id": get_request_id()})
        return JSONResponse(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            content=_error_payload(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                code="internal_server_error",
                message="An unexpected server error occurred.",
                details={},
                category="internal",
                retryable=False,
            ),
            headers={
                "X-Error-Code": "internal_server_error",
                "X-Error-Category": "internal",
            },
        )
