-- Ghim thông báo: tin đã ghim lên đầu danh sách và không bị xoá hàng loạt.
ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_notifications_user_pinned_created
    ON notifications (user_id, pinned DESC, created_at DESC);
