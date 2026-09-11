-- Ảnh đại diện lưu thẳng trong cơ sở dữ liệu.
--
-- Trước đây file ghi vào thư mục uploads/ BÊN TRONG container. Đĩa của Render
-- ở gói free là tạm: mỗi lần deploy hoặc khởi động lại là mất sạch, trong khi
-- bảng users vẫn giữ đường dẫn — nên avatar thành liên kết hỏng 404. Đã kiểm
-- chứng trên production: users.avatar trỏ tới một file không còn tồn tại.
--
-- Postgres giữ được qua mọi lần deploy và không tốn thêm dịch vụ nào. Ảnh giới
-- hạn 2MB nên một bảng riêng là đủ; để riêng khỏi bảng users để mỗi lần đọc
-- hồ sơ không kéo theo vài trăm KB nhị phân.
CREATE TABLE IF NOT EXISTS user_avatars (
    user_id      UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    content_type TEXT  NOT NULL,
    data         BYTEA NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Đường dẫn cũ trỏ tới file đã mất, để lại chỉ làm ảnh vỡ trên giao diện.
-- Chỉ xoá đường dẫn nội bộ; avatar từ OAuth là URL đầy đủ (http...) nên giữ.
UPDATE users SET avatar = NULL WHERE avatar LIKE '/uploads/avatars/%';
