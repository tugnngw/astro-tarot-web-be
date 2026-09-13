
-- ============================================================
-- ASTRO TAROT PLATFORM 
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- USERS
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER','READER','ADMIN')),
    phone VARCHAR(20),
    avatar TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','INACTIVE','BANNED')),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash TEXT NOT NULL UNIQUE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    device_info TEXT,
    ip_address VARCHAR(45),
    expired_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_astrological_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    profile_type VARCHAR(20) NOT NULL DEFAULT 'SELF'
        CHECK(profile_type IN ('SELF','OTHER','COUPLE')),
    title VARCHAR(255) NOT NULL,
    target_name VARCHAR(255),
    birth_date DATE NOT NULL,
    birth_time TIME,
    birth_place VARCHAR(255),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    timezone VARCHAR(50),
    encrypted_data BYTEA NOT NULL,
    encryption_iv BYTEA NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- READER
CREATE TABLE reader_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    bio TEXT,
    specialties TEXT[] DEFAULT '{}',
    years_experience INTEGER DEFAULT 0 CHECK(years_experience >= 0),
    price_per_15m BIGINT CHECK(price_per_15m >= 0),
    price_per_30m BIGINT CHECK(price_per_30m >= 0),
    price_per_60m BIGINT CHECK(price_per_60m >= 0),
    rating DECIMAL(3,2) DEFAULT 0 CHECK(rating BETWEEN 0 AND 5),
    total_reviews INTEGER DEFAULT 0,
    is_available BOOLEAN DEFAULT TRUE,
    verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE reader_availability (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reader_id UUID NOT NULL REFERENCES reader_profiles(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK(day_of_week BETWEEN 0 AND 6),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_avail_time CHECK(end_time > start_time)
);

CREATE TABLE reader_unavailable_dates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reader_id UUID NOT NULL REFERENCES reader_profiles(id) ON DELETE CASCADE,
    unavailable_date DATE NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- BOOKINGS
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    reader_profile_id UUID NOT NULL REFERENCES reader_profiles(id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    total_amount BIGINT NOT NULL CHECK(total_amount >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    cancel_reason TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT chk_booking_time CHECK(end_time > start_time)
);

-- READINGS
CREATE TABLE tarot_readings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    booking_id UUID REFERENCES bookings(id),
    astro_profile_id UUID REFERENCES user_astrological_data(id),
    session_type VARCHAR(20) DEFAULT 'AI',
    main_question TEXT NOT NULL,
    ai_model_used VARCHAR(100),
    total_tokens_used INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE tarot_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) UNIQUE NOT NULL,
    arcana_type VARCHAR(20),
    card_number INTEGER,
    image_url TEXT
);

CREATE TABLE reading_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reading_id UUID NOT NULL REFERENCES tarot_readings(id) ON DELETE CASCADE,
    card_id UUID NOT NULL REFERENCES tarot_cards(id),
    position SMALLINT NOT NULL,
    is_reversed BOOLEAN DEFAULT FALSE,
    interpretation TEXT
);

-- CHAT
CREATE TABLE chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    tarot_reading_id UUID REFERENCES tarot_readings(id) ON DELETE CASCADE,
    booking_id UUID REFERENCES bookings(id),
    session_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    sender_type VARCHAR(20) NOT NULL,
    sender_id UUID,
    content TEXT NOT NULL,
    message_type VARCHAR(20) DEFAULT 'TEXT',
    metadata JSONB,
    is_edited BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- PAYMENT
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID REFERENCES bookings(id),
    user_id UUID NOT NULL REFERENCES users(id),
    amount BIGINT NOT NULL CHECK(amount > 0),
    payment_method VARCHAR(50),
    external_transaction_id VARCHAR(255) UNIQUE,
    status VARCHAR(20) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE escrow_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id),
    balance BIGINT DEFAULT 0,
    pending_balance BIGINT DEFAULT 0,
    total_earned BIGINT DEFAULT 0,
    total_withdrawn BIGINT DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE payout_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reader_id UUID NOT NULL REFERENCES reader_profiles(id),
    amount BIGINT NOT NULL CHECK(amount > 0),
    bank_name VARCHAR(255),
    bank_account VARCHAR(255),
    account_holder VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);

-- COMMUNITY
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID UNIQUE NOT NULL REFERENCES bookings(id),
    user_id UUID NOT NULL REFERENCES users(id),
    reader_profile_id UUID NOT NULL REFERENCES reader_profiles(id),
    rating INTEGER NOT NULL CHECK(rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id UUID NOT NULL REFERENCES users(id),
    reported_user_id UUID NOT NULL REFERENCES users(id),
    booking_id UUID REFERENCES bookings(id),
    chat_session_id UUID REFERENCES chat_sessions(id),
    chat_message_id UUID REFERENCES chat_messages(id),
    report_type VARCHAR(30) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- SYSTEM
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    type VARCHAR(30) DEFAULT 'GENERAL',
    is_read BOOLEAN DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE activity_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id UUID,
    changes JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- INDEXES
CREATE INDEX idx_users_email ON users(lower(email));
CREATE INDEX idx_sessions_user ON user_sessions(user_id);
CREATE INDEX idx_astro_user ON user_astrological_data(user_id);
CREATE INDEX idx_booking_user ON bookings(user_id);
CREATE INDEX idx_booking_reader ON bookings(reader_profile_id);
CREATE INDEX idx_tarot_user ON tarot_readings(user_id);
CREATE INDEX idx_chat_session_created ON chat_messages(session_id, created_at DESC);
CREATE INDEX idx_notification_unread ON notifications(user_id) WHERE is_read = FALSE;

-- UPDATED_AT TRIGGER
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
