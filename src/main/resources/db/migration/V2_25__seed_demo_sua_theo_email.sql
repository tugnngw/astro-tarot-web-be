-- ============================================================
-- Làm lại phần seed mà V2_23 bỏ sót.
--
-- ------------------------------------------------------------
-- V2_23 sai ở đâu
-- ------------------------------------------------------------
-- Nó chặn trùng hồ sơ Reader bằng NOT EXISTS:
--
--     INSERT INTO reader_profiles ... FROM users u
--     WHERE u.email = 'tranduydatqtdl@gmail.com'
--       AND NOT EXISTS (SELECT 1 FROM reader_profiles p WHERE p.user_id = u.id)
--
-- rồi những khối SAU lại hardcode id 'd9000000-…-01' của hồ sơ ấy.
--
-- Tài khoản này đã có hồ sơ Reader từ trước, nên nhánh tạo bị bỏ qua — đúng
-- như ý định. Nhưng id 'd9000000-…-01' vì thế không bao giờ tồn tại, và sáu
-- buổi hẹn kèm sáu đánh giá đều rơi vào guard `EXISTS (... p.id = 'd9000000')`
-- nên không chèn dòng nào. Migration chạy xong, Flyway ghi thành công, mà kết
-- quả là con số không.
--
-- Bài học: chặn trùng bằng một điều kiện (user_id) rồi tham chiếu bằng một
-- khoá khác (id cố định) thì hai thứ đó rời nhau ngay lần đầu dữ liệu không
-- như mình đoán.
--
-- Nhìn thấy được vì production trả về Reader "Đạt Trần" với rating 5.00 và
-- đúng 1 đánh giá, thay vì 4.67 và 6 đánh giá như seed lẽ ra phải tạo.
--
-- V2_23 đã nằm trong flyway_schema_history của production nên không sửa lại
-- được — đổi nội dung là đổi checksum, lần khởi động sau sẽ chết vì lỗi khác.
-- Nên làm lại ở file mới.
--
-- ------------------------------------------------------------
-- Lần này tra hồ sơ theo EMAIL, không hardcode id
-- ------------------------------------------------------------
-- Mọi khối dưới đây đều bắt đầu từ users.email rồi join sang reader_profiles.
-- Tài khoản đã có hồ sơ hay chưa đều ra cùng một kết quả.
--
-- Vẫn là DỮ LIỆU DEMO, không phải minh chứng giao dịch — xem khối cảnh báo
-- trong V2_23, nguyên văn vẫn đúng.
-- ============================================================

-- ---------- 1. Bảo đảm có hồ sơ Reader ----------
-- Chỉ tạo khi thật sự chưa có. Có rồi thì giữ nguyên hồ sơ người ta đã khai,
-- không ghi đè bio hay giá — đó là nội dung thật của họ.
INSERT INTO reader_profiles (
    id, user_id, bio, specialties, years_experience,
    rating, total_reviews, is_available, verified_at,
    price_per_15m, price_per_30m, price_per_60m,
    created_at, updated_at
)
SELECT
    gen_random_uuid(), u.id,
    'Mình đọc Tarot kết hợp bản đồ sao, tập trung vào chuyện học hành, '
    || 'định hướng nghề và các mối quan hệ ở tuổi hai mươi.',
    ARRAY['Tình yêu', 'Sự nghiệp', 'Học tập'],
    3, 0, 0, TRUE, NOW() - interval '60 days',
    90000, 170000, 300000,
    NOW() - interval '60 days', NOW()
FROM users u
WHERE u.email = 'tranduydatqtdl@gmail.com'
  AND NOT EXISTS (SELECT 1 FROM reader_profiles p WHERE p.user_id = u.id);

-- ---------- 2. Lịch tuần T2–T6 ----------
INSERT INTO reader_availability (id, reader_id, day_of_week, start_time, end_time, is_active)
SELECT gen_random_uuid(), p.id, d.dow, TIME '09:00', TIME '17:00', TRUE
FROM users u
JOIN reader_profiles p ON p.user_id = u.id
CROSS JOIN (VALUES (1), (2), (3), (4), (5)) AS d(dow)
WHERE u.email = 'tranduydatqtdl@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM reader_availability a
      WHERE a.reader_id = p.id AND a.day_of_week = d.dow
  );

-- ---------- 3. Sáu buổi đã hoàn tất ----------
-- Buổi 1 là của alloffdenchi13 — buổi nhóm mở ra lúc demo.
-- Giờ lệch nhau và lùi về quá khứ nên không đụng no_overlapping_bookings;
-- vẫn kiểm chồng giờ trước khi chèn, phòng trường hợp hồ sơ có sẵn đã có buổi
-- vào đúng khung ấy.
INSERT INTO bookings (
    id, user_id, reader_profile_id, start_time, end_time,
    total_amount, status, payment_status,
    deposit_amount, remaining_amount, created_at, updated_at
)
SELECT
    ('e5000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid,
    ku.id, p.id,
    date_trunc('day', NOW()) - (b.ngay || ' days')::interval + (b.gio || ' hours')::interval,
    date_trunc('day', NOW()) - (b.ngay || ' days')::interval + (b.gio || ' hours')::interval + interval '60 minutes',
    300000, 'COMPLETED', 'PAID', 300000, 0,
    date_trunc('day', NOW()) - ((b.ngay + 3) || ' days')::interval,
    date_trunc('day', NOW()) - (b.ngay || ' days')::interval
FROM (VALUES
    (1, 6,  10, 'alloffdenchi13@gmail.com'),
    (2, 11, 14, 'user.an@astrotarot.demo'),
    (3, 15, 9,  'user.bich@astrotarot.demo'),
    (4, 20, 16, 'user.cuong@astrotarot.demo'),
    (5, 27, 11, 'user.an@astrotarot.demo'),
    (6, 34, 15, 'user.bich@astrotarot.demo')
) AS b(i, ngay, gio, email)
JOIN users ku ON ku.email = b.email
JOIN users ru ON ru.email = 'tranduydatqtdl@gmail.com'
JOIN reader_profiles p ON p.user_id = ru.id
WHERE NOT EXISTS (
    SELECT 1 FROM bookings x
    WHERE x.id = ('e5000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid
)
AND NOT EXISTS (
    SELECT 1 FROM bookings y
    WHERE y.reader_profile_id = p.id
      AND y.status <> 'CANCELLED'
      AND tstzrange(y.start_time, y.end_time) && tstzrange(
          date_trunc('day', NOW()) - (b.ngay || ' days')::interval + (b.gio || ' hours')::interval,
          date_trunc('day', NOW()) - (b.ngay || ' days')::interval + (b.gio || ' hours')::interval + interval '60 minutes'
      )
);

-- ---------- 4. Giao dịch ----------
INSERT INTO payment_transactions (
    id, booking_id, user_id, amount, payment_method,
    external_transaction_id, status, phase, metadata, created_at
)
SELECT
    ('f5000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid,
    bk.id, bk.user_id, bk.total_amount,
    'SEED_DEMO',
    'SEED-DEMO-25-' || lpad(b.i::text, 4, '0'),
    'SUCCESS', 'FULL',
    jsonb_build_object(
        'nguon', 'migration V2_25',
        'ghi_chu', 'Du lieu demo. Khong co giao dich that, khong doi chieu duoc voi PayOS.'
    ),
    bk.start_time - interval '2 days'
FROM (VALUES (1), (2), (3), (4), (5), (6)) AS b(i)
JOIN bookings bk ON bk.id = ('e5000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid
WHERE NOT EXISTS (
    SELECT 1 FROM payment_transactions t
    WHERE t.id = ('f5000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid
);

-- ---------- 5. Đánh giá ----------
-- Không cho toàn 5 sao: một hồ sơ toàn năm sao trông như hàng mua, và câu hỏi
-- đầu tiên lúc demo sẽ là "số này thật không".
INSERT INTO reviews (id, booking_id, user_id, reader_profile_id, rating, comment, created_at)
SELECT
    ('c5000000-0000-4000-8000-' || lpad(r.i::text, 12, '0'))::uuid,
    bk.id, bk.user_id, bk.reader_profile_id, r.sao, r.nhan_xet,
    bk.end_time + interval '3 hours'
FROM (VALUES
    (1, 5, 'Mình hỏi về chuyện chọn chuyên ngành, anh đọc bài xong hỏi ngược lại mấy câu làm mình tự trả lời được. Đáng tiền.'),
    (2, 5, 'Đúng giờ, nói rõ ràng, không doạ. Chỗ nào bài không thuận thì nói thẳng chứ không vòng vo.'),
    (3, 4, 'Buổi xem tốt, mình thích cách giải thích từng lá. Trừ một sao vì phần đầu hơi dài, muốn vào ý chính sớm hơn.'),
    (4, 5, 'Lần thứ hai đặt. Nhớ được chuyện lần trước mình kể, nên không phải giải thích lại từ đầu.'),
    (5, 4, 'Nội dung ổn và thật. Mình mong có thêm phần ghi chú sau buổi để đọc lại.'),
    (6, 5, 'Hỏi chuyện gia đình, được nghe một góc nhìn mình chưa từng nghĩ tới. Cảm ơn anh.')
) AS r(i, sao, nhan_xet)
JOIN bookings bk ON bk.id = ('e5000000-0000-4000-8000-' || lpad(r.i::text, 12, '0'))::uuid
WHERE NOT EXISTS (
    SELECT 1 FROM reviews v
    WHERE v.id = ('c5000000-0000-4000-8000-' || lpad(r.i::text, 12, '0'))::uuid
)
AND NOT EXISTS (SELECT 1 FROM reviews v2 WHERE v2.booking_id = bk.id);

-- ---------- 6. Tính lại điểm ----------
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
