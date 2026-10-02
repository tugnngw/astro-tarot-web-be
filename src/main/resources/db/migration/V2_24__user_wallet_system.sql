-- V2_24: User Wallet System (Ví người dùng ASTROTAROT)
-- Tạo bảng: user_wallets, wallet_transactions

-- ============================================================
-- 1. user_wallets: Ví cá nhân lưu số dư khả dụng
-- ============================================================
CREATE TABLE user_wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    balance BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_wallet_balance_non_negative CHECK (balance >= 0)
);

-- ============================================================
-- 2. wallet_transactions: Sổ cái biến động số dư ví
-- ============================================================
CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL,
    user_id UUID NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('TOPUP', 'AI_SUBSCRIPTION', 'BOOKING_PAYMENT', 'BOOKING_REFUND', 'ADMIN_ADJUSTMENT')),
    amount BIGINT NOT NULL,
    balance_before BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED')),
    reference_id VARCHAR(100),
    payment_method VARCHAR(50) DEFAULT 'WALLET',
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_wallet FOREIGN KEY (wallet_id) REFERENCES user_wallets(id) ON DELETE CASCADE,
    CONSTRAINT fk_tx_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_user_wallets_user_id ON user_wallets(user_id);
CREATE INDEX idx_wallet_tx_user_created ON wallet_transactions(user_id, created_at DESC);
CREATE INDEX idx_wallet_tx_wallet_created ON wallet_transactions(wallet_id, created_at DESC);
CREATE INDEX idx_wallet_tx_reference ON wallet_transactions(reference_id);

-- Khởi tạo ví rỗng cho tất cả người dùng hiện có
INSERT INTO user_wallets (id, user_id, balance)
SELECT gen_random_uuid(), id, 0
FROM users
ON CONFLICT (user_id) DO NOTHING;

-- ============================================================
-- Nới ràng buộc phase cho lượt nạp ví
-- ============================================================
-- WalletServiceImpl ghi PaymentPhase.TOPUP vào payment_transactions.phase,
-- nhưng ràng buộc dựng ở V2_17 chỉ cho ba giá trị:
--
--     CHECK (phase IN ('DEPOSIT', 'REMAINING', 'FULL'))
--
-- Nên mọi lượt nạp ví sẽ đổ ngay ở câu INSERT với lỗi vi phạm ràng buộc. Lỗi
-- này không lộ ra lúc biên dịch cũng không lộ trong bộ test (test chạy H2 và
-- không đi qua ràng buộc này), chỉ hiện khi có người bấm nạp tiền thật.
ALTER TABLE payment_transactions
    DROP CONSTRAINT IF EXISTS chk_payment_tx_phase;

ALTER TABLE payment_transactions
    ADD CONSTRAINT chk_payment_tx_phase
    CHECK (phase IN ('DEPOSIT', 'REMAINING', 'FULL', 'TOPUP'));
