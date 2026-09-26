-- Lần cuối người dùng còn kết nối, để khung trao đổi nói "Hoạt động 5 phút trước".
--
-- Tách khỏi last_login_at: đăng nhập một lần rồi mở app suốt tuần thì
-- last_login_at đứng im ở ngày đầu, và nó sẽ nói dối rằng người ta biến mất
-- bảy ngày trước.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;
