-- ============================================================
-- Ví và gói AI: dữ liệu để demo hai tính năng mới.
--
-- V2_24 tạo ví RỖNG cho mọi người (balance = 0) và không có giao dịch nào.
-- V2_22 gieo năm gói bán nhưng không ai mua. Nên mở màn Ví hay màn Gói AI lúc
-- demo thì chỉ thấy số 0 và danh sách trống — không cho thấy tính năng chạy.
--
-- ------------------------------------------------------------
-- Sổ cái phải cộng trừ khớp nhau
-- ------------------------------------------------------------
-- wallet_transactions có balance_before và balance_after. Gieo số bừa thì mở
-- lịch sử ra là thấy ngay: dòng này kết ở 300k, dòng sau lại mở ở 500k. Mỗi
-- người dưới đây có một câu chuyện liền mạch, và số dư cuối của ví được tính
-- LẠI từ dòng cuối của sổ chứ không gõ tay.
--
-- Số tiền gói lấy đúng theo V2_22:
--   Basic 1 Ngày   15.000   5 lượt/ngày    1 ngày
--   Combo 3 Ngày   39.000  10 lượt/ngày    3 ngày
--   VIP 7 Ngày     79.000  15 lượt/ngày    7 ngày
--   Cơ bản Tháng   99.000  10 lượt/ngày   30 ngày
--   Pro Tháng     199.000  25 lượt/ngày   30 ngày
--
-- Sai một đồng so với bảng giá là lúc demo có người đối chiếu ra ngay.
--
-- ------------------------------------------------------------
-- Vẫn là dữ liệu demo
-- ------------------------------------------------------------
-- Các lượt nạp mang payment_method 'SEED_DEMO' và reference_id 'SEED-...',
-- không đối chiếu được với PayOS. Xem khối cảnh báo ở đầu V2_23 — nguyên văn
-- vẫn đúng: đừng nộp chúng làm minh chứng giao dịch cho Outcome 3.
--
-- Idempotent: id cố định, bọc NOT EXISTS. Thiếu tài khoản nào thì bỏ qua tài
-- khoản đó, không lỗi.
-- ============================================================

-- ---------- 1. Sổ cái ví ----------
-- Ba câu chuyện, mỗi dòng nối tiếp dòng trước:
--
--   alloffdenchi13  nạp 300.000, mua Pro Tháng 199.000           -> còn 101.000
--   user.an         nạp 100.000, mua Combo 3 Ngày, Basic 1 Ngày  -> còn  46.000
--   user.bich       nạp 200.000, trả buổi 150.000, được hoàn lại
--                   vì Reader huỷ, rồi mua Cơ bản Tháng 99.000   -> còn 101.000
INSERT INTO wallet_transactions (
    id, wallet_id, user_id, type, amount,
    balance_before, balance_after, status, reference_id,
    payment_method, description, created_at
)
SELECT
    ('d1000000-0000-4000-8000-' || lpad(t.i::text, 12, '0'))::uuid,
    w.id, u.id, t.loai, t.so_tien,
    t.truoc, t.sau, 'SUCCESS',
    'SEED-' || lpad(t.i::text, 4, '0'),
    CASE WHEN t.loai = 'TOPUP' THEN 'SEED_DEMO' ELSE 'WALLET' END,
    t.mo_ta,
    date_trunc('minute', NOW()) - (t.ngay || ' days')::interval
FROM (VALUES
    -- alloffdenchi13
    (1,  'alloffdenchi13@gmail.com', 'TOPUP',           300000,      0, 300000, 20, 'Nạp ví qua VietQR'),
    (2,  'alloffdenchi13@gmail.com', 'AI_SUBSCRIPTION', -199000, 300000, 101000, 18, 'Mua gói Pro Tháng'),
    -- user.an
    (3,  'user.an@astrotarot.demo',  'TOPUP',           100000,      0, 100000, 28, 'Nạp ví qua VietQR'),
    (4,  'user.an@astrotarot.demo',  'AI_SUBSCRIPTION',  -39000, 100000,  61000, 25, 'Mua gói Combo 3 Ngày'),
    (5,  'user.an@astrotarot.demo',  'AI_SUBSCRIPTION',  -15000,  61000,  46000,  3, 'Mua gói Basic 1 Ngày'),
    -- user.bich
    (6,  'user.bich@astrotarot.demo','TOPUP',           200000,      0, 200000, 22, 'Nạp ví qua VietQR'),
    (7,  'user.bich@astrotarot.demo','BOOKING_PAYMENT', -150000, 200000,  50000, 16, 'Thanh toán buổi xem 30 phút'),
    (8,  'user.bich@astrotarot.demo','BOOKING_REFUND',   150000,  50000, 200000, 15, 'Hoàn tiền: Reader huỷ buổi'),
    (9,  'user.bich@astrotarot.demo','AI_SUBSCRIPTION',  -99000, 200000, 101000, 10, 'Mua gói Cơ bản Tháng')
) AS t(i, email, loai, so_tien, truoc, sau, ngay, mo_ta)
JOIN users u ON u.email = t.email
JOIN user_wallets w ON w.user_id = u.id
WHERE NOT EXISTS (
    SELECT 1 FROM wallet_transactions x
    WHERE x.id = ('d1000000-0000-4000-8000-' || lpad(t.i::text, 12, '0'))::uuid
);

-- ---------- 2. Số dư ví tính LẠI từ sổ ----------
-- Không gõ tay số dư: lấy balance_after của giao dịch mới nhất. Gõ tay thì
-- sớm muộn có người thêm một dòng vào sổ mà quên sửa số dư, và hai chỗ lệch
-- nhau là lỗi không ai tin nổi khi nhìn thấy trên màn hình.
UPDATE user_wallets w
SET balance = m.so_du, updated_at = NOW()
FROM (
    SELECT DISTINCT ON (wallet_id) wallet_id, balance_after AS so_du
      FROM wallet_transactions
     ORDER BY wallet_id, created_at DESC, id DESC
) m
WHERE w.id = m.wallet_id
  AND w.balance IS DISTINCT FROM m.so_du;

-- ---------- 3. Lượt mua gói AI ----------
-- Khớp từng dòng AI_SUBSCRIPTION ở trên: cùng ngày, cùng số tiền.
-- purchase_type = 'WALLET' vì tiền trừ từ ví, không qua cổng thanh toán.
--
-- Trạng thái tính theo end_at so với hôm nay, không gán cứng: gán cứng thì vài
-- tuần nữa một gói "ACTIVE" đã hết hạn từ lâu vẫn nằm đó.
INSERT INTO user_plan_purchase (
    id, user_id, plan_id, plan_name_snapshot, daily_quota_snapshot,
    price_snapshot, start_at, end_at, status, purchase_type,
    created_at, updated_at
)
SELECT
    ('d2000000-0000-4000-8000-' || lpad(p.i::text, 12, '0'))::uuid,
    u.id, p.plan_id::uuid, p.ten, p.quota, p.gia,
    date_trunc('minute', NOW()) - (p.ngay || ' days')::interval,
    date_trunc('minute', NOW()) - (p.ngay || ' days')::interval + (p.so_ngay || ' days')::interval,
    CASE
        WHEN date_trunc('minute', NOW()) - (p.ngay || ' days')::interval
             + (p.so_ngay || ' days')::interval > NOW()
        THEN 'ACTIVE' ELSE 'EXPIRED'
    END,
    'WALLET',
    date_trunc('minute', NOW()) - (p.ngay || ' days')::interval,
    NOW()
FROM (VALUES
    (1, 'alloffdenchi13@gmail.com', 'a0000000-0000-4000-8000-000000000006', 'Pro Tháng',    25, 199000, 18, 30),
    (2, 'user.an@astrotarot.demo',  'a0000000-0000-4000-8000-000000000003', 'Combo 3 Ngày', 10,  39000, 25,  3),
    (3, 'user.an@astrotarot.demo',  'a0000000-0000-4000-8000-000000000002', 'Basic 1 Ngày',  5,  15000,  3,  1),
    (4, 'user.bich@astrotarot.demo','a0000000-0000-4000-8000-000000000005', 'Cơ bản Tháng', 10,  99000, 10, 30)
) AS p(i, email, plan_id, ten, quota, gia, ngay, so_ngay)
JOIN users u ON u.email = p.email
WHERE EXISTS (SELECT 1 FROM subscription_plan s WHERE s.id = p.plan_id::uuid)
AND NOT EXISTS (
    SELECT 1 FROM user_plan_purchase x
    WHERE x.id = ('d2000000-0000-4000-8000-' || lpad(p.i::text, 12, '0'))::uuid
);

-- ---------- 4. Lượt dùng AI theo ngày ----------
-- Chỉ cho hai người còn gói ACTIVE, và luôn DƯỚI hạn mức ngày của gói họ mua:
-- một dòng count_used vượt quota là mâu thuẫn ngay trên màn hình, vì chính hệ
-- thống phải chặn trước khi tới đó.
--
-- Số lượt nhấp nhô chứ không đều: người thật không dùng đúng bốn lượt mỗi ngày.
INSERT INTO ai_usage_daily (user_id, usage_date, count_used)
SELECT u.id, CURRENT_DATE - d.lui, d.so_luot
FROM (VALUES
    (0, 3), (1, 7), (2, 0), (3, 12), (4, 5), (5, 9), (6, 2)
) AS d(lui, so_luot)
JOIN users u ON u.email = 'alloffdenchi13@gmail.com'
WHERE NOT EXISTS (
    SELECT 1 FROM ai_usage_daily a
    WHERE a.user_id = u.id AND a.usage_date = CURRENT_DATE - d.lui
);

INSERT INTO ai_usage_daily (user_id, usage_date, count_used)
SELECT u.id, CURRENT_DATE - d.lui, d.so_luot
FROM (VALUES
    (0, 2), (1, 5), (2, 8), (3, 0), (4, 4)
) AS d(lui, so_luot)
JOIN users u ON u.email = 'user.bich@astrotarot.demo'
WHERE NOT EXISTS (
    SELECT 1 FROM ai_usage_daily a
    WHERE a.user_id = u.id AND a.usage_date = CURRENT_DATE - d.lui
);
