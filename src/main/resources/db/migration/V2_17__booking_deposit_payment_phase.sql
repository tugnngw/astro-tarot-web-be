-- ============================================================
-- ASTRO TAROT — Thanh toán đặt cọc & Payment Phase
-- ============================================================
-- File migration mới. Không sửa V1__init_schema.sql vì Flyway
-- đã chạy migration đó ở môi trường production; sửa sẽ gây
-- lỗi checksum.

-- Thêm cột đặt cọc và thời hạn thanh toán cho bookings
ALTER TABLE bookings
    ADD COLUMN deposit_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN remaining_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN payment_deadline TIMESTAMPTZ,
    ADD COLUMN forfeited_amount BIGINT NOT NULL DEFAULT 0;

-- CHECK constraint cho payment_status mở rộng
ALTER TABLE bookings
    ADD CONSTRAINT chk_booking_payment_status
    CHECK (payment_status IN ('UNPAID', 'DEPOSIT_PAID', 'PAID', 'REFUNDED', 'FAILED'));

-- CHECK cho deposit/remaining không âm
ALTER TABLE bookings
    ADD CONSTRAINT chk_booking_deposit_non_negative CHECK (deposit_amount >= 0),
    ADD CONSTRAINT chk_booking_remaining_non_negative CHECK (remaining_amount >= 0),
    ADD CONSTRAINT chk_booking_forfeited_non_negative CHECK (forfeited_amount >= 0);

-- Thêm cột phase cho payment_transactions
ALTER TABLE payment_transactions
    ADD COLUMN phase VARCHAR(20) NOT NULL DEFAULT 'DEPOSIT';

-- CHECK constraint cho phase
ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_tx_phase
    CHECK (phase IN ('DEPOSIT', 'REMAINING', 'FULL'));

-- Backfill booking cũ: đặt cọc = total_amount / 2, remaining = phần còn lại
-- Chỉ ảnh hưởng booking có payment_status = 'UNPAID' hoặc 'PAID'
UPDATE bookings
SET deposit_amount = total_amount / 2,
    remaining_amount = total_amount - (total_amount / 2),
    payment_deadline = CASE
        WHEN start_time - interval '12 hours' > NOW()
            THEN start_time - interval '12 hours'
        ELSE NULL
    END
WHERE payment_status IN ('UNPAID', 'PAID');

-- Index cho job quét booking quá hạn
CREATE INDEX IF NOT EXISTS idx_bookings_payment_deadline
    ON bookings (payment_deadline)
    WHERE payment_status = 'DEPOSIT_PAID' AND payment_deadline IS NOT NULL;
