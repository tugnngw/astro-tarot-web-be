-- V2_18: AI Subscription System
-- Tạo bảng: subscription_plan, user_plan_purchase, ai_usage_daily, plan_change_audit_log

-- ============================================================
-- 1. subscription_plan - Danh mục gói AI
-- ============================================================
CREATE TABLE subscription_plan (
    id UUID PRIMARY KEY,
    plan_type VARCHAR(20) NOT NULL CHECK (plan_type IN ('MONTHLY', 'DAY_PASS', 'FREE')),
    name VARCHAR(255) NOT NULL,
    daily_quota INT NOT NULL,
    price BIGINT NOT NULL,
    duration_days INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. user_plan_purchase - Lượt mua thực tế (snapshot)
-- ============================================================
CREATE TABLE user_plan_purchase (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    plan_id UUID NOT NULL,
    plan_name_snapshot VARCHAR(255) NOT NULL,
    daily_quota_snapshot INT NOT NULL,
    price_snapshot BIGINT NOT NULL,
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'EXPIRED', 'SUPERSEDED', 'CANCELLED')),
    purchase_type VARCHAR(20) NOT NULL CHECK (purchase_type IN ('STRIPE', 'PAYOS', 'WALLET', 'MANUAL')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_purchase_plan FOREIGN KEY (plan_id) REFERENCES subscription_plan(id)
);

-- ============================================================
-- 3. ai_usage_daily - Đếm số lượt dùng AI theo ngày
-- ============================================================
CREATE TABLE ai_usage_daily (
    user_id UUID NOT NULL,
    usage_date DATE NOT NULL,
    count_used INT DEFAULT 0,
    CONSTRAINT pk_ai_usage_daily PRIMARY KEY (user_id, usage_date),
    CONSTRAINT fk_usage_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================================
-- 4. plan_change_audit_log - Ghi log admin actions
-- ============================================================
CREATE TABLE plan_change_audit_log (
    id UUID PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('PLAN', 'USER_PURCHASE')),
    target_id UUID NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by UUID NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (changed_by) REFERENCES users(id)
);

-- ============================================================
-- Indexes
-- ============================================================
CREATE INDEX idx_subscription_plan_active ON subscription_plan(is_active);
CREATE INDEX idx_subscription_plan_type ON subscription_plan(plan_type);
CREATE INDEX idx_user_plan_purchase_user ON user_plan_purchase(user_id, status);
CREATE INDEX idx_user_plan_purchase_user_date ON user_plan_purchase(user_id, start_at, end_at);
CREATE INDEX idx_user_plan_purchase_status ON user_plan_purchase(status);
CREATE INDEX idx_ai_usage_daily_date ON ai_usage_daily(usage_date);
CREATE INDEX idx_plan_change_audit_target ON plan_change_audit_log(target_type, target_id);
CREATE INDEX idx_plan_change_audit_user ON plan_change_audit_log(changed_by);

-- ============================================================
-- Seed data: Free tier (always 1 active)
-- ============================================================
INSERT INTO subscription_plan (id, plan_type, name, daily_quota, price, duration_days, is_active, description)
VALUES (
    'a0000000-0000-4000-8000-000000000001',
    'FREE',
    'Free',
    3,
    0,
    36500,
    TRUE,
    'Gói miễn phí: 3 lượt AI mỗi ngày'
);