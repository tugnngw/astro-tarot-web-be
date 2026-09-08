-- ============================================================
-- Các trường hồ sơ cá nhân
--
-- Bảng users trước đây chỉ có full_name, phone, avatar — không đủ cho một
-- trang hồ sơ chỉnh sửa được.
--
-- Cố ý KHÔNG thêm giờ sinh / nơi sinh / toạ độ ở đây: những thứ đó đã nằm ở
-- user_astrological_data và được mã hoá AES-256-GCM vì là dữ liệu nhạy cảm
-- dùng để lập bản đồ sao. date_of_birth dưới đây chỉ là ngày sinh mức tài
-- khoản (để chúc mừng sinh nhật, kiểm tra độ tuổi), không thay thế cho hồ sơ
-- chiêm tinh.
-- ============================================================

ALTER TABLE users ADD COLUMN gender VARCHAR(20) NOT NULL DEFAULT 'UNDISCLOSED'
    CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED'));

ALTER TABLE users ADD COLUMN date_of_birth DATE;
ALTER TABLE users ADD COLUMN bio TEXT;
ALTER TABLE users ADD COLUMN address TEXT;
ALTER TABLE users ADD COLUMN city VARCHAR(120);
ALTER TABLE users ADD COLUMN country VARCHAR(120);
