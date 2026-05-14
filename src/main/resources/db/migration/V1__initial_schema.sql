-- Baseline schema for NeuroFlow Planner (SQLite).

CREATE TABLE tasks (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    deadline TEXT NOT NULL,
    complexity INTEGER NOT NULL,
    smart_priority REAL DEFAULT 0,
    ai_insight TEXT,
    parent_id TEXT,
    tags TEXT DEFAULT '',
    recurrence TEXT DEFAULT '',
    depends_on TEXT DEFAULT '',
    archived INTEGER DEFAULT 0,
    tracked_minutes INTEGER DEFAULT 0,
    start_date TEXT,
    completed INTEGER DEFAULT 0,
    completed_date TEXT,
    FOREIGN KEY (parent_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE TABLE task_templates (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    complexity INTEGER NOT NULL,
    days_until_deadline INTEGER DEFAULT 7,
    tags TEXT DEFAULT ''
);

CREATE TABLE mood_entries (
    id TEXT PRIMARY KEY,
    timestamp TEXT NOT NULL,
    score INTEGER NOT NULL,
    note TEXT,
    analysis TEXT
);

CREATE TABLE chat_conversations (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE chat_messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    seq INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES chat_conversations(id) ON DELETE CASCADE
);

CREATE TABLE time_sessions (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    started_at TEXT NOT NULL,
    minutes INTEGER NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

CREATE TABLE goals (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    period TEXT NOT NULL,
    target INTEGER NOT NULL,
    progress INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX idx_chat_messages_conv ON chat_messages(conversation_id, seq);
CREATE INDEX idx_time_sessions_started ON time_sessions(started_at);
