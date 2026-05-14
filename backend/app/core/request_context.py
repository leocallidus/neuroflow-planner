from __future__ import annotations

from contextvars import ContextVar, Token
import logging

REQUEST_ID_CTX: ContextVar[str] = ContextVar("request_id", default="-")


def set_request_id(request_id: str) -> Token[str]:
    return REQUEST_ID_CTX.set(request_id)


def get_request_id() -> str:
    return REQUEST_ID_CTX.get()


def clear_request_id(token: Token[str]) -> None:
    REQUEST_ID_CTX.reset(token)


class RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = get_request_id()
        return True
