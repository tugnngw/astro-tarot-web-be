ALTER TABLE users ADD COLUMN username VARCHAR(50);
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN auth_provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users ADD COLUMN provider_id VARCHAR(255);
ALTER TABLE users ADD COLUMN pending_email VARCHAR(255);
ALTER TABLE users ADD COLUMN email_verification_token VARCHAR(255);
ALTER TABLE users ADD COLUMN email_verification_expires_at TIMESTAMPTZ;

UPDATE users
SET username = lower(split_part(email, '@', 1)) || '_' || substring(id::text, 1, 8)
WHERE username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE(username);

CREATE INDEX idx_users_username ON users(lower(username));
CREATE UNIQUE INDEX idx_users_provider ON users(auth_provider, provider_id) WHERE provider_id IS NOT NULL;
CREATE UNIQUE INDEX idx_users_email_verification_token ON users(email_verification_token) WHERE email_verification_token IS NOT NULL;
