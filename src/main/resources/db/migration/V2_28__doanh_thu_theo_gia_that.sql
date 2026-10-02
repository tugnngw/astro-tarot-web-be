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
--        buổi xem   150/200/250k (15')  280/350/450k (30')  500/650/800k (60')
--        gói AI     15k  39k  39k  99k  199k
--
--      980.000 không mua được gì. Chính comment của V2_10 đã thừa nhận
--      "chỉ phục vụ thống kê, không gắn lịch thật".
--
-- Thay bằng lượt mua gói AI rải bốn tháng, mỗi dòng đúng một mức giá đang
-- bán. Chọn gói AI chứ không chọn buổi xem vì lượt mua gói VỐN không có
-- booking — booking_id NULL ở đây là đúng bản chất, không phải đi tắt. Còn
-- doanh thu buổi xem đã có booking thật ở V2_19, V2_23 và V2_25.
--
-- Vẫn giữ nhãn SEED_DEMO và tiền tố SEED-PLAN-. Làm số liệu thật hơn là
-- một việc, khoác nhãn PAYOS cho giao dịch không tồn tại là việc khác —
-- hướng dẫn EXE201 đòi minh chứng Outcome 3 "truy xuất được về nguồn gốc",
-- nên những dòng này phải tự nhận là dữ liệu demo khi có ai soi tới.

-- ---------- 1. Bỏ mười một dòng dựng biểu đồ ----------
-- Khoanh đúng tiền tố SEED-PAY-MONTH- của V2_10, không đụng giao dịch nào
-- khác. Các dòng này tự khai là dữ liệu gieo nên xoá là an toàn.
DELETE FROM payment_transactions
WHERE external_transaction_id LIKE 'SEED-PAY-MONTH-%';

-- ---------- 2. Doanh thu gói AI theo giá thật ----------
-- Bốn tháng, đi lên dần: tháng đầu chỉ vài gói ngày rẻ nhất, rồi mới có
-- người mua gói tháng. Đó là hình dáng của một sản phẩm mới mở bán.
--
-- Người mua lặp lại trong bốn tháng là chuyện bình thường của sản phẩm thu
-- phí theo kỳ — đúng ra là dấu hiệu tốt (khách quay lại), không phải dấu
-- hiệu dữ liệu giả.
--
-- Tra người mua theo email chứ không theo uuid cố định. V2_23 từng gieo
-- rỗng vì tôi gán cứng id hồ sơ; từ V2_25 trở đi mọi tài khoản thật đều
-- tra theo email.
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
    -- NOW(), luôn nằm trong quá khứ dù chạy vào ngày nào.
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
    -- stt, email người mua, giá, tên gói, tháng trước, ngày trong tháng
    -- (tháng 0 thì cột cuối là SỐ GIỜ tính lùi từ bây giờ)

    -- Tháng mở bán: ba lượt, toàn gói ngày rẻ nhất.
    ( 1, 'user.an@astrotarot.demo',    15000::bigint, 'Basic 1 Ngày',  3, 19),
    ( 2, 'user.bich@astrotarot.demo',  15000::bigint, 'Basic 1 Ngày',  3, 22),
    ( 3, 'user.an@astrotarot.demo',    39000::bigint, 'Combo 3 Ngày',  3, 26),

    -- Tháng thứ hai: có người thứ ba, và lượt gói tháng đầu tiên.
    ( 4, 'user.an@astrotarot.demo',    39000::bigint, 'Combo 3 Ngày',  2,  5),
    ( 5, 'user.bich@astrotarot.demo',  39000::bigint, 'Combo 3 Ngày',  2,  9),
    ( 6, 'user.cuong@astrotarot.demo', 15000::bigint, 'Basic 1 Ngày',  2, 12),
    ( 7, 'user.an@astrotarot.demo',    99000::bigint, 'Cơ bản Tháng',  2, 16),
    ( 8, 'user.bich@astrotarot.demo',  15000::bigint, 'Basic 1 Ngày',  2, 21),
    ( 9, 'user.cuong@astrotarot.demo', 39000::bigint, 'Combo 3 Ngày',  2, 27),

    -- Tháng thứ ba: đông hơn, có gói Pro đầu tiên.
    (10, 'user.an@astrotarot.demo',    99000::bigint, 'Cơ bản Tháng',  1,  3),
    (11, 'user.cuong@astrotarot.demo', 79000::bigint, 'VIP 7 Ngày',    1,  6),
    (12, 'user.bich@astrotarot.demo',  99000::bigint, 'Cơ bản Tháng',  1,  8),
    (13, 'alloffdenchi13@gmail.com',   15000::bigint, 'Basic 1 Ngày',  1, 12),
    (14, 'user.an@astrotarot.demo',   199000::bigint, 'Pro Tháng',     1, 14),
    (15, 'user.cuong@astrotarot.demo', 39000::bigint, 'Combo 3 Ngày',  1, 18),
    (16, 'user.bich@astrotarot.demo',  39000::bigint, 'Combo 3 Ngày',  1, 23),
    (17, 'alloffdenchi13@gmail.com',   79000::bigint, 'VIP 7 Ngày',    1, 27),

    -- Tháng này: mới chạy được mấy ngày nên ít dòng, đúng như thực tế.
    (18, 'user.an@astrotarot.demo',   199000::bigint, 'Pro Tháng',     0, 30),
    (19, 'alloffdenchi13@gmail.com',   99000::bigint, 'Cơ bản Tháng',  0,  8)
) AS v(stt, email, gia, ten_goi, thang, ngay)
JOIN users u ON u.email = v.email
WHERE NOT EXISTS (
    SELECT 1 FROM payment_transactions p
    WHERE p.external_transaction_id = 'SEED-PLAN-' || lpad(v.stt::text, 3, '0')
       OR p.id = ('f2000000-0000-4000-8000-' || lpad(v.stt::text, 12, '0'))::uuid
);
