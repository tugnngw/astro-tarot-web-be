-- ============================================================
-- V2_30: Cập nhật bảng user_feedback hỗ trợ chỉnh sửa và quản trị
-- Thêm cột updated_at để theo dõi thời điểm người dùng sửa góp ý.
-- Tạo index tối ưu tìm kiếm theo user_id và rating.
-- Khử trùng lặp seed cũ và tạo partial unique index cho user_id.
-- ============================================================

ALTER TABLE user_feedback
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ;

-- Đồng bộ updated_at ban đầu khớp created_at cho các bản ghi cũ
UPDATE user_feedback
SET updated_at = created_at
WHERE updated_at IS NULL;

-- Dọn các bản ghi cũ trùng user_id (từ seed V2_20 gán nhiều bản ghi cho 5 tài khoản demo)
-- thành ẩn danh (user_id = NULL) cho các bản ghi cũ hơn, chỉ giữ bản ghi mới nhất cho mỗi user_id.
-- Giữ nguyên tổng số lượng 20 bản ghi, bảo toàn điểm số và OC3.
UPDATE user_feedback f
SET user_id = NULL
WHERE f.user_id IS NOT NULL
  AND f.id NOT IN (
      SELECT DISTINCT ON (sub.user_id) sub.id
      FROM user_feedback sub
      WHERE sub.user_id IS NOT NULL
      ORDER BY sub.user_id, sub.created_at DESC
  );

-- Index duy nhất ở cấp CSDL: mỗi tài khoản chỉ có tối đa 1 bản ghi phản hồi.
-- Ràng buộc một phần (WHERE user_id IS NOT NULL) bảo đảm an toàn đa instance
-- mà vẫn cho phép khách vãng lai (user_id NULL) gửi nhiều phản hồi độc lập.
DROP INDEX IF EXISTS idx_user_feedback_user_id;
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_feedback_user_id
    ON user_feedback (user_id)
    WHERE user_id IS NOT NULL;

-- Index phục vụ lọc theo số sao đánh giá trong màn Quản trị
CREATE INDEX IF NOT EXISTS idx_user_feedback_rating
    ON user_feedback (rating);
