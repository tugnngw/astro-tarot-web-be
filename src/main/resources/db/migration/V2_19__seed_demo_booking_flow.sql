-- ============================================================
-- Lịch mẫu còn TƯƠNG LAI, để demo luồng khách đặt Reader.
--
-- V2_4 đã gieo buổi theo CURRENT_DATE lúc migration chạy. Những buổi
-- "sắp tới" ấy đã trôi về quá khứ, nên lịch tháng trống và không còn
-- nút nhận lịch / đặt cọc / nhắn tin để bấm.
--
-- Idempotent: mỗi dòng có id cố định, chạy lại không nhân đôi.
-- Ngày được tính lúc chạy, luôn rơi vào thứ 2–6 (đúng khung giờ
-- reader_availability của ba Reader demo), và bỏ qua nếu khung đó
-- đã có buổi khác — để không đụng ràng buộc chống trùng giờ.
--
-- Sau file này, đăng nhập (mật khẩu demo ghi ở đầu V2_4):
--   user.an@astrotarot.demo
--     thứ sắp tới 10:00 với Lan — đã trả hết, mở được chat
--     thứ kế 09:00 với Minh — đã cọc 50%, còn lại, mở được chat
--     một thứ vừa qua với Lan — đã hoàn tất, có ghi chú của Reader
--     thứ trước đó với Minh — đã hoàn tất nhưng mới cọc, hiện nhắc trả nốt
--   user.bich@astrotarot.demo
--     cùng ngày với buổi của An, 15:00 với Lan — chờ Lan bấm Nhận lịch,
--     khách thấy nút Đặt cọc 50% và Thanh toán hết
--     thứ thứ ba tới 14:00 với Hỗ trợ — đã trả hết
--   staff.lan / staff.minh / staff.support @astrotarot.demo
--     mở /staff → Lịch hẹn. Lan phải đăng nhập lại nếu token còn nhớ vai USER.
-- ============================================================

-- Lan bị hạ về USER nên không có READER_MANAGE_PROFILE, không nhận lịch được.
UPDATE users
SET role = 'STAFF',
    updated_at = NOW()
WHERE email = 'staff.lan@astrotarot.demo'
  AND role = 'USER'
  AND EXISTS (
      SELECT 1 FROM reader_profiles rp WHERE rp.user_id = users.id
  );

-- Ngày làm việc sắp tới (1 = ngày làm việc kế tiếp) và vừa qua.
-- DOW của Postgres: 0 = CN … 6 = T7, khớp cột day_of_week của lịch tuần.

INSERT INTO bookings (
    id, user_id, reader_profile_id, start_time, end_time,
    total_amount, deposit_amount, remaining_amount, forfeited_amount,
    status, payment_status, reader_note, reader_note_at, created_at, updated_at
)
SELECT
    v.id,
    u.id,
    rp.id,
    ((d.ngay)::timestamp + v.gio_bat_dau) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((d.ngay)::timestamp + v.gio_ket_thuc) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    v.total_amount,
    v.deposit_amount,
    v.remaining_amount,
    0,
    v.status,
    v.payment_status,
    v.reader_note,
    CASE WHEN v.reader_note IS NULL THEN NULL ELSE NOW() - INTERVAL '1 day' END,
    NOW() - v.tao_cach_day,
    NOW()
FROM (VALUES
    -- An × Lan, ngày làm tới: đã trả hết, sắp diễn ra
    ('e1000000-0000-4000-8000-000000000101'::uuid,
     'user.an@astrotarot.demo', 'staff.lan@astrotarot.demo',
     1, TIME '10:00', TIME '10:30',
     350000::bigint, 350000::bigint, 0::bigint,
     'CONFIRMED', 'PAID', NULL::text, INTERVAL '1 day'),

    -- Bích × Lan, cùng ngày buổi chiều: chờ Reader nhận, chưa trả
    ('e1000000-0000-4000-8000-000000000102'::uuid,
     'user.bich@astrotarot.demo', 'staff.lan@astrotarot.demo',
     1, TIME '15:00', TIME '15:30',
     350000::bigint, 175000::bigint, 175000::bigint,
     'PENDING', 'UNPAID', NULL::text, INTERVAL '3 hours'),

    -- An × Minh, ngày làm kế: đã cọc, còn 50%
    ('e1000000-0000-4000-8000-000000000103'::uuid,
     'user.an@astrotarot.demo', 'staff.minh@astrotarot.demo',
     2, TIME '09:00', TIME '10:00',
     800000::bigint, 400000::bigint, 400000::bigint,
     'CONFIRMED', 'DEPOSIT_PAID', NULL::text, INTERVAL '20 hours'),

    -- Bích × Hỗ trợ, ngày làm thứ ba tới: đã trả hết, buổi ngắn
    ('e1000000-0000-4000-8000-000000000104'::uuid,
     'user.bich@astrotarot.demo', 'staff.support@astrotarot.demo',
     3, TIME '14:00', TIME '14:15',
     150000::bigint, 150000::bigint, 0::bigint,
     'CONFIRMED', 'PAID', NULL::text, INTERVAL '2 days'),

    -- An × Lan, thứ vừa rồi: hoàn tất, có ghi chú
    ('e1000000-0000-4000-8000-000000000105'::uuid,
     'user.an@astrotarot.demo', 'staff.lan@astrotarot.demo',
     -1, TIME '10:00', TIME '10:30',
     350000::bigint, 350000::bigint, 0::bigint,
     'COMPLETED', 'PAID',
     'Ba lá cho thấy An đang đứng giữa hai lựa chọn. Lá giữa nghiêng về việc nói rõ nhu cầu trước, thay vì chờ đối phương đoán.',
     INTERVAL '4 days'),

    -- An × Minh, thứ trước nữa: hoàn tất nhưng mới cọc — nhắc trả nốt
    ('e1000000-0000-4000-8000-000000000106'::uuid,
     'user.an@astrotarot.demo', 'staff.minh@astrotarot.demo',
     -2, TIME '14:00', TIME '15:00',
     800000::bigint, 400000::bigint, 400000::bigint,
     'COMPLETED', 'DEPOSIT_PAID', NULL::text, INTERVAL '6 days')
) AS v(id, email_khach, email_reader, buoc_ngay, gio_bat_dau, gio_ket_thuc,
       total_amount, deposit_amount, remaining_amount,
       status, payment_status, reader_note, tao_cach_day)
JOIN users u ON u.email = v.email_khach
JOIN users ru ON ru.email = v.email_reader
JOIN reader_profiles rp ON rp.user_id = ru.id
JOIN LATERAL (
    SELECT ngay FROM (
        SELECT g.d::date AS ngay
        FROM generate_series(
            CASE WHEN v.buoc_ngay > 0 THEN CURRENT_DATE + 1 ELSE CURRENT_DATE - 21 END,
            CASE WHEN v.buoc_ngay > 0 THEN CURRENT_DATE + 21 ELSE CURRENT_DATE - 1 END,
            INTERVAL '1 day'
        ) AS g(d)
        WHERE EXTRACT(DOW FROM g.d) BETWEEN 1 AND 5
        ORDER BY CASE WHEN v.buoc_ngay > 0 THEN g.d END ASC,
                 CASE WHEN v.buoc_ngay < 0 THEN g.d END DESC
        OFFSET (abs(v.buoc_ngay) - 1) LIMIT 1
    ) picked
) d ON TRUE
WHERE NOT EXISTS (SELECT 1 FROM bookings b WHERE b.id = v.id)
  AND NOT EXISTS (
      SELECT 1 FROM bookings b
      WHERE b.reader_profile_id = rp.id
        AND b.status <> 'CANCELLED'
        AND b.start_time < ((d.ngay)::timestamp + v.gio_ket_thuc) AT TIME ZONE 'Asia/Ho_Chi_Minh'
        AND b.end_time > ((d.ngay)::timestamp + v.gio_bat_dau) AT TIME ZONE 'Asia/Ho_Chi_Minh'
  );

INSERT INTO payment_transactions (
    id, booking_id, user_id, amount, payment_method,
    external_transaction_id, status, phase, metadata, created_at
)
SELECT
    v.id, v.booking_id, b.user_id, v.amount, 'BANK_TRANSFER',
    v.external_id, 'SUCCESS', v.phase,
    '{"note":"seed demo booking flow"}'::jsonb,
    b.created_at
FROM (VALUES
    ('f1000000-0000-4000-8000-000000000101'::uuid,
     'e1000000-0000-4000-8000-000000000101'::uuid,
     350000::bigint, 'SEED-FLOW-101', 'FULL'),
    ('f1000000-0000-4000-8000-000000000103'::uuid,
     'e1000000-0000-4000-8000-000000000103'::uuid,
     400000::bigint, 'SEED-FLOW-103', 'DEPOSIT'),
    ('f1000000-0000-4000-8000-000000000104'::uuid,
     'e1000000-0000-4000-8000-000000000104'::uuid,
     150000::bigint, 'SEED-FLOW-104', 'FULL'),
    ('f1000000-0000-4000-8000-000000000105'::uuid,
     'e1000000-0000-4000-8000-000000000105'::uuid,
     350000::bigint, 'SEED-FLOW-105', 'FULL'),
    ('f1000000-0000-4000-8000-000000000106'::uuid,
     'e1000000-0000-4000-8000-000000000106'::uuid,
     400000::bigint, 'SEED-FLOW-106', 'DEPOSIT')
) AS v(id, booking_id, amount, external_id, phase)
JOIN bookings b ON b.id = v.booking_id
WHERE NOT EXISTS (SELECT 1 FROM payment_transactions p WHERE p.id = v.id);

INSERT INTO booking_messages (id, booking_id, sender_id, body, created_at)
SELECT v.id, v.booking_id, u.id, v.body, NOW() - v.cach_day
FROM (VALUES
    ('ab100000-0000-4000-8000-000000000101'::uuid,
     'e1000000-0000-4000-8000-000000000101'::uuid,
     'user.an@astrotarot.demo',
     'Chào chị Lan, em muốn hỏi về chuyện sắp phải chọn giữa hai công việc.',
     INTERVAL '5 hours'),
    ('ab100000-0000-4000-8000-000000000102'::uuid,
     'e1000000-0000-4000-8000-000000000101'::uuid,
     'staff.lan@astrotarot.demo',
     'Chào An, chị nhận được rồi. Em mang theo khoảng thời gian hai cơ hội đó, buổi tới mình xem kỹ.',
     INTERVAL '4 hours'),
    ('ab100000-0000-4000-8000-000000000103'::uuid,
     'e1000000-0000-4000-8000-000000000103'::uuid,
     'user.an@astrotarot.demo',
     'Anh Minh ơi, em đã cọc. Em muốn xem timing của một khoản tiền em đang phân vân.',
     INTERVAL '2 hours'),
    ('ab100000-0000-4000-8000-000000000104'::uuid,
     'e1000000-0000-4000-8000-000000000103'::uuid,
     'staff.minh@astrotarot.demo',
     'Anh thấy rồi. Em ghi giúp số tiền và thời điểm cần quyết, mang vào buổi nhé.',
     INTERVAL '1 hour')
) AS v(id, booking_id, email, body, cach_day)
JOIN users u ON u.email = v.email
WHERE EXISTS (SELECT 1 FROM bookings b WHERE b.id = v.booking_id)
  AND NOT EXISTS (SELECT 1 FROM booking_messages m WHERE m.id = v.id);
