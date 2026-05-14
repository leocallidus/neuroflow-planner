FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN pip install --no-cache-dir uv

WORKDIR /app

COPY pyproject.toml uv.lock* README.md ./
RUN uv sync --frozen --no-dev

COPY app ./app

EXPOSE 8000

CMD ["uv", "run", "neuroflow-sync-api"]
