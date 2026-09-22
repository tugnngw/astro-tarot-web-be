-- ============================================================
-- CHAT GIỮA KHÁCH VÀ READER, GẮN VỚI MỘT BUỔI ĐẶT LỊCH
-- ============================================================
-- Không có bảng "conversations" riêng: một booking ĐÃ LÀ một cuộc hội thoại.
-- Hai người, một khoảng thời gian, một lần thanh toán. Thêm một bảng nữa chỉ
-- để chứa đúng cặp (user, reader) vốn đã nằm sẵn ở bookings là thừa, và tạo
-- ra hai nguồn sự thật về "ai được nói với ai".
--
-- Tín hiệu cuộc gọi (WebRTC offer/answer/ICE) KHÔNG lưu ở đây. Nó chỉ có
-- nghĩa trong vài giây lúc bắt tay, lưu lại là rác.

CREATE TABLE booking_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body TEXT NOT NULL,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Truy vấn duy nhất chạy thường xuyên: "lấy tin của booking này, mới nhất
-- trước, có phân trang". Chỉ mục ghép theo đúng thứ tự đó.
CREATE INDEX idx_booking_messages_booking_created
    ON booking_messages (booking_id, created_at DESC);

-- Đếm số tin chưa đọc của phía bên kia. Lọc luôn read_at IS NULL vào chỉ mục
-- một phần cho nhẹ, vì tin đã đọc chiếm đa số và không bao giờ bị đếm.
CREATE INDEX idx_booking_messages_unread
    ON booking_messages (booking_id, sender_id)
    WHERE read_at IS NULL;
