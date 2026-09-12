-- Phản hồi khảo sát người dùng (EXE201 OC3: ≥20 phản hồi).
-- NPS 0–10 + câu hỏi ngắn; có thể gửi ẩn danh hoặc kèm user_id khi đã đăng nhập.

CREATE TABLE IF NOT EXISTS user_feedback (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    source          VARCHAR(40) NOT NULL,
    nps             SMALLINT NOT NULL CHECK (nps >= 0 AND nps <= 10),
    rating          SMALLINT CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
    comment         TEXT,
    utm_source      VARCHAR(100),
    utm_medium      VARCHAR(100),
    utm_campaign    VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_user_feedback_created ON user_feedback (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_user_feedback_source ON user_feedback (source);

-- Sự kiện marketing nhẹ (CTA clicks, UTM land) — phục vụ CAP / OC2.
CREATE TABLE IF NOT EXISTS marketing_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    event_name      VARCHAR(80) NOT NULL,
    path            VARCHAR(500),
    utm_source      VARCHAR(100),
    utm_medium      VARCHAR(100),
    utm_campaign    VARCHAR(100),
    metadata        JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_marketing_events_name_created
    ON marketing_events (event_name, created_at DESC);
