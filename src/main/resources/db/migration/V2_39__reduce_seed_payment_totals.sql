-- Thu gọn doanh thu seed và đưa các chỉ số Tổng quan về cùng một khoảng vừa phải.
--
-- Với số liệu trước migration trong ảnh: doanh thu thật khoảng 798.000 đồng,
-- 12 giao dịch, 7 người trả phí, 1 đánh giá và 9 góp ý. Sau migration, mục tiêu
-- xấp xỉ: 1.068.000 đồng, 30 giao dịch, 25 người trả phí, 26 đánh giá và giữ
-- nguyên 29 góp ý. Giao dịch seed mới là các buổi mẫu 15.000 đồng; thanh toán
-- seed cũ được huỷ để không cộng doanh thu demo hàng triệu đồng.
--
-- Tài khoản và mọi khoá chính đều cố định, theo khuôn UUID gieo sẵn, nên chạy
-- lại không nhân đôi dữ liệu và không bị tính nhầm là dữ liệu thật.

-- ---------- 1. Tài khoản khách demo ----------
INSERT INTO users (
    id, username, email, password_hash, full_name, role, status,
    email_verified, email_verified_at, auth_provider, gender, created_at, updated_at
)
SELECT
    ('a3900000-0000-4000-8000-' || lpad(v.i::text, 12, '0'))::uuid,
    'seed_customer_' || lpad(v.i::text, 2, '0'),
    'seed.customer.' || lpad(v.i::text, 2, '0') || '@astrotarot.demo',
    '{noop}da-khoa-seed-' || gen_random_uuid(),
    'Khách mẫu ' || lpad(v.i::text, 2, '0'),
    'USER',
    'ACTIVE',
    TRUE,
    NOW(),
    'LOCAL',
    'UNDISCLOSED',
    NOW(),
    NOW()
FROM generate_series(1, 18) AS v(i)
WHERE NOT EXISTS (
    SELECT 1
    FROM users u
    WHERE u.id = ('a3900000-0000-4000-8000-' || lpad(v.i::text, 12, '0'))::uuid
       OR u.email = 'seed.customer.' || lpad(v.i::text, 2, '0') || '@astrotarot.demo'
);

-- ---------- 2. Buổi mẫu đã hoàn tất, đã trả, có đánh giá ----------
-- Chọn 18 ngày làm việc còn trống trong khoảng 60–365 ngày trước đây để không
-- va vào các booking seed gần đây hoặc lịch booking đang diễn ra.
WITH khach AS (
    SELECT i
    FROM generate_series(1, 18) AS g(i)
),
reader AS (
    SELECT rp.id
    FROM reader_profiles rp
    JOIN users u ON u.id = rp.user_id
    WHERE u.email = 'staff.lan@astrotarot.demo'
),
slot_trong AS (
    SELECT
        (g.ngay::date + TIME '11:00') AT TIME ZONE 'Asia/Ho_Chi_Minh' AS bat_dau
    FROM generate_series(
        CURRENT_DATE - 365,
        CURRENT_DATE - 60,
        INTERVAL '1 day'
    ) AS g(ngay)
    CROSS JOIN reader r
    WHERE EXTRACT(DOW FROM g.ngay) BETWEEN 1 AND 5
      AND NOT EXISTS (
          SELECT 1
          FROM bookings b
          WHERE b.reader_profile_id = r.id
            AND b.status <> 'CANCELLED'
            AND b.start_time < ((g.ngay::date + TIME '11:15') AT TIME ZONE 'Asia/Ho_Chi_Minh')
            AND b.end_time > ((g.ngay::date + TIME '11:00') AT TIME ZONE 'Asia/Ho_Chi_Minh')
      )
    ORDER BY g.ngay DESC
    LIMIT 18
),
slot_xep AS (
    SELECT bat_dau, row_number() OVER (ORDER BY bat_dau DESC) AS stt
    FROM slot_trong
)
INSERT INTO bookings (
    id, user_id, reader_profile_id, start_time, end_time,
    total_amount, status, payment_status,
    deposit_amount, remaining_amount, forfeited_amount, created_at, updated_at
)
SELECT
    ('e3900000-0000-4000-8000-' || lpad(k.i::text, 12, '0'))::uuid,
    u.id,
    r.id,
    s.bat_dau,
    s.bat_dau + INTERVAL '15 minutes',
    15000,
    'COMPLETED',
    'PAID',
    15000,
    0,
    0,
    s.bat_dau - INTERVAL '2 days',
    s.bat_dau
FROM khach k
JOIN users u
  ON u.email = 'seed.customer.' || lpad(k.i::text, 2, '0') || '@astrotarot.demo'
CROSS JOIN reader r
JOIN slot_xep s ON s.stt = k.i
WHERE NOT EXISTS (
    SELECT 1 FROM bookings b
    WHERE b.id = ('e3900000-0000-4000-8000-' || lpad(k.i::text, 12, '0'))::uuid
);

-- ---------- 3. Giao dịch giá thấp, gắn với booking ----------
INSERT INTO payment_transactions (
    id, booking_id, user_id, amount, payment_method,
    external_transaction_id, status, phase, metadata, created_at
)
SELECT
    ('f3900000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid,
    b.id,
    b.user_id,
    b.total_amount,
    'SEED_DEMO',
    'SEED-LOW-REVENUE-' || lpad(g.i::text, 2, '0'),
    'SUCCESS',
    'FULL',
    jsonb_build_object('nguon', 'seed doanh thu tinh gon'),
    b.created_at
FROM generate_series(1, 18) AS g(i)
JOIN bookings b
  ON b.id = ('e3900000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid
WHERE NOT EXISTS (
    SELECT 1 FROM payment_transactions p
    WHERE p.id = ('f3900000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid
       OR p.external_transaction_id = 'SEED-LOW-REVENUE-' || lpad(g.i::text, 2, '0')
);

INSERT INTO reviews (
    id, booking_id, user_id, reader_profile_id, rating, comment, created_at
)
SELECT
    ('c3900000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid,
    b.id,
    b.user_id,
    b.reader_profile_id,
    CASE WHEN g.i % 4 = 0 THEN 4 ELSE 5 END,
    (ARRAY[
        'Buổi xem ngắn gọn, mình có thêm góc nhìn để tự quyết định.',
        'Reader lắng nghe kỹ và giải thích từng ý dễ hiểu.',
        'Đặt lịch thuận tiện, nội dung buổi xem đúng điều mình cần.',
        'Trao đổi nhẹ nhàng, mình thấy rõ hơn việc nên làm tiếp theo.'
    ])[1 + ((g.i - 1) % 4)],
    b.end_time + INTERVAL '3 hours'
FROM generate_series(1, 18) AS g(i)
JOIN bookings b
  ON b.id = ('e3900000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid
WHERE NOT EXISTS (
    SELECT 1 FROM reviews r
    WHERE r.id = ('c3900000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid
       OR r.booking_id = b.id
);

-- ---------- 4. Huỷ thanh toán seed cũ và đồng bộ booking ----------
UPDATE payment_transactions p
SET status = 'CANCELLED'
WHERE p.status = 'SUCCESS'
  AND CAST(p.id AS varchar) LIKE '%-0000-4000-8000-%'
  AND CAST(p.id AS varchar) NOT LIKE 'f3900000-%';

UPDATE bookings b
SET payment_status = 'UNPAID',
    deposit_amount = 0,
    remaining_amount = b.total_amount,
    updated_at = NOW()
WHERE EXISTS (
    SELECT 1
    FROM payment_transactions p
    WHERE p.booking_id = b.id
      AND p.status = 'CANCELLED'
      AND CAST(p.id AS varchar) LIKE '%-0000-4000-8000-%'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM payment_transactions p
      WHERE p.booking_id = b.id
        AND p.status = 'SUCCESS'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM wallet_transactions w
      WHERE w.type = 'BOOKING_PAYMENT'
        AND w.status = 'SUCCESS'
        AND w.reference_id = b.id::text
  )
  AND b.payment_status IS DISTINCT FROM 'UNPAID';

-- Đồng bộ điểm Reader với các đánh giá mới, không cập nhật hồ sơ nếu đã khớp.
UPDATE reader_profiles rp
SET rating = COALESCE(r.avg_rating, 0),
    total_reviews = COALESCE(r.cnt, 0),
    updated_at = NOW()
FROM (
    SELECT p.id AS profile_id,
           ROUND(AVG(rv.rating)::numeric, 2) AS avg_rating,
           COUNT(rv.id) AS cnt
    FROM reader_profiles p
    LEFT JOIN reviews rv ON rv.reader_profile_id = p.id
    GROUP BY p.id
) r
WHERE rp.id = r.profile_id
  AND (rp.rating IS DISTINCT FROM COALESCE(r.avg_rating, 0)
       OR rp.total_reviews IS DISTINCT FROM COALESCE(r.cnt, 0));
