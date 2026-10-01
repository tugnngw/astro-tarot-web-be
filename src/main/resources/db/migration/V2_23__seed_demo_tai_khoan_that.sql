-- ============================================================
-- Dữ liệu demo cho hai tài khoản THẬT của nhóm.
--
-- V2_4 và V2_19 đã gieo đủ cho các tài khoản @astrotarot.demo, nhưng lúc
-- demo trước lớp thì nhóm đăng nhập bằng tài khoản thật của mình — và hai
-- tài khoản ấy chưa có lịch sử gì: không buổi nào, không giao dịch, Reader
-- chưa có hồ sơ nên không hiện trong danh sách.
--
-- File này dựng cho:
--   tranduydatqtdl@gmail.com   -> hồ sơ Reader đầy đủ + lịch tuần T2–T6
--   alloffdenchi13@gmail.com   -> đã đặt, đã trả, đã đánh giá Reader trên
--
-- và thêm năm buổi đã hoàn tất từ các tài khoản demo khác, mỗi buổi một
-- đánh giá, để hồ sơ Reader có điểm trung bình và danh sách nhận xét thật
-- sự nhìn được chứ không phải "Chưa có đánh giá nào".
--
-- ------------------------------------------------------------
-- ĐÂY LÀ DỮ LIỆU DEMO, KHÔNG PHẢI MINH CHỨNG GIAO DỊCH
-- ------------------------------------------------------------
-- Hướng dẫn EXE201 yêu cầu minh chứng của Outcome 3 phải "truy xuất được
-- về nguồn gốc (tài khoản quản trị kênh, dashboard, lịch sử thanh toán)".
-- Mấy dòng payment_transactions dưới đây KHÔNG có đồng nào chuyển thật và
-- không đối chiếu được với PayOS.
--
-- Nên chúng cố ý mang dấu riêng để không ai nhầm:
--   payment_method          = 'SEED_DEMO'
--   external_transaction_id = 'SEED-DEMO-...'
--
-- Muốn có minh chứng thật cho OC3 thì phải có giao dịch PayOS thật, dù chỉ
-- vài nghìn đồng — nó sẽ hiện trong bảng điều khiển PayOS và đối chiếu
-- được. Đừng nộp mấy dòng này thay cho nó.
--
-- ------------------------------------------------------------
-- Idempotent và phòng thủ
-- ------------------------------------------------------------
-- Mọi dòng có id cố định và bọc NOT EXISTS, chạy lại không nhân đôi.
--
-- Nếu một trong hai tài khoản chưa tồn tại (CSDL mới, hoặc môi trường của
-- người khác) thì toàn bộ file không chèn gì và cũng không lỗi — các lệnh
-- đều là INSERT ... SELECT, không có hàng nguồn thì không có hàng đích.
-- Cố ý không dùng biến hay DO block: migration phải chạy được trên một CSDL
-- trống trong CI y như trên production.
-- ============================================================

-- ---------- 1. Nâng tài khoản Reader lên STAFF ----------
-- Không có vai STAFF thì không có quyền READER_MANAGE_PROFILE, nên dù có hồ
-- sơ vẫn không bấm "Nhận lịch" được.
UPDATE users
SET role = 'STAFF',
    updated_at = NOW()
WHERE email = 'tranduydatqtdl@gmail.com'
  AND role = 'USER';

-- ---------- 2. Hồ sơ Reader ----------
INSERT INTO reader_profiles (
    id, user_id, bio, specialties, years_experience,
    rating, total_reviews, is_available, verified_at,
    price_per_15m, price_per_30m, price_per_60m,
    created_at, updated_at
)
SELECT
    'd9000000-0000-4000-8000-000000000001'::uuid,
    u.id,
    'Mình đọc Tarot kết hợp bản đồ sao, tập trung vào chuyện học hành, '
    || 'định hướng nghề và các mối quan hệ ở tuổi hai mươi. Buổi xem là một '
    || 'cuộc trò chuyện hai chiều: mình hỏi lại nhiều, và sẽ nói thẳng khi '
    || 'lá bài không ủng hộ điều bạn đang mong.',
    ARRAY['Tình yêu', 'Sự nghiệp', 'Học tập'],
    3,
    0, 0, TRUE, NOW() - interval '60 days',
    90000, 170000, 300000,
    NOW() - interval '60 days', NOW()
FROM users u
WHERE u.email = 'tranduydatqtdl@gmail.com'
  AND NOT EXISTS (
      SELECT 1 FROM reader_profiles p WHERE p.user_id = u.id
  );

-- ---------- 3. Lịch tuần T2–T6, 09:00–17:00 ----------
INSERT INTO reader_availability (id, reader_id, day_of_week, start_time, end_time, is_active)
SELECT
    ('b9000000-0000-4000-8000-' || lpad(d.dow::text, 12, '0'))::uuid,
    'd9000000-0000-4000-8000-000000000001'::uuid,
    d.dow, TIME '09:00', TIME '17:00', TRUE
FROM (VALUES (1), (2), (3), (4), (5)) AS d(dow)
WHERE EXISTS (
    SELECT 1 FROM reader_profiles p
    WHERE p.id = 'd9000000-0000-4000-8000-000000000001'::uuid
)
AND NOT EXISTS (
    SELECT 1 FROM reader_availability a
    WHERE a.id = ('b9000000-0000-4000-8000-' || lpad(d.dow::text, 12, '0'))::uuid
);

-- ---------- 4. Sáu buổi đã hoàn tất, đã trả hết ----------
-- Buổi số 1 là của alloffdenchi13 — buổi nhóm sẽ mở ra lúc demo.
-- Năm buổi còn lại lấy từ tài khoản demo, chỉ để hồ sơ Reader có đủ đánh giá.
--
-- Giờ bắt đầu lệch nhau và lùi về quá khứ, nên không đụng ràng buộc
-- no_overlapping_bookings. Hồ sơ này mới nên cũng chưa có buổi nào từ trước.
INSERT INTO bookings (
    id, user_id, reader_profile_id, start_time, end_time,
    total_amount, status, payment_status,
    deposit_amount, remaining_amount, phase,
    created_at, updated_at
)
SELECT
    ('e9000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid,
    u.id,
    'd9000000-0000-4000-8000-000000000001'::uuid,
    date_trunc('day', NOW()) - (b.ngay || ' days')::interval + (b.gio || ' hours')::interval,
    date_trunc('day', NOW()) - (b.ngay || ' days')::interval + (b.gio || ' hours')::interval + interval '60 minutes',
    300000, 'COMPLETED', 'PAID',
    300000, 0, 'FULL',
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
JOIN users u ON u.email = b.email
WHERE EXISTS (
    SELECT 1 FROM reader_profiles p
    WHERE p.id = 'd9000000-0000-4000-8000-000000000001'::uuid
)
AND NOT EXISTS (
    SELECT 1 FROM bookings x
    WHERE x.id = ('e9000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid
);

-- ---------- 5. Giao dịch thanh toán ----------
-- Xem khối cảnh báo ở đầu file: đây là dữ liệu demo, không phải tiền thật.
INSERT INTO payment_transactions (
    id, booking_id, user_id, amount, payment_method,
    external_transaction_id, status, metadata, created_at
)
SELECT
    ('f9000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid,
    bk.id,
    bk.user_id,
    bk.total_amount,
    'SEED_DEMO',
    'SEED-DEMO-' || lpad(b.i::text, 4, '0'),
    'SUCCESS',
    jsonb_build_object(
        'nguon', 'migration V2_23',
        'ghi_chu', 'Du lieu demo. Khong co giao dich that, khong doi chieu duoc voi PayOS.'
    ),
    bk.start_time - interval '2 days'
FROM (VALUES (1), (2), (3), (4), (5), (6)) AS b(i)
JOIN bookings bk
  ON bk.id = ('e9000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid
WHERE NOT EXISTS (
    SELECT 1 FROM payment_transactions t
    WHERE t.id = ('f9000000-0000-4000-8000-' || lpad(b.i::text, 12, '0'))::uuid
);

-- ---------- 6. Đánh giá ----------
-- Không cho toàn 5 sao. Một hồ sơ mười đánh giá năm sao liền trông như hàng
-- mua, và lúc demo trước lớp thì câu hỏi đầu tiên sẽ là "số này thật không".
-- Bốn và năm sao trộn lẫn, kèm một nhận xét nói cả điểm chưa hài lòng.
INSERT INTO reviews (id, booking_id, user_id, reader_profile_id, rating, comment, created_at)
SELECT
    ('c9000000-0000-4000-8000-' || lpad(r.i::text, 12, '0'))::uuid,
    bk.id,
    bk.user_id,
    bk.reader_profile_id,
    r.sao,
    r.nhan_xet,
    bk.end_time + interval '3 hours'
FROM (VALUES
    (1, 5, 'Mình hỏi về chuyện chọn chuyên ngành, anh đọc bài xong hỏi ngược lại mấy câu làm mình tự trả lời được. Đáng tiền.'),
    (2, 5, 'Đúng giờ, nói rõ ràng, không doạ. Chỗ nào bài không thuận thì nói thẳng chứ không vòng vo.'),
    (3, 4, 'Buổi xem tốt, mình thích cách giải thích từng lá. Trừ một sao vì phần đầu hơi dài, muốn vào ý chính sớm hơn.'),
    (4, 5, 'Lần thứ hai đặt. Nhớ được chuyện lần trước mình kể, nên không phải giải thích lại từ đầu.'),
    (5, 4, 'Nội dung ổn và thật. Mình mong có thêm phần ghi chú sau buổi để đọc lại.'),
    (6, 5, 'Hỏi chuyện gia đình, được nghe một góc nhìn mình chưa từng nghĩ tới. Cảm ơn anh.')
) AS r(i, sao, nhan_xet)
JOIN bookings bk
  ON bk.id = ('e9000000-0000-4000-8000-' || lpad(r.i::text, 12, '0'))::uuid
WHERE NOT EXISTS (
    SELECT 1 FROM reviews v
    WHERE v.id = ('c9000000-0000-4000-8000-' || lpad(r.i::text, 12, '0'))::uuid
)
AND NOT EXISTS (
    SELECT 1 FROM reviews v2 WHERE v2.booking_id = bk.id
);

-- ---------- 7. Tính lại điểm trung bình ----------
-- Cùng cách tính của V2_16. Không làm bước này thì hồ sơ vẫn hiện 0 sao dù
-- đã có sáu đánh giá — cột rating là giá trị chốt sẵn, không tính lúc đọc.
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
