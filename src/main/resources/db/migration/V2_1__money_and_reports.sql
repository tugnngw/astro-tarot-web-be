-- ============================================================
-- Siết ràng buộc cho khối tiền, và bổ sung trường xử lý báo cáo vi phạm
--
-- Tầng service đã kiểm tra hết những điều dưới đây. Nhưng đây là tiền và là
-- chỗ chồng lấn lịch hẹn: hai request chạy song song vẫn lọt qua khe giữa lúc
-- đọc và lúc ghi. Ràng buộc ở database là hàng rào duy nhất không có khe đó.
-- ============================================================

-- ---------- Ký quỹ không bao giờ âm ----------
-- Trừ tiền hai lần vì một cú double-click là kịch bản rất thật. Nếu tầng code
-- sót, ta muốn giao dịch nổ chứ không muốn một tài khoản mang số dư -280000.
ALTER TABLE escrow_accounts
    ADD CONSTRAINT chk_escrow_balance_non_negative CHECK (balance >= 0),
    ADD CONSTRAINT chk_escrow_pending_non_negative CHECK (pending_balance >= 0),
    ADD CONSTRAINT chk_escrow_total_earned_non_negative CHECK (total_earned >= 0),
    ADD CONSTRAINT chk_escrow_total_withdrawn_non_negative CHECK (total_withdrawn >= 0);

-- ---------- Trạng thái phải nằm trong tập hợp đã biết ----------
-- Ba bảng này lưu trạng thái dạng VARCHAR mà không có CHECK nào, nên một lỗi
-- chính tả ở tầng code sẽ ghi thẳng vào database và không ai phát hiện.
ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_status
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED'));

ALTER TABLE payout_requests
    ADD CONSTRAINT chk_payout_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PAID'));

ALTER TABLE reports
    ADD CONSTRAINT chk_report_status
    CHECK (status IN ('PENDING', 'REVIEWED', 'RESOLVED', 'REJECTED'));

-- ---------- Không cho hai lịch hẹn chồng giờ của cùng một Reader ----------
-- BookingServiceImpl có kiểm tra chồng lấn ngay trước khi ghi, nhưng hai khách
-- bấm cùng lúc vẫn lọt: cả hai cùng đọc thấy trống rồi cùng ghi. Ràng buộc
-- loại trừ đóng hẳn khe đó.
--
-- Đơn đã huỷ không tính, đúng như truy vấn ở tầng code — huỷ xong thì khung
-- giờ phải mở lại cho người khác.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings
    ADD CONSTRAINT no_overlapping_bookings
    EXCLUDE USING gist (
        reader_profile_id WITH =,
        tstzrange(start_time, end_time) WITH &&
    ) WHERE (status <> 'CANCELLED');

-- ---------- Xử lý báo cáo vi phạm ----------
-- Bảng reports mới chỉ ghi được ai tố ai. Muốn quản lý xử lý được thì phải
-- lưu cả người xử lý và kết luận — nếu không, "REVIEWED" là một trạng thái
-- không ai chịu trách nhiệm.
ALTER TABLE reports
    ADD COLUMN handled_by UUID REFERENCES users(id),
    ADD COLUMN handled_at TIMESTAMPTZ,
    ADD COLUMN resolution_note TEXT;

COMMENT ON COLUMN reports.resolution_note IS
    'Kết luận của người xử lý. Người tố cáo đọc được, nên viết cho họ hiểu.';

-- ---------- Lý do từ chối lệnh rút ----------
ALTER TABLE payout_requests
    ADD COLUMN reject_reason TEXT;

COMMENT ON COLUMN payout_requests.reject_reason IS
    'Vì sao lệnh rút bị từ chối. Reader phải biết để sửa thông tin ngân hàng.';
