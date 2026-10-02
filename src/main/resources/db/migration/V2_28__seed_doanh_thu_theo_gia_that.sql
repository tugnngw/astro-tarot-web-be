-- ============================================================
-- Dựng lại biểu đồ doanh thu theo giá thật của sản phẩm.
-- ============================================================
--
-- V2_10 gieo mười một tháng doanh thu, mỗi tháng đúng MỘT giao dịch, số
-- tròn trĩnh từ 380.000 tới 1.050.000. Ba chỗ không khớp thực tế:
--
--   1. Mười một tháng liên tục nghĩa là đã bán hàng từ gần một năm trước.
--      Đồ án một kỳ không có lịch sử đó. Nó còn chọi với chính dữ liệu
--      khác trong database: hai mươi phản hồi chỉ rải trong hai mươi ngày.
--
--   2. Mỗi tháng một giao dịch ~900.000 không phải là một doanh nghiệp.
--      Doanh thu thật là nhiều giao dịch nhỏ, không đều.
--
--   3. Không con số nào là giá của thứ gì đang bán. Bảng giá thật:
--
--        buổi xem   150/200/250k (15 phút)   280/350/450k (30 phút)
--                   500/650/800k (60 phút)
--        gói AI     15k   39k   79k   99k   199k
--
--      980.000 không mua được gì. Chính comment của V2_10 đã thừa nhận
--      "chỉ phục vụ thống kê, không gắn lịch thật".
--
-- ------------------------------------------------------------
-- Vì sao SỬA mười một dòng cũ chứ không xoá chúng
-- ------------------------------------------------------------
-- Bản đầu của file này xoá hẳn mười một dòng rồi chèn bộ mới. CI đổ, và đổ
-- đúng:
--
--   Bước "Seed chạy lại không nhân đôi dữ liệu" áp lại mọi file V*seed*.sql
--   rồi so số hàng. V2_10 vẫn nằm trong repo với chốt
--   NOT EXISTS (… external_transaction_id = 'SEED-PAY-MONTH-…'). Xoá mười
--   một dòng đó là làm chốt ấy hở ra, nên V2_10 chèn lại cả mười một.
--
-- Trên Flyway thật thì vẫn ra kết quả đúng — mỗi file chạy một lần, theo
-- thứ tự, V2_10 chèn rồi V2_28 xoá. Nhưng phép kiểm không phân biệt được
-- "seed không idempotent" với "một migration sau cố ý dọn đi", và nó đúng
-- khi không phân biệt: một file seed mà chạy lại lại sinh thêm hàng là
-- thứ đáng chặn.
--
-- Nên đổi hướng: dùng lại chính mười một dòng đó. Sửa số tiền, ngày, người
-- mua, giữ nguyên external_transaction_id. Chốt của V2_10 vẫn chặn, số hàng
-- không đổi, không còn gì hở. Hoá ra lại gọn hơn bản xoá.
--
-- Không sửa nội dung V2_10. Nó đã chạy trên production; đổi nội dung là đổi
-- checksum và Flyway sẽ đổ.
--
-- ------------------------------------------------------------
-- Chọn gói AI làm nguồn doanh thu
-- ------------------------------------------------------------
-- Lượt mua gói VỐN không có booking, nên booking_id NULL ở đây đúng bản
-- chất chứ không phải đi tắt. Doanh thu buổi xem đã có booking thật ở
-- V2_19, V2_23 và V2_25.
--
-- Vẫn giữ nhãn SEED_DEMO. Làm số liệu thật hơn là một việc, khoác nhãn
-- PAYOS cho giao dịch không tồn tại là việc khác — hướng dẫn EXE201 đòi
-- minh chứng Outcome 3 "truy xuất được về nguồn gốc", nên những dòng này
-- phải tự nhận là dữ liệu demo khi có ai soi tới.
--
-- Bốn tháng, đi lên dần: tháng đầu chỉ vài gói ngày rẻ nhất, rồi mới có
-- người mua gói tháng. Đó là hình dáng của một sản phẩm mới mở bán. Người
-- mua lặp lại trong bốn tháng là chuyện bình thường của sản phẩm thu phí
-- theo kỳ — đúng ra là dấu hiệu tốt, không phải dấu hiệu dữ liệu giả.

-- ---------- 1. Sửa mười một dòng của V2_10 ----------
-- Chỉ gán cho ba tài khoản khách của V2_4, không gán cho tài khoản thật.
-- Ba tài khoản ấy chắc chắn tồn tại nên cả mười một dòng đều được sửa; nếu
-- gán cho một email có thể không có, dòng đó sẽ nằm lại với số tiền cũ —
-- tệ hơn cả việc không sửa, vì lúc đó bộ dữ liệu nửa mới nửa cũ.
UPDATE payment_transactions p
SET amount = v.gia,
    user_id = u.id,
    payment_method = 'SEED_DEMO',
    phase = 'FULL',
    metadata = jsonb_build_object('goi', v.ten_goi, 'nguon', 'seed doanh thu goi AI'),
    created_at = (
        date_trunc('month', NOW() AT TIME ZONE 'Asia/Ho_Chi_Minh')
        - (v.thang || ' months')::interval
        + ((v.ngay - 1) || ' days')::interval
        + TIME '20:30'
    ) AT TIME ZONE 'Asia/Ho_Chi_Minh'
FROM (VALUES
    -- mã cũ của V2_10, email người mua, giá, tên gói, tháng trước, ngày
    ('SEED-PAY-MONTH-01', 'user.an@astrotarot.demo',    15000::bigint, 'Basic 1 Ngày', 3, 19),
    ('SEED-PAY-MONTH-02', 'user.bich@astrotarot.demo',  15000::bigint, 'Basic 1 Ngày', 3, 22),
    ('SEED-PAY-MONTH-03', 'user.an@astrotarot.demo',    39000::bigint, 'Combo 3 Ngày', 3, 26),
    ('SEED-PAY-MONTH-04', 'user.an@astrotarot.demo',    39000::bigint, 'Combo 3 Ngày', 2,  5),
    ('SEED-PAY-MONTH-05', 'user.bich@astrotarot.demo',  39000::bigint, 'Combo 3 Ngày', 2,  9),
    ('SEED-PAY-MONTH-06', 'user.cuong@astrotarot.demo', 15000::bigint, 'Basic 1 Ngày', 2, 12),
    ('SEED-PAY-MONTH-07', 'user.an@astrotarot.demo',    99000::bigint, 'Cơ bản Tháng', 2, 16),
    ('SEED-PAY-MONTH-08', 'user.bich@astrotarot.demo',  15000::bigint, 'Basic 1 Ngày', 2, 21),
    ('SEED-PAY-MONTH-09', 'user.cuong@astrotarot.demo', 39000::bigint, 'Combo 3 Ngày', 2, 27),
    ('SEED-PAY-MONTH-10', 'user.an@astrotarot.demo',    99000::bigint, 'Cơ bản Tháng', 1,  3),
    ('SEED-PAY-MONTH-11', 'user.cuong@astrotarot.demo', 79000::bigint, 'VIP 7 Ngày',   1,  6)
) AS v(ma, email, gia, ten_goi, thang, ngay)
JOIN users u ON u.email = v.email
WHERE p.external_transaction_id = v.ma
  -- Chạy lại thì không đụng gì: nhãn nguồn này chỉ có sau lần sửa đầu.
  AND p.metadata->>'nguon' IS DISTINCT FROM 'seed doanh thu goi AI';

-- ---------- 2. Tám lượt mua nữa cho đủ mười chín ----------
-- Tới đây mới gán cho tài khoản thật alloffdenchi13@gmail.com. Đây là chèn
-- nên tài khoản không có thì dòng tự rụng qua JOIN, không để lại dữ liệu
-- nửa vời.
--
-- Tra theo email chứ không theo uuid cố định: V2_23 từng gieo rỗng vì tôi
-- gán cứng id hồ sơ, từ V2_25 trở đi mọi tài khoản thật đều tra theo email.
INSERT INTO payment_transactions (
    id, booking_id, user_id, amount, payment_method,
    external_transaction_id, status, phase, metadata, created_at
)
SELECT
    ('f2000000-0000-4000-8000-' || lpad(v.stt::text, 12, '0'))::uuid,
    NULL::uuid,
    u.id,
    v.gia,
    'SEED_DEMO',
    'SEED-PLAN-' || lpad(v.stt::text, 3, '0'),
    'SUCCESS',
    'FULL',
    jsonb_build_object('goi', v.ten_goi, 'nguon', 'seed doanh thu goi AI'),
    -- Tháng trước thì neo vào mốc đầu tháng rồi cộng ngày, để
    -- to_char(created_at, 'YYYY-MM') rơi đúng tháng cần.
    --
    -- Tháng hiện tại KHÔNG neo kiểu đó: hôm nay có thể là ngày 2, cộng
    -- thêm ngày là ra giao dịch ở tương lai. Nên tháng này tính lùi từ
    -- NOW() theo giờ, luôn nằm trong quá khứ dù chạy vào ngày nào.
    CASE WHEN v.thang = 0
        THEN NOW() - (v.ngay || ' hours')::interval
        ELSE (
            date_trunc('month', NOW() AT TIME ZONE 'Asia/Ho_Chi_Minh')
            - (v.thang || ' months')::interval
            + ((v.ngay - 1) || ' days')::interval
            + TIME '20:30'
        ) AT TIME ZONE 'Asia/Ho_Chi_Minh'
    END
FROM (VALUES
    -- stt, email, giá, tên gói, tháng trước, ngày (tháng 0 thì là SỐ GIỜ)
    (12, 'user.bich@astrotarot.demo',  99000::bigint, 'Cơ bản Tháng', 1,  8),
    (13, 'alloffdenchi13@gmail.com',   15000::bigint, 'Basic 1 Ngày', 1, 12),
    (14, 'user.an@astrotarot.demo',   199000::bigint, 'Pro Tháng',    1, 14),
    (15, 'user.cuong@astrotarot.demo', 39000::bigint, 'Combo 3 Ngày', 1, 18),
    (16, 'user.bich@astrotarot.demo',  39000::bigint, 'Combo 3 Ngày', 1, 23),
    (17, 'alloffdenchi13@gmail.com',   79000::bigint, 'VIP 7 Ngày',   1, 27),
    -- Tháng này mới chạy được mấy ngày nên chỉ hai dòng, đúng như thực tế.
    (18, 'user.an@astrotarot.demo',   199000::bigint, 'Pro Tháng',    0, 30),
    (19, 'alloffdenchi13@gmail.com',   99000::bigint, 'Cơ bản Tháng', 0,  8)
) AS v(stt, email, gia, ten_goi, thang, ngay)
JOIN users u ON u.email = v.email
WHERE NOT EXISTS (
    SELECT 1 FROM payment_transactions p
    WHERE p.external_transaction_id = 'SEED-PLAN-' || lpad(v.stt::text, 3, '0')
       OR p.id = ('f2000000-0000-4000-8000-' || lpad(v.stt::text, 12, '0'))::uuid
);
