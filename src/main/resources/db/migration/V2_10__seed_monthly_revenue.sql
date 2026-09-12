-- Seed giao dịch SUCCESS rải 11 tháng gần đây để biểu đồ "Doanh thu theo tháng"
-- trên trang Quản trị có nhiều cột (V2_4 chỉ có ~2 giao dịch trong tháng hiện tại).
--
-- booking_id NULL: chỉ phục vụ thống kê, không gắn lịch thật.
-- Idempotent theo external_transaction_id cố định.

INSERT INTO payment_transactions (
    id, booking_id, user_id, amount, payment_method,
    external_transaction_id, status, metadata, created_at
)
SELECT
    ('f1000000-0000-4000-8000-0000000001' || lpad(g.n::text, 2, '0'))::uuid,
    NULL::uuid,
    CASE
        WHEN g.n % 2 = 0 THEN 'a1000000-0000-4000-8000-000000000005'::uuid -- An
        ELSE 'a1000000-0000-4000-8000-000000000006'::uuid                 -- Bích
    END,
    g.amount,
    'BANK_TRANSFER',
    'SEED-PAY-MONTH-' || lpad(g.n::text, 2, '0'),
    'SUCCESS',
    jsonb_build_object(
        'note', 'seed monthly revenue chart',
        'monthsAgo', g.n
    ),
    -- Giữa tháng (timezone VN) để to_char(created_at, 'YYYY-MM') khớp đúng tháng.
    (
        date_trunc('month', NOW() AT TIME ZONE 'Asia/Ho_Chi_Minh')
        - (g.n || ' months')::interval
        + INTERVAL '12 days'
        + TIME '10:00'
    ) AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM (VALUES
    (1,   980000::bigint),
    (2,  1050000::bigint),
    (3,   870000::bigint),
    (4,   940000::bigint),
    (5,   810000::bigint),
    (6,   590000::bigint),
    (7,   720000::bigint),
    (8,   650000::bigint),
    (9,   380000::bigint),
    (10,  510000::bigint),
    (11,  420000::bigint)
) AS g(n, amount)
WHERE EXISTS (
    SELECT 1 FROM users u
    WHERE u.id = 'a1000000-0000-4000-8000-000000000005'::uuid
)
  AND NOT EXISTS (
    SELECT 1 FROM payment_transactions p
    WHERE p.external_transaction_id = 'SEED-PAY-MONTH-' || lpad(g.n::text, 2, '0')
       OR p.id = ('f1000000-0000-4000-8000-0000000001' || lpad(g.n::text, 2, '0'))::uuid
);
