from __future__ import annotations

import json
import logging
from datetime import UTC, datetime
from typing import Any

from app.core.config import LoggingSettings
from app.core.request_context import RequestIdFilter

STANDARD_LOG_KEYS = {
    "args",
    "asctime",
    "created",
    "exc_info",
    "exc_text",
    "filename",
    "funcName",
    "levelname",
    "levelno",
    "lineno",
    "module",
    "msecs",
    "message",
    "msg",
    "name",
    "pathname",
    "process",
    "processName",
    "relativeCreated",
    "stack_info",
    "thread",
    "threadName",
}


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": datetime.now(tz=UTC).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "request_id": getattr(record, "request_id", "-"),
        }
        extras = {
            key: value
            for key, value in record.__dict__.items()
            if key not in STANDARD_LOG_KEYS and not key.startswith("_")
        }
        if extras:
            payload["context"] = extras
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload, ensure_ascii=False, default=str)


class PlainFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        timestamp = datetime.now(tz=UTC).isoformat()
        request_id = getattr(record, "request_id", "-")
        message = f"{timestamp} [{record.levelname}] {record.name} request_id={request_id} {record.getMessage()}"
        extras = {
            key: value
            for key, value in record.__dict__.items()
            if key not in STANDARD_LOG_KEYS and not key.startswith("_")
        }
        if extras:
            message = f"{message} | {extras}"
        if record.exc_info:
            message = f"{message}\n{self.formatException(record.exc_info)}"
        return message


def configure_logging(settings: LoggingSettings) -> None:
    root_logger = logging.getLogger()
    formatter: logging.Formatter = JsonFormatter() if settings.json_logs else PlainFormatter()

    handler = logging.StreamHandler()
    handler.setFormatter(formatter)
    handler.addFilter(RequestIdFilter())

    root_logger.handlers.clear()
    root_logger.addHandler(handler)
    root_logger.setLevel(settings.level.upper())

    logging.getLogger("uvicorn.access").handlers.clear()
    logging.getLogger("uvicorn.access").propagate = True
