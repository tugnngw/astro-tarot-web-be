-- ============================================================
-- Ghi chú buổi xem: thứ khách nhận được sau khi trả tiền
--
-- Cho tới giờ, một buổi xem để lại đúng ba thứ trong hệ thống: trạng thái
-- COMPLETED, một dòng tiền, và có thể là một đánh giá. Nội dung buổi xem —
-- lá bài nào, Reader nói gì, khách nên để ý điều gì — không được lưu ở đâu cả.
--
-- Với đề tài Tarot thì đó chính là sản phẩm. Khách trả 200.000đ và sau một
-- tuần không còn gì để đọc lại; lịch sử trải bài của họ thì chỉ có phần AI,
-- tức phần miễn phí. Phần đắt tiền hơn lại là phần biến mất.
--
-- Ghi chú do Reader viết, khách đọc. Không phải nhật ký nội bộ.
-- ============================================================

ALTER TABLE bookings
    ADD COLUMN reader_note TEXT,
    ADD COLUMN reader_note_at TIMESTAMPTZ;

COMMENT ON COLUMN bookings.reader_note IS
    'Ghi chú Reader viết cho khách sau buổi xem. Khách đọc được, nên viết cho họ.';
COMMENT ON COLUMN bookings.reader_note_at IS
    'Lần cập nhật ghi chú gần nhất. Sửa chính tả là chuyện thường, nên cho sửa.';
