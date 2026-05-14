ALTER TABLE chat_context_state ADD COLUMN last_context_window_tokens INTEGER;
ALTER TABLE chat_context_state ADD COLUMN last_estimated_usage_tokens INTEGER;
ALTER TABLE chat_context_state ADD COLUMN last_reserved_completion_tokens INTEGER;
ALTER TABLE chat_context_state ADD COLUMN last_summarize_at TEXT NOT NULL DEFAULT '';
ALTER TABLE chat_context_state ADD COLUMN last_summarize_status TEXT NOT NULL DEFAULT '';
ALTER TABLE chat_context_state ADD COLUMN active_summary_revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE chat_context_state ADD COLUMN last_budget_severity TEXT NOT NULL DEFAULT '';
ALTER TABLE chat_context_state ADD COLUMN last_usage_ratio REAL;
