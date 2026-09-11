-- ============================================================
-- Sổ cái ký quỹ, tiền phạt vi phạm, và thông tin để dựng QR ngân hàng
--
-- Ba thiếu sót của khối tiền hiện tại, sửa cùng một lần vì chúng dính nhau:
--
-- 1. escrow_accounts chỉ có BỐN con số cộng dồn (balance, pending_balance,
--    total_earned, total_withdrawn). Không có lịch sử. Reader nhìn thấy
--    "1.275.000 đ" mà không biết nó đến từ buổi xem nào, và khi số liệu lệch
--    thì không ai đối soát được vì không có gì để đối chiếu. Tiền mà không có
--    sổ cái là tiền không kiểm toán được.
--
-- 2. Xử lý báo cáo vi phạm không đụng gì tới tiền. Quản lý kết luận Reader sai
--    thì chỉ ghi được một dòng ghi chú — hình phạt duy nhất là lời nói.
--
-- 3. Lệnh rút chỉ lưu tên ngân hàng dạng chữ tự do. Muốn dựng mã QR chuyển
--    khoản thì phải có mã BIN của ngân hàng theo chuẩn VietQR; "Vietcombank",
--    "VCB", "ngân hàng ngoại thương" là ba cách viết của cùng một nơi mà máy
--    không đoán được.
-- ============================================================

-- ---------- Sổ cái: mọi đồng ra vào đều có một dòng ----------
CREATE TABLE escrow_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escrow_account_id UUID NOT NULL REFERENCES escrow_accounts(id),

    -- Loại nghiệp vụ. Không dùng enum của Postgres: thêm một loại mới sẽ phải
    -- ALTER TYPE, mà Flyway chạy trong transaction thì việc đó rất phiền.
    kind VARCHAR(32) NOT NULL,

    -- Luôn dương. Hướng tiền nằm ở `kind`, không nhét dấu âm vào số tiền —
    -- cộng nhầm dấu là loại lỗi im lặng nhất trong kế toán.
    amount BIGINT NOT NULL CHECK (amount >= 0),

    -- Số dư rút được SAU nghiệp vụ này. Chép lại có chủ ý: để đối soát được
    -- mà không phải cộng dồn cả bảng, và để thấy ngay chỗ nào đứt mạch.
    balance_after BIGINT NOT NULL,
    pending_after BIGINT NOT NULL,

    -- Nguồn gốc. Cả ba đều cho phép rỗng vì mỗi loại nghiệp vụ chỉ dính tới
    -- một trong số đó.
    booking_id UUID REFERENCES bookings(id),
    report_id UUID REFERENCES reports(id),
    payout_id UUID REFERENCES payout_requests(id),

    -- Viết cho Reader đọc, không phải cho lập trình viên đọc.
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_escrow_txn_kind CHECK (kind IN (
        'HOLD',            -- khách trả tiền, giữ lại
        'RELEASE',         -- buổi xem xong, nhả cho Reader
        'REFUND',          -- huỷ sau khi đã trả, gỡ khỏi phần giữ
        'PENALTY',         -- trừ vì vi phạm
        'PENALTY_DEBT',    -- phạt vượt số dư, ghi nợ để thu sau
        'DEBT_COLLECTED',  -- thu nợ phạt từ khoản nhả về sau
        'PAYOUT_RESERVE',  -- giữ chỗ số tiền xin rút
        'PAYOUT_RETURN',   -- lệnh rút bị từ chối, trả lại
        'PAYOUT_SETTLE'    -- đã chuyển khoản thật
    ))
);

-- Reader mở tab "Thu nhập" là đọc ngay sổ của mình, mới nhất trước.
CREATE INDEX idx_escrow_txn_account_time
    ON escrow_transactions (escrow_account_id, created_at DESC);

-- ---------- Nợ tiền phạt ----------
-- Vì sao cần: ràng buộc chk_escrow_balance_non_negative (V2_1) chặn số dư âm,
-- nên một khoản phạt lớn hơn số dư hiện có sẽ làm giao dịch nổ. Bỏ qua phần
-- thiếu thì Reader vi phạm đúng lúc ví rỗng lại thoát phạt — phần thưởng cho
-- việc rút sạch tiền trước khi bị xử lý. Ghi nợ rồi trừ dần vào các khoản nhả
-- sau là cách duy nhất vừa giữ được ràng buộc vừa không tạo ra kẽ hở đó.
ALTER TABLE escrow_accounts
    ADD COLUMN penalty_owed BIGINT NOT NULL DEFAULT 0
        CHECK (penalty_owed >= 0);

COMMENT ON COLUMN escrow_accounts.penalty_owed IS
    'Tiền phạt chưa thu được vì lúc xử lý số dư không đủ. Tự trừ dần vào các khoản nhả về sau.';

-- ---------- Tiền phạt gắn vào kết luận vi phạm ----------
ALTER TABLE reports
    ADD COLUMN penalty_amount BIGINT NOT NULL DEFAULT 0
        CHECK (penalty_amount >= 0);

COMMENT ON COLUMN reports.penalty_amount IS
    'Số tiền trừ của người bị báo cáo khi kết luận là có vi phạm. 0 = nhắc nhở, không phạt.';

-- ---------- Thông tin dựng QR chuyển khoản ----------
ALTER TABLE payout_requests
    ADD COLUMN bank_bin VARCHAR(20),
    ADD COLUMN method VARCHAR(20) NOT NULL DEFAULT 'BANK_TRANSFER';

ALTER TABLE payout_requests
    ADD CONSTRAINT chk_payout_method CHECK (method IN ('BANK_TRANSFER'));

COMMENT ON COLUMN payout_requests.bank_bin IS
    'Mã BIN ngân hàng theo chuẩn VietQR (ví dụ 970436 = Vietcombank). Dùng để dựng mã QR chuyển khoản.';
COMMENT ON COLUMN payout_requests.method IS
    'Hiện chỉ có chuyển khoản ngân hàng. Cột tồn tại sẵn để sau này thêm kênh chi tự động mà không phải đổi lược đồ.';
