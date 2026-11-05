-- ============================================================
-- V2_38: Một tài khoản góp ý được nhiều lần
--
-- V2_34 đã khoá ở tầng CSDL: mỗi tài khoản tối đa MỘT bản ghi góp ý, bằng
-- một partial unique index trên user_id. Lúc ấy tầng ứng dụng cũng ghi đè
-- bản cũ nên hai bên khớp nhau.
--
-- Nay ứng dụng giữ từng lần gửi thành một bản ghi riêng — một người dùng sản
-- phẩm nhiều lần thì có nhiều điều để nói, ở những thời điểm khác nhau. Index
-- này trở thành chỗ duy nhất còn giữ luật cũ, và nó làm mọi lần gửi thứ hai
-- của một tài khoản đổ với lỗi trùng khoá.
--
-- Đổi lại thành index THƯỜNG: vẫn phục vụ các truy vấn theo user_id
-- (countByUserId, findFirstByUserIdOrderByCreatedAtDesc) mà không cấm trùng.
-- ============================================================

DROP INDEX IF EXISTS uq_user_feedback_user_id;

CREATE INDEX IF NOT EXISTS idx_user_feedback_user_id
    ON user_feedback (user_id)
    WHERE user_id IS NOT NULL;
