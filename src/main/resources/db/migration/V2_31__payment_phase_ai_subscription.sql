-- Nới ràng buộc phase cho mua gói AI qua PayOS.
--
-- SubscriptionServiceImpl ghi PaymentPhase.AI_SUBSCRIPTION vào
-- payment_transactions.phase khi tạo link PayOS. Ràng buộc từ V2_24 chỉ cho:
--
--     CHECK (phase IN ('DEPOSIT', 'REMAINING', 'FULL', 'TOPUP'))
--
-- Không nới thì INSERT đổ ngay khi khách bấm thanh toán PayOS cho gói AI.

ALTER TABLE payment_transactions
    DROP CONSTRAINT IF EXISTS chk_payment_tx_phase;

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_tx_phase
    CHECK (phase IN ('DEPOSIT', 'REMAINING', 'FULL', 'TOPUP', 'AI_SUBSCRIPTION'));
