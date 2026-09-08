-- ============================================================
-- Đăng nhập bằng email + luồng quên mật khẩu
--
-- Trước đây định danh đăng nhập là username. Từ nay email là thứ người dùng
-- nhập để đăng nhập; username vẫn giữ (NOT NULL UNIQUE từ V1_1, và OAuth
-- đang dùng) nhưng được sinh tự động từ email khi đăng ký.
-- ============================================================

-- Token đặt lại mật khẩu. Lưu bản BĂM chứ không lưu token gốc: nếu lộ CSDL
-- thì kẻ lấy được cũng không dựng lại được link đặt lại mật khẩu.
ALTER TABLE users ADD COLUMN password_reset_token VARCHAR(255);
ALTER TABLE users ADD COLUMN password_reset_expires_at TIMESTAMPTZ;

CREATE UNIQUE INDEX idx_users_password_reset_token
    ON users(password_reset_token)
    WHERE password_reset_token IS NOT NULL;

-- Email giờ là định danh đăng nhập nên phải tra cứu nhanh và không phân biệt
-- hoa thường. V1 có UNIQUE trên cột email nhưng đó là so sánh phân biệt hoa
-- thường, tức là A@x.com và a@x.com vẫn lọt qua thành hai tài khoản.
CREATE UNIQUE INDEX idx_users_email_lower
    ON users(lower(email))
    WHERE email IS NOT NULL AND deleted_at IS NULL;
