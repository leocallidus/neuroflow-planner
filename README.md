# NeuroFlow Planner

NeuroFlow Planner - настольный планировщик задач с AI-ассистентом, локальным хранением данных и опциональной облачной синхронизацией. Приложение ориентировано на личное планирование, работу с заметками, анализ нагрузки и более осознанное распределение задач по времени.

Основной клиент написан на JavaFX и работает локально с SQLite. Отдельный backend на FastAPI используется только для аккаунтов, устройств и синхронизации данных между установками.

## Возможности

- Создание, редактирование и организация задач с приоритетами, сроками, сложностью, категориями и зависимостями.
- Несколько рабочих представлений: список задач, календарь, Kanban, диаграмма Ганта, статистика, тепловые карты, прогресс проектов и нагрузка.
- AI-ассистент для диалога по планированию, анализа задач, автозаполнения, рекомендаций и работы с контекстом.
- Поддержка AI-режимов: `offline`, локальный Ollama и внешний OpenAI-compatible API.
- Умные заметки с отдельным экраном, сохранением и экспортом.
- Рекомендации по фокус-блокам, ежедневный обзор, анализ качества планирования и персональные инсайты.
- Локальная база SQLite с миграциями Flyway.
- Импорт и экспорт данных, включая Excel/PDF-сценарии.
- Опциональная облачная синхронизация через отдельный FastAPI backend.
- Настраиваемая тема, адаптивная компоновка, боковая навигация, палитра команд и горячие клавиши.

## Скриншоты

### Стартовый экран

![Стартовое окно NeuroFlow Planner](<screenshots/Стартовое окно NeuroFlow Planner после запуска приложения.png>)

### Первичная настройка

![Окно первичной настройки профиля и AI-режима](<screenshots/Окно WelcomeDialog с первичной настройкой профиля и AI-режима.png>)

### Главное окно

![Главное окно NeuroFlow Planner](<screenshots/Главное окно NeuroFlow Planner с основными областями интерфейса.png>)

### Создание и редактирование задачи

![Окно создания или редактирования задачи](<screenshots/Окно создания или редактирования задачи.png>)

### AI-ассистент

![Окно AI-ассистента](<screenshots/Окно AI-ассистента .png>)

### Заметки

![Экран заметок](<screenshots/Экран заметок.png>)

### Облачная синхронизация

![Настройки облачной синхронизации и диагностики](<screenshots/Раздел настроек облачной синхронизации и диагностики.png>)

## Технологии

- Java 21
- JavaFX 21
- Maven Wrapper
- SQLite JDBC
- Flyway
- Jackson и JSON Schema Validator
- SLF4J и Logback
- Apache POI и iText
- JUnit 5, TestFX, Mockito, ArchUnit
- FastAPI backend для облачной синхронизации
- PostgreSQL для backend-сервиса синхронизации
- uv для Python-зависимостей backend-сервиса

## Требования

Для desktop-приложения:

- JDK 21
- Maven Wrapper из репозитория: `mvnw` или `mvnw.cmd`

Для backend-синхронизации:

- Python 3.12+
- uv
- Docker или Podman с compose-поддержкой

## Быстрый запуск desktop-приложения

Windows:

```powershell
.\mvnw.cmd javafx:run
```

Linux/macOS:

```bash
./mvnw javafx:run
```

При первом запуске приложение создаст локальную директорию `neuroflow_data/` в рабочем каталоге или рядом с JAR-файлом. В ней хранятся база данных, пользовательская конфигурация, сгенерированные изображения и вложения чата.

## Конфигурация

Пример пользовательских настроек находится в `config.example.properties`. Рабочая конфигурация создается как `neuroflow_data/config.properties`.

Минимальная локальная конфигурация по умолчанию использует offline-режим:

```properties
ai.mode=offline
```

Для локальной модели через Ollama:

```properties
ai.mode=local
local.ollama.baseUrl=http://localhost:11434
local.ollama.model=llama3
```

Для внешнего OpenAI-compatible API:

```properties
ai.mode=external
external.api.baseUrl=https://api.example.com/v1
external.api.model=your-model
```

API-ключи не стоит хранить в репозитории. Приложение поддерживает переменные окружения и системное хранилище секретов; примерный файл конфигурации оставляет ключи пустыми.

## Облачная синхронизация

Backend находится в каталоге `backend/`. Он обслуживает регистрацию, вход, устройства, bootstrap/pull/push-синхронизацию и технические health/metrics endpoints.

Локальный запуск backend:

```bash
cd backend
cp .env.dev.example .env
docker compose up -d postgres
uv sync --group dev
uv run alembic upgrade head
uv run neuroflow-sync-api
```

API по умолчанию будет доступен на `http://127.0.0.1:8000`.

Чтобы подключить desktop-клиент к локальному backend, укажите в конфигурации:

```properties
cloud.sync.enabled=true
cloud.sync.baseUrl=http://127.0.0.1:8000
```

## Тесты и проверки

Запуск Java-тестов:

```bash
./mvnw test
```

Запуск backend-тестов:

```bash
cd backend
uv sync --group dev
uv run pytest
```

Проверка миграций Flyway:

```bash
./mvnw flyway:migrate -Dflyway.url=jdbc:sqlite:target/local-flyway.db
./mvnw flyway:validate -Dflyway.url=jdbc:sqlite:target/local-flyway.db
```

## Сборка

Собрать desktop-проект:

```bash
./mvnw package
```

После сборки Maven создаст артефакты в `target/`. Файл `dependency-reduced-pom.xml`, если он появляется после shade-сборки, считается локальным build-артефактом.

## Структура проекта

```text
.
├── backend/                 # FastAPI backend для облачной синхронизации
├── screenshots/             # Скриншоты интерфейса для README
├── src/main/java/           # JavaFX desktop-приложение
├── src/main/resources/      # CSS, изображения, конфигурация и Flyway-миграции
├── src/test/                # Java-тесты
├── tests/                   # Дополнительные тестовые артефакты
├── config.example.properties
├── pom.xml
└── README.md
```

## Данные и безопасность

- `neuroflow_data/` содержит пользовательскую базу, конфигурацию и локальные вложения. Этот каталог не должен попадать в Git.
- `config.properties`, `.env`, API-ключи, refresh-токены и локальные базы данных не коммитятся.
- Примерные файлы `config.example.properties`, `backend/.env.example`, `backend/.env.dev.example` и `backend/.env.prod.example` предназначены для документации конфигурации и могут храниться в репозитории.

## CI

В GitHub Actions настроены проверки Java-проекта, Flyway-миграций, архитектурных ограничений UI/AI-слоев и regression-suite backend-сервиса. Основной workflow находится в `.github/workflows/ci.yml`.
