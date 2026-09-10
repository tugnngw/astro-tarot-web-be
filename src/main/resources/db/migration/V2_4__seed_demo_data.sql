-- ============================================================
-- Dữ liệu demo để test toàn bộ vai trò và luồng chính.
--
-- Idempotent: chạy lại không nhân đôi (WHERE NOT EXISTS / ON CONFLICT).
-- Mật khẩu MỌI tài khoản demo: admin123
-- (cùng bcrypt hash với admin seed ở V1_2).
--
-- Tài khoản:
--   admin@example.com              ADMIN
--   manager@astrotarot.demo        MANAGER
--   staff.support@astrotarot.demo  STAFF (hỗ trợ, có hồ sơ Reader)
--   staff.lan@astrotarot.demo      STAFF + Reader (đã duyệt, đang nhận lịch)
--   staff.minh@astrotarot.demo     STAFF + Reader
--   user.an@astrotarot.demo        USER (khách thường)
--   user.bich@astrotarot.demo      USER (đã có booking / đánh giá)
--   user.cuong@astrotarot.demo     USER (đơn xin làm Reader đang chờ)
--   user.banned@astrotarot.demo    USER bị BAN (thử màn khoá tài khoản)
-- ============================================================

-- BCrypt (strength 10) của chuỗi "admin123"
-- $2a$10$blHfFtzwiJo6RwZUueJ1AOJKbVrXfIWNhsebN9gKESfQB/rvgRpOa

-- ---------- Tài khoản ----------
INSERT INTO users (
    id, username, email, password_hash, full_name, role, status,
    email_verified, email_verified_at, auth_provider, gender, phone, city, country,
    bio, created_at, updated_at
)
SELECT * FROM (VALUES
    ('a1000000-0000-4000-8000-000000000001'::uuid, 'manager_demo',
     'manager@astrotarot.demo',
     '$2a$10$blHfFtzwiJo6RwZUueJ1AOJKbVrXfIWNhsebN9gKESfQB/rvgRpOa',
     'Nguyễn Quản Lý', 'MANAGER', 'ACTIVE',
     TRUE, NOW(), 'LOCAL', 'FEMALE', '0901000001', 'Hà Nội', 'VN',
     'Quản lý nhân sự và duyệt hồ sơ Reader.', NOW(), NOW()),

    ('a1000000-0000-4000-8000-000000000002'::uuid, 'staff_support',
     'staff.support@astrotarot.demo',
     '$2a$10$blHfFtzwiJo6RwZUueJ1AOJKbVrXfIWNhsebN9gKESfQB/rvgRpOa',
     'Trần Hỗ Trợ', 'STAFF', 'ACTIVE',
     TRUE, NOW(), 'LOCAL', 'MALE', '0901000002', 'Hà Nội', 'VN',
     'Nhân viên hỗ trợ khách; kiêm nhận booking Reader.', NOW(), NOW()),

    ('a1000000-0000-4000-8000-000000000003'::uuid, 'staff_lan',
     'staff.lan@astrotarot.demo',
     '$2a$10$blHfFtzwiJo6RwZUueJ1AOJKbVrXfIWNhsebN9gKESfQB/rvgRpOa',
     'Lê Thu Lan', 'STAFF', 'ACTIVE',
     TRUE, NOW(), 'LOCAL', 'FEMALE', '0901000003', 'Đà Nẵng', 'VN',
     'Reader chuyên tình cảm và sự nghiệp.', NOW(), NOW()),

    ('a1000000-0000-4000-8000-000000000004'::uuid, 'staff_minh',
     'staff.minh@astrotarot.demo',
     '$2a$10$blHfFtzwiJo6RwZUueJ1AOJKbVrXfIWNhsebN9gKESfQB/rvgRpOa',
     'Phạm Quang Minh', 'STAFF', 'ACTIVE',
     TRUE, NOW(), 'LOCAL', 'MALE', '0901000004', 'TP. Hồ Chí Minh', 'VN',
     'Reader chuyên tài chính và quyết định lớn.', NOW(), NOW()),

    ('a1000000-0000-4000-8000-000000000005'::uuid, 'user_an',
     'user.an@astrotarot.demo',
     '$2a$10$blHfFtzwiJo6RwZUueJ1AOJKbVrXfIWNhsebN9gKESfQB/rvgRpOa',
     'Hoàng Văn An', 'USER', 'ACTIVE',
     TRUE, NOW(), 'LOCAL', 'MALE', '0901000005', 'Hải Phòng', 'VN',
     'Thành viên mới, thích trải bài AI.', NOW(), NOW()),

    ('a1000000-0000-4000-8000-000000000006'::uuid, 'user_bich',
     'user.bich@astrotarot.demo',
     '$2a$10$blHfFtzwiJo6RwZUueJ1AOJKbVrXfIWNhsebN9gKESfQB/rvgRpOa',
     'Đỗ Ngọc Bích', 'USER', 'ACTIVE',
     TRUE, NOW(), 'LOCAL', 'FEMALE', '0901000006', 'Huế', 'VN',
     'Đã đặt lịch Reader và để lại đánh giá.', NOW(), NOW()),

    ('a1000000-0000-4000-8000-000000000007'::uuid, 'user_cuong',
     'user.cuong@astrotarot.demo',
     '$2a$10$blHfFtzwiJo6RwZUueJ1AOJKbVrXfIWNhsebN9gKESfQB/rvgRpOa',
     'Vũ Thành Cường', 'USER', 'ACTIVE',
     TRUE, NOW(), 'LOCAL', 'MALE', '0901000007', 'Cần Thơ', 'VN',
     'Đang nộp hồ sơ xin làm Reader.', NOW(), NOW()),

    ('a1000000-0000-4000-8000-000000000008'::uuid, 'user_banned',
     'user.banned@astrotarot.demo',
     '$2a$10$blHfFtzwiJo6RwZUueJ1AOJKbVrXfIWNhsebN9gKESfQB/rvgRpOa',
     'Tài Khoản Bị Khoá', 'USER', 'BANNED',
     TRUE, NOW(), 'LOCAL', 'UNDISCLOSED', NULL, NULL, 'VN',
     'Dùng để thử màn tài khoản bị khoá.', NOW(), NOW())
) AS v(id, username, email, password_hash, full_name, role, status,
       email_verified, email_verified_at, auth_provider, gender, phone, city, country,
       bio, created_at, updated_at)
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.email = v.email OR u.username = v.username
);

-- Đảm bảo admin cũ (V1_2) đã xác minh email để đăng nhập không bị chặn.
UPDATE users
SET email_verified = TRUE,
    email_verified_at = COALESCE(email_verified_at, NOW()),
    status = 'ACTIVE'
WHERE email = 'admin@example.com';

-- ---------- Hồ sơ Reader (STAFF nhận booking) ----------
INSERT INTO reader_profiles (
    id, user_id, bio, specialties, years_experience,
    price_per_15m, price_per_30m, price_per_60m,
    rating, total_reviews, is_available, verified_at, created_at, updated_at
)
SELECT * FROM (VALUES
    ('b1000000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000002'::uuid,
     'Mình hỗ trợ khách hằng ngày và nhận buổi xem ngắn khi còn lịch trống.',
     ARRAY['Hỗ trợ chung', 'Tarot cơ bản']::text[],
     2, 150000::bigint, 280000::bigint, 500000::bigint,
     4.50::numeric, 2, TRUE, NOW() - INTERVAL '60 days', NOW(), NOW()),

    ('b1000000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000003'::uuid,
     'Chuyên tình cảm, mối quan hệ và định hướng sự nghiệp. Phong cách nhẹ nhàng, nói thẳng nhưng không phán xét.',
     ARRAY['Tình cảm', 'Sự nghiệp', 'Tarot']::text[],
     5, 200000::bigint, 350000::bigint, 650000::bigint,
     4.80::numeric, 1, TRUE, NOW() - INTERVAL '120 days', NOW(), NOW()),

    ('b1000000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000004'::uuid,
     'Nhìn bài theo hướng thực dụng: tiền bạc, quyết định lớn, timing. Có nền tảng chiêm tinh.',
     ARRAY['Tài chính', 'Quyết định', 'Chiêm tinh']::text[],
     7, 250000::bigint, 450000::bigint, 800000::bigint,
     4.20::numeric, 0, TRUE, NOW() - INTERVAL '90 days', NOW(), NOW())
) AS v(id, user_id, bio, specialties, years_experience,
       price_per_15m, price_per_30m, price_per_60m,
       rating, total_reviews, is_available, verified_at, created_at, updated_at)
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = v.user_id)
  AND NOT EXISTS (SELECT 1 FROM reader_profiles rp WHERE rp.user_id = v.user_id);

-- Lịch tuần: T2–T6 09:00–12:00 và 14:00–18:00 (0=CN … 6=T7)
INSERT INTO reader_availability (id, reader_id, day_of_week, start_time, end_time, is_active)
SELECT gen_random_uuid(), r.id, d.dow, t.start_t, t.end_t, TRUE
FROM reader_profiles r
CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS d(dow)
CROSS JOIN (VALUES
    (TIME '09:00', TIME '12:00'),
    (TIME '14:00', TIME '18:00')
) AS t(start_t, end_t)
WHERE r.id IN (
    'b1000000-0000-4000-8000-000000000001',
    'b1000000-0000-4000-8000-000000000002',
    'b1000000-0000-4000-8000-000000000003'
)
AND NOT EXISTS (
    SELECT 1 FROM reader_availability a
    WHERE a.reader_id = r.id
      AND a.day_of_week = d.dow
      AND a.start_time = t.start_t
      AND a.end_time = t.end_t
);

-- Một ngày bận của Lan (ngày mai) để test unavailable
INSERT INTO reader_unavailable_dates (id, reader_id, unavailable_date, reason, created_at)
SELECT
    'b2000000-0000-4000-8000-000000000001'::uuid,
    'b1000000-0000-4000-8000-000000000002'::uuid,
    (CURRENT_DATE + 1),
    'Nghỉ việc riêng',
    NOW()
WHERE EXISTS (SELECT 1 FROM reader_profiles WHERE id = 'b1000000-0000-4000-8000-000000000002')
  AND NOT EXISTS (
      SELECT 1 FROM reader_unavailable_dates
      WHERE id = 'b2000000-0000-4000-8000-000000000001'
  );

-- ---------- Hồ sơ chiêm tinh (plaintext + ciphertext giả; decrypt lỗi thì fallback plaintext) ----------
INSERT INTO user_astrological_data (
    id, user_id, profile_type, title, target_name,
    birth_date, birth_time, birth_place, latitude, longitude, timezone,
    encrypted_data, encryption_iv, is_primary, created_at, updated_at
)
SELECT * FROM (VALUES
    ('c1000000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     'SELF', 'Hồ sơ của An', 'Hoàng Văn An',
     DATE '1998-04-10', TIME '12:00', 'Hải Phòng',
     20.8449000::numeric, 106.6881000::numeric, 'Asia/Ho_Chi_Minh',
     '\x0000000000000000000000000000000000000000000000000000000000000000'::bytea,
     '\x000000000000000000000000'::bytea,
     TRUE, NOW(), NOW()),

    ('c1000000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     'SELF', 'Hồ sơ của Bích', 'Đỗ Ngọc Bích',
     DATE '1995-08-22', TIME '09:30', 'Huế',
     16.4637000::numeric, 107.5909000::numeric, 'Asia/Ho_Chi_Minh',
     '\x0000000000000000000000000000000000000000000000000000000000000000'::bytea,
     '\x000000000000000000000000'::bytea,
     TRUE, NOW(), NOW()),

    ('c1000000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     'OTHER', 'Người ấy', 'Trần Văn Nam',
     DATE '1994-01-15', TIME '12:00', 'Hà Nội',
     21.0285000::numeric, 105.8542000::numeric, 'Asia/Ho_Chi_Minh',
     '\x0000000000000000000000000000000000000000000000000000000000000000'::bytea,
     '\x000000000000000000000000'::bytea,
     FALSE, NOW(), NOW())
) AS v(id, user_id, profile_type, title, target_name,
       birth_date, birth_time, birth_place, latitude, longitude, timezone,
       encrypted_data, encryption_iv, is_primary, created_at, updated_at)
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = v.user_id)
  AND NOT EXISTS (SELECT 1 FROM user_astrological_data a WHERE a.id = v.id);

-- ---------- Hồ sơ xin làm Reader ----------
INSERT INTO reader_applications (
    id, user_id, bio, experience, specialties, status,
    reviewed_by, reviewed_at, rejection_reason, created_at, updated_at
)
SELECT * FROM (VALUES
    -- Đang chờ duyệt (Manager/Admin thử duyệt)
    ('d1000000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000007'::uuid,
     'Mình học Tarot 3 năm, từng đọc cho bạn bè và nhóm nhỏ. Muốn nhận khách trên nền tảng.',
     3,
     ARRAY['Tarot', 'Trực giác']::text[],
     'PENDING',
     NULL::uuid, NULL::timestamptz, NULL::text, NOW() - INTERVAL '2 days', NOW()),

    -- Đã duyệt (Lan) — lịch sử
    ('d1000000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000003'::uuid,
     'Hồ sơ đã được duyệt trước đây.',
     5,
     ARRAY['Tình cảm', 'Sự nghiệp']::text[],
     'APPROVED',
     (SELECT id FROM users WHERE email = 'admin@example.com' LIMIT 1),
     NOW() - INTERVAL '120 days', NULL::text, NOW() - INTERVAL '130 days', NOW()),

    -- Bị từ chối mẫu
    ('d1000000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     'Xin làm Reader nhưng chưa đủ kinh nghiệm (mẫu bị từ chối).',
     0,
     ARRAY['Tarot']::text[],
     'REJECTED',
     (SELECT id FROM users WHERE email = 'manager@astrotarot.demo' LIMIT 1),
     NOW() - INTERVAL '10 days',
     'Chưa đủ kinh nghiệm thực tế. Hãy quay lại sau khi có thêm buổi đọc thử.',
     NOW() - INTERVAL '14 days', NOW())
) AS v(id, user_id, bio, experience, specialties, status,
       reviewed_by, reviewed_at, rejection_reason, created_at, updated_at)
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = v.user_id)
  AND NOT EXISTS (SELECT 1 FROM reader_applications a WHERE a.id = v.id);

-- ---------- Bookings (không chồng giờ cùng Reader) ----------
-- Dùng múi giờ VN (+07) cho khung giờ dễ đọc.
INSERT INTO bookings (
    id, user_id, reader_profile_id, start_time, end_time,
    total_amount, status, payment_status, cancel_reason, created_at, updated_at
)
SELECT * FROM (VALUES
    -- Bích × Lan: đã hoàn tất + đã thanh toán (để có review)
    ('e1000000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     'b1000000-0000-4000-8000-000000000002'::uuid,
     ((CURRENT_DATE - 3)::timestamp + TIME '10:00') AT TIME ZONE 'Asia/Ho_Chi_Minh',
     ((CURRENT_DATE - 3)::timestamp + TIME '10:30') AT TIME ZONE 'Asia/Ho_Chi_Minh',
     350000::bigint, 'COMPLETED', 'PAID', NULL::text,
     NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days'),

    -- An × Lan: đã xác nhận, chờ buổi xem
    ('e1000000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     'b1000000-0000-4000-8000-000000000002'::uuid,
     ((CURRENT_DATE + 2)::timestamp + TIME '15:00') AT TIME ZONE 'Asia/Ho_Chi_Minh',
     ((CURRENT_DATE + 2)::timestamp + TIME '15:30') AT TIME ZONE 'Asia/Ho_Chi_Minh',
     350000::bigint, 'CONFIRMED', 'PAID', NULL::text,
     NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),

    -- Bích × Minh: đang chờ Reader xác nhận
    ('e1000000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     'b1000000-0000-4000-8000-000000000003'::uuid,
     ((CURRENT_DATE + 3)::timestamp + TIME '09:00') AT TIME ZONE 'Asia/Ho_Chi_Minh',
     ((CURRENT_DATE + 3)::timestamp + TIME '10:00') AT TIME ZONE 'Asia/Ho_Chi_Minh',
     800000::bigint, 'PENDING', 'UNPAID', NULL::text,
     NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours'),

    -- An × Support: đã huỷ
    ('e1000000-0000-4000-8000-000000000004'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     'b1000000-0000-4000-8000-000000000001'::uuid,
     ((CURRENT_DATE - 1)::timestamp + TIME '16:00') AT TIME ZONE 'Asia/Ho_Chi_Minh',
     ((CURRENT_DATE - 1)::timestamp + TIME '16:15') AT TIME ZONE 'Asia/Ho_Chi_Minh',
     150000::bigint, 'CANCELLED', 'REFUNDED', 'Khách bận đột xuất',
     NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days')
) AS v(id, user_id, reader_profile_id, start_time, end_time,
       total_amount, status, payment_status, cancel_reason, created_at, updated_at)
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = v.user_id)
  AND EXISTS (SELECT 1 FROM reader_profiles r WHERE r.id = v.reader_profile_id)
  AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.id = v.id);

-- ---------- Thanh toán + ký quỹ + rút tiền ----------
INSERT INTO payment_transactions (
    id, booking_id, user_id, amount, payment_method,
    external_transaction_id, status, metadata, created_at
)
SELECT * FROM (VALUES
    ('f1000000-0000-4000-8000-000000000001'::uuid,
     'e1000000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     350000::bigint, 'BANK_TRANSFER',
     'SEED-PAY-001', 'SUCCESS',
     '{"note":"seed completed booking"}'::jsonb,
     NOW() - INTERVAL '4 days'),

    ('f1000000-0000-4000-8000-000000000002'::uuid,
     'e1000000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     350000::bigint, 'BANK_TRANSFER',
     'SEED-PAY-002', 'SUCCESS',
     '{"note":"seed confirmed booking"}'::jsonb,
     NOW() - INTERVAL '1 day'),

    ('f1000000-0000-4000-8000-000000000003'::uuid,
     'e1000000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     800000::bigint, 'BANK_TRANSFER',
     'SEED-PAY-003', 'PENDING',
     '{"note":"seed awaiting payment"}'::jsonb,
     NOW() - INTERVAL '6 hours')
) AS v(id, booking_id, user_id, amount, payment_method,
       external_transaction_id, status, metadata, created_at)
WHERE EXISTS (SELECT 1 FROM bookings b WHERE b.id = v.booking_id)
  AND NOT EXISTS (SELECT 1 FROM payment_transactions p WHERE p.id = v.id);

INSERT INTO escrow_accounts (
    id, user_id, balance, pending_balance, total_earned, total_withdrawn, updated_at
)
SELECT * FROM (VALUES
    ('f2000000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000003'::uuid,
     350000::bigint, 350000::bigint, 700000::bigint, 0::bigint, NOW()),
    ('f2000000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000004'::uuid,
     0::bigint, 0::bigint, 0::bigint, 0::bigint, NOW()),
    ('f2000000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000002'::uuid,
     100000::bigint, 0::bigint, 100000::bigint, 0::bigint, NOW())
) AS v(id, user_id, balance, pending_balance, total_earned, total_withdrawn, updated_at)
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = v.user_id)
  AND NOT EXISTS (SELECT 1 FROM escrow_accounts e WHERE e.user_id = v.user_id);

INSERT INTO payout_requests (
    id, reader_id, amount, bank_name, bank_account, account_holder,
    status, requested_at, processed_at, reject_reason
)
SELECT * FROM (VALUES
    ('f3000000-0000-4000-8000-000000000001'::uuid,
     'b1000000-0000-4000-8000-000000000002'::uuid,
     200000::bigint, 'Vietcombank', '0123456789', 'LE THU LAN',
     'PENDING', NOW() - INTERVAL '12 hours', NULL::timestamptz, NULL::text),
    ('f3000000-0000-4000-8000-000000000002'::uuid,
     'b1000000-0000-4000-8000-000000000001'::uuid,
     50000::bigint, 'MB Bank', '0987654321', 'TRAN HO TRO',
     'REJECTED', NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days',
     'Sai tên chủ tài khoản so với CCCD.')
) AS v(id, reader_id, amount, bank_name, bank_account, account_holder,
       status, requested_at, processed_at, reject_reason)
WHERE EXISTS (SELECT 1 FROM reader_profiles r WHERE r.id = v.reader_id)
  AND NOT EXISTS (SELECT 1 FROM payout_requests p WHERE p.id = v.id);

-- ---------- Đánh giá ----------
INSERT INTO reviews (
    id, booking_id, user_id, reader_profile_id, rating, comment, created_at
)
SELECT
    'e2000000-0000-4000-8000-000000000001'::uuid,
    'e1000000-0000-4000-8000-000000000001'::uuid,
    'a1000000-0000-4000-8000-000000000006'::uuid,
    'b1000000-0000-4000-8000-000000000002'::uuid,
    5,
    'Lan đọc rất tinh tế, mình thấy nhẹ lòng hơn sau buổi xem.',
    NOW() - INTERVAL '3 days'
WHERE EXISTS (SELECT 1 FROM bookings WHERE id = 'e1000000-0000-4000-8000-000000000001')
  AND NOT EXISTS (SELECT 1 FROM reviews WHERE id = 'e2000000-0000-4000-8000-000000000001');

-- ---------- Báo cáo vi phạm ----------
INSERT INTO reports (
    id, reporter_user_id, reported_user_id, booking_id,
    report_type, description, status,
    handled_by, handled_at, resolution_note, created_at
)
SELECT * FROM (VALUES
    ('aa100000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     'a1000000-0000-4000-8000-000000000004'::uuid,
     NULL::uuid,
     'INAPPROPRIATE',
     'Tin nhắn mẫu để Manager thử hàng chờ báo cáo (chưa xử lý).',
     'PENDING',
     NULL::uuid, NULL::timestamptz, NULL::text,
     NOW() - INTERVAL '8 hours'),

    ('aa100000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     'a1000000-0000-4000-8000-000000000008'::uuid,
     NULL::uuid,
     'SPAM',
     'Tài khoản này spam (đã xử lý mẫu).',
     'RESOLVED',
     (SELECT id FROM users WHERE email = 'manager@astrotarot.demo' LIMIT 1),
     NOW() - INTERVAL '1 day',
     'Đã khoá tài khoản vi phạm.',
     NOW() - INTERVAL '2 days')
) AS v(id, reporter_user_id, reported_user_id, booking_id,
       report_type, description, status,
       handled_by, handled_at, resolution_note, created_at)
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = v.reporter_user_id)
  AND EXISTS (SELECT 1 FROM users u2 WHERE u2.id = v.reported_user_id)
  AND NOT EXISTS (SELECT 1 FROM reports r WHERE r.id = v.id);

-- ---------- Hỗ trợ khách ----------
INSERT INTO support_tickets (
    id, user_id, subject, status, assigned_to, created_at, updated_at
)
SELECT * FROM (VALUES
    ('ab100000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     'Không nhận được mail xác nhận đặt lịch',
     'OPEN',
     NULL::uuid,
     NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'),

    ('ab100000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     'Hỏi về hoàn tiền khi Reader huỷ',
     'PENDING',
     'a1000000-0000-4000-8000-000000000002'::uuid,
     NOW() - INTERVAL '1 day', NOW() - INTERVAL '20 hours'),

    ('ab100000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000007'::uuid,
     'Cách nộp hồ sơ Reader',
     'RESOLVED',
     'a1000000-0000-4000-8000-000000000002'::uuid,
     NOW() - INTERVAL '7 days', NOW() - INTERVAL '6 days')
) AS v(id, user_id, subject, status, assigned_to, created_at, updated_at)
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = v.user_id)
  AND NOT EXISTS (SELECT 1 FROM support_tickets t WHERE t.id = v.id);

INSERT INTO support_ticket_messages (id, ticket_id, sender_id, body, created_at)
SELECT * FROM (VALUES
    ('ab200000-0000-4000-8000-000000000001'::uuid,
     'ab100000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     'Em đặt lịch với chị Lan nhưng chưa thấy mail xác nhận. Nhờ anh/chị kiểm tra giúp ạ.',
     NOW() - INTERVAL '3 hours'),

    ('ab200000-0000-4000-8000-000000000002'::uuid,
     'ab100000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000006'::uuid,
     'Nếu Reader huỷ buổi đã thanh toán thì tiền có về lại không ạ?',
     NOW() - INTERVAL '1 day'),

    ('ab200000-0000-4000-8000-000000000003'::uuid,
     'ab100000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000002'::uuid,
     'Chào bạn, nếu Reader huỷ thì hệ thống hoàn về theo chính sách ký quỹ trong 1–3 ngày làm việc. Bạn gửi giúp mã đơn để mình kiểm tra cụ thể nhé.',
     NOW() - INTERVAL '20 hours'),

    ('ab200000-0000-4000-8000-000000000004'::uuid,
     'ab100000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000007'::uuid,
     'Muốn xin làm Reader thì nộp ở đâu ạ?',
     NOW() - INTERVAL '7 days'),

    ('ab200000-0000-4000-8000-000000000005'::uuid,
     'ab100000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000002'::uuid,
     'Bạn vào hồ sơ → Đăng ký Reader, điền bio và chuyên môn rồi gửi. Quản lý sẽ duyệt trong vài ngày.',
     NOW() - INTERVAL '6 days 12 hours'),

    ('ab200000-0000-4000-8000-000000000006'::uuid,
     'ab100000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000007'::uuid,
     'Cảm ơn ạ, mình đã nộp rồi!',
     NOW() - INTERVAL '6 days')
) AS v(id, ticket_id, sender_id, body, created_at)
WHERE EXISTS (SELECT 1 FROM support_tickets t WHERE t.id = v.ticket_id)
  AND NOT EXISTS (SELECT 1 FROM support_ticket_messages m WHERE m.id = v.id);

-- ---------- Trải bài AI mẫu ----------
INSERT INTO tarot_readings (
    id, user_id, booking_id, astro_profile_id, session_type,
    main_question, ai_model_used, total_tokens_used, created_at, updated_at
)
SELECT
    'ad100000-0000-4000-8000-000000000001'::uuid,
    'a1000000-0000-4000-8000-000000000005'::uuid,
    NULL,
    'c1000000-0000-4000-8000-000000000001'::uuid,
    'AI',
    'Tuần này công việc của mình sẽ ra sao?',
    'seed-demo',
    1200,
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '2 days'
WHERE EXISTS (SELECT 1 FROM users WHERE id = 'a1000000-0000-4000-8000-000000000005')
  AND NOT EXISTS (SELECT 1 FROM tarot_readings WHERE id = 'ad100000-0000-4000-8000-000000000001');

INSERT INTO reading_cards (id, reading_id, card_id, position, is_reversed, interpretation)
SELECT
    gen_random_uuid(),
    'ad100000-0000-4000-8000-000000000001'::uuid,
    c.id,
    v.pos,
    v.rev,
    v.interp
FROM (VALUES
    (0, FALSE, 'Quá khứ: nền tảng vững nhưng hơi cứng nhắc.'),
    (1, TRUE,  'Hiện tại: cần linh hoạt hơn với đồng nghiệp.'),
    (2, FALSE, 'Tương lai: cơ hội mới mở ra nếu bạn chủ động.')
) AS v(pos, rev, interp)
JOIN LATERAL (
    SELECT id FROM tarot_cards ORDER BY card_number NULLS LAST, name LIMIT 1 OFFSET v.pos
) c ON TRUE
WHERE EXISTS (SELECT 1 FROM tarot_readings WHERE id = 'ad100000-0000-4000-8000-000000000001')
  AND NOT EXISTS (
      SELECT 1 FROM reading_cards rc
      WHERE rc.reading_id = 'ad100000-0000-4000-8000-000000000001'
        AND rc.position = v.pos
  );

INSERT INTO chat_sessions (
    id, user_id, tarot_reading_id, booking_id, session_type, status,
    last_message_at, created_at, updated_at
)
SELECT
    'ad200000-0000-4000-8000-000000000001'::uuid,
    'a1000000-0000-4000-8000-000000000005'::uuid,
    'ad100000-0000-4000-8000-000000000001'::uuid,
    NULL,
    'AI',
    'ACTIVE',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '2 days',
    NOW() - INTERVAL '2 days'
WHERE EXISTS (SELECT 1 FROM tarot_readings WHERE id = 'ad100000-0000-4000-8000-000000000001')
  AND NOT EXISTS (SELECT 1 FROM chat_sessions WHERE id = 'ad200000-0000-4000-8000-000000000001');

INSERT INTO chat_messages (id, session_id, sender_type, sender_id, content, message_type, created_at, updated_at)
SELECT * FROM (VALUES
    ('ad300000-0000-4000-8000-000000000001'::uuid,
     'ad200000-0000-4000-8000-000000000001'::uuid,
     'USER', 'a1000000-0000-4000-8000-000000000005'::uuid,
     'Tuần này công việc của mình sẽ ra sao?', 'TEXT',
     NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
    ('ad300000-0000-4000-8000-000000000002'::uuid,
     'ad200000-0000-4000-8000-000000000001'::uuid,
     'AI', NULL::uuid,
     'Ba lá bài gợi ý bạn đang đứng trước một bước chuyển nhẹ. Hãy mở lòng với góp ý và chủ động đề xuất ý tưởng mới.',
     'TEXT',
     NOW() - INTERVAL '2 days' + INTERVAL '1 minute',
     NOW() - INTERVAL '2 days' + INTERVAL '1 minute')
) AS v(id, session_id, sender_type, sender_id, content, message_type, created_at, updated_at)
WHERE EXISTS (SELECT 1 FROM chat_sessions s WHERE s.id = v.session_id)
  AND NOT EXISTS (SELECT 1 FROM chat_messages m WHERE m.id = v.id);

-- ---------- Thông báo ----------
INSERT INTO notifications (id, user_id, title, message, type, is_read, metadata, created_at)
SELECT * FROM (VALUES
    ('ae100000-0000-4000-8000-000000000001'::uuid,
     'a1000000-0000-4000-8000-000000000005'::uuid,
     'Lịch hẹn đã xác nhận',
     'Buổi xem với Lê Thu Lan đã được xác nhận.',
     'BOOKING_CONFIRMED', FALSE,
     '{"bookingId":"e1000000-0000-4000-8000-000000000002"}'::jsonb,
     NOW() - INTERVAL '1 day'),
    ('ae100000-0000-4000-8000-000000000002'::uuid,
     'a1000000-0000-4000-8000-000000000003'::uuid,
     'Có lịch hẹn mới',
     'Hoàng Văn An vừa đặt lịch với bạn.',
     'BOOKING_CONFIRMED', FALSE,
     '{"bookingId":"e1000000-0000-4000-8000-000000000002"}'::jsonb,
     NOW() - INTERVAL '1 day'),
    ('ae100000-0000-4000-8000-000000000003'::uuid,
     'a1000000-0000-4000-8000-000000000002'::uuid,
     'Ticket hỗ trợ mới',
     'Khách hỏi về mail xác nhận đặt lịch.',
     'SUPPORT', FALSE,
     '{"ticketId":"ab100000-0000-4000-8000-000000000001"}'::jsonb,
     NOW() - INTERVAL '3 hours'),
    ('ae100000-0000-4000-8000-000000000004'::uuid,
     (SELECT id FROM users WHERE email = 'admin@example.com' LIMIT 1),
     'Có hồ sơ Reader chờ duyệt',
     'Vũ Thành Cường vừa nộp hồ sơ xin làm Reader.',
     'READER_APPLICATION', FALSE,
     '{"applicationId":"d1000000-0000-4000-8000-000000000001"}'::jsonb,
     NOW() - INTERVAL '2 days')
) AS v(id, user_id, title, message, type, is_read, metadata, created_at)
WHERE v.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.id = v.id);

-- ---------- Lượt bấm affiliate (shop) ----------
INSERT INTO product_clicks (id, product_id, user_id, referrer, created_at)
SELECT
    gen_random_uuid(),
    p.id,
    'a1000000-0000-4000-8000-000000000005'::uuid,
    'seed',
    NOW() - (n || ' hours')::interval
FROM products p
CROSS JOIN generate_series(1, 3) AS n
WHERE p.slug IN ('rider-waite-tarot', 'moonology-oracle', 'khan-trai-bai-nhung')
  AND EXISTS (SELECT 1 FROM users WHERE id = 'a1000000-0000-4000-8000-000000000005')
  AND NOT EXISTS (
      SELECT 1 FROM product_clicks c
      WHERE c.product_id = p.id AND c.referrer = 'seed'
  );

UPDATE products p
SET click_count = GREATEST(
    COALESCE(p.click_count, 0),
    (SELECT COUNT(*) FROM product_clicks c WHERE c.product_id = p.id)
)
WHERE p.slug IN ('rider-waite-tarot', 'moonology-oracle', 'khan-trai-bai-nhung');

-- ---------- Activity log mẫu cho Admin ----------
INSERT INTO activity_logs (id, user_id, action, entity_type, entity_id, changes, created_at)
SELECT * FROM (VALUES
    ('af100000-0000-4000-8000-000000000001'::uuid,
     (SELECT id FROM users WHERE email = 'admin@example.com' LIMIT 1),
     'SEED_DEMO_DATA',
     'system',
     NULL::uuid,
     '{"note":"Flyway V2_4 seed demo data applied"}'::jsonb,
     NOW()),
    ('af100000-0000-4000-8000-000000000002'::uuid,
     (SELECT id FROM users WHERE email = 'manager@astrotarot.demo' LIMIT 1),
     'REPORT_RESOLVED',
     'report',
     'aa100000-0000-4000-8000-000000000002'::uuid,
     '{"status":"RESOLVED"}'::jsonb,
     NOW() - INTERVAL '1 day')
) AS v(id, user_id, action, entity_type, entity_id, changes, created_at)
WHERE v.user_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM activity_logs a WHERE a.id = v.id);

COMMENT ON TABLE users IS
    'Gồm tài khoản demo V2_4 (email *@astrotarot.demo, mật khẩu admin123) — xem SEED.md.';
