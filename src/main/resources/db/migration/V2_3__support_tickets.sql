-- ============================================================
-- Hàng chờ hỗ trợ khách
--
-- Trụ cột còn thiếu của khu vực Nhân viên: quyền SUPPORT_VIEW và
-- SUPPORT_RESPOND đã có từ V2_0 nhưng chưa có chỗ lưu, nên tab "Hỗ trợ khách"
-- đứng trống. Hai bảng:
--
--  - support_tickets: mỗi yêu cầu của một khách, kèm trạng thái xử lý và người
--    trực đang nhận (assigned_to). assigned_to để trống khi ticket còn trong
--    hàng chờ chung.
--  - support_ticket_messages: dòng trao đổi qua lại. Tách khỏi ticket vì một
--    yêu cầu có thể qua nhiều lượt hỏi–đáp, và cần biết mỗi câu do ai gửi.
--
-- Trạng thái đi một chiều theo vòng đời tự nhiên: OPEN (khách vừa gửi) →
-- PENDING (nhân viên đã trả lời, chờ khách) → RESOLVED (đã xong) → CLOSED
-- (đóng hẳn). Không ép ràng buộc chuyển trạng thái ở CSDL — đó là luật nghiệp
-- vụ, giữ ở tầng service để còn báo lỗi cho người dùng bằng tiếng Việt.
-- ============================================================

CREATE TABLE support_tickets (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Khách tạo ticket. Xoá tài khoản thì cuốn theo ticket của họ.
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject      VARCHAR(200) NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    -- Nhân viên đang phụ trách. Giữ ticket lại khi người trực bị xoá tài khoản,
    -- chỉ gỡ liên kết — mất một ticket vì đổi nhân sự là không chấp nhận được.
    assigned_to  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_ticket_status
        CHECK (status IN ('OPEN', 'PENDING', 'RESOLVED', 'CLOSED'))
);

-- Hàng chờ của nhân viên lọc theo trạng thái rồi xếp theo thời gian; khách mở
-- trang xem ticket của chính mình. Hai chỉ mục cho hai lối đọc đó.
CREATE INDEX idx_support_tickets_status_created
    ON support_tickets (status, created_at DESC);
CREATE INDEX idx_support_tickets_user
    ON support_tickets (user_id, created_at DESC);

CREATE TABLE support_ticket_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   UUID NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    -- Người gửi — khách hay nhân viên đều lưu chung ở đây; vai trò suy ra từ
    -- việc sender có phải chủ ticket hay không, không cần cột riêng.
    sender_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_support_messages_ticket_created
    ON support_ticket_messages (ticket_id, created_at);

COMMENT ON TABLE support_tickets IS
    'Yêu cầu hỗ trợ của khách; nhân viên xử lý ở tab Hỗ trợ khách.';
COMMENT ON COLUMN support_tickets.assigned_to IS
    'Nhân viên đang phụ trách; null khi còn trong hàng chờ chung.';
