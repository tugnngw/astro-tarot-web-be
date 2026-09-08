-- AI Usage Tracking Table
-- V1_4__ai_usage_logs.sql

CREATE TABLE ai_usage_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    reading_id UUID NULL,
    chat_session_id UUID NULL,
    provider VARCHAR(50) NOT NULL,
    model VARCHAR(100) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    estimated_cost_usd NUMERIC(10, 6),
    latency_ms INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Add foreign key constraints
ALTER TABLE ai_usage_logs
    ADD CONSTRAINT fk_ai_usage_logs_user
    FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ai_usage_logs
    ADD CONSTRAINT fk_ai_usage_logs_reading
    FOREIGN KEY (reading_id) REFERENCES tarot_readings(id);

ALTER TABLE ai_usage_logs
    ADD CONSTRAINT fk_ai_usage_logs_chat_session
    FOREIGN KEY (chat_session_id) REFERENCES chat_sessions(id);

-- Add indexes for performance
CREATE INDEX idx_ai_usage_logs_user_id ON ai_usage_logs(user_id);
CREATE INDEX idx_ai_usage_logs_reading_id ON ai_usage_logs(reading_id);
CREATE INDEX idx_ai_usage_logs_chat_session_id ON ai_usage_logs(chat_session_id);
CREATE INDEX idx_ai_usage_logs_created_at ON ai_usage_logs(created_at);
CREATE INDEX idx_ai_usage_logs_provider_model ON ai_usage_logs(provider, model);