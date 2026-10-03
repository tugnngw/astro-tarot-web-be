-- ============================================================
-- Lượt gọi AI khớp với lượt mua gói.
-- ============================================================
--
-- V2_5 gieo đúng NĂM lượt gọi AI, tổng, rải trong mười hai ngày. Sau V2_28
-- thì database có mười chín lượt mua gói AI trong bốn tháng, trong đó có
-- hai gói Pro 25 lượt/ngày. Cả hệ thống gọi AI năm lần mà có người bỏ
-- 199.000 mua gói 25 lượt/ngày là chuyện không khớp nhau — và nó nằm ngay
-- trên màn Tổng quan, cạnh biểu đồ doanh thu.
--
-- V2_5 còn gieo model 'gemini-2.0-flash' và 'gemini-1.5-flash'. App không
-- gọi model nào trong hai cái đó: GeminiProvider.java:233 dùng
-- 'gemini-2.5-flash'. Một bản ghi sử dụng ghi tên model chưa từng được gọi
-- là thứ đọc kỹ sẽ thấy.
--
-- Không sửa V2_5 (đã chạy trên production, đổi nội dung là đổi checksum).
-- Chỉ thêm vào cho đủ.
--
-- ------------------------------------------------------------
-- Sinh lượt dùng TỪ lượt mua, không sinh rời
-- ------------------------------------------------------------
-- Mỗi lượt gọi được sinh từ một lượt mua cụ thể, nên ba thứ tự khớp nhau
-- mà không phải cố gắng:
--
--   • người gọi đúng là người đã mua gói
--   • gọi SAU lúc mua, không phải trước
--   • mua gói to thì gọi nhiều, mua gói ngày thì gọi vài lần
--
-- Gieo rời rồi gán ngẫu nhiên sẽ sinh ra những dòng kiểu "người chưa mua gì
-- vẫn gọi hai mươi lần", đúng loại chi tiết làm người đọc mất tin vào cả bộ
-- dữ liệu.
--
-- Số lượt đặt dưới hạn mức của gói, không đặt bằng. Người mua gói 25
-- lượt/ngày rồi dùng đúng 25 lượt mỗi ngày trong ba mươi ngày là hành vi
-- của một script, không phải của người.
WITH luot AS (
    SELECT
        p.user_id,
        -- Số lượt và giãn cách theo gói đã mua.
        CASE p.metadata->>'goi'
            WHEN 'Basic 1 Ngày' THEN 3
            WHEN 'Combo 3 Ngày' THEN 6
            WHEN 'VIP 7 Ngày'   THEN 9
            WHEN 'Cơ bản Tháng' THEN 14
            WHEN 'Pro Tháng'    THEN 22
            ELSE 0
        END AS so_luot,
        s.i,
        -- Lượt đầu cách lúc mua hai tiếng: mua rồi mở ra dùng ngay, nhưng
        -- không phải cùng giây đó.
        p.created_at
            + INTERVAL '2 hours'
            + ((s.i - 1) * CASE p.metadata->>'goi'
                WHEN 'Basic 1 Ngày' THEN 3
                WHEN 'Combo 3 Ngày' THEN 10
                WHEN 'VIP 7 Ngày'   THEN 16
                WHEN 'Cơ bản Tháng' THEN 48
                WHEN 'Pro Tháng'    THEN 30
                ELSE 24
              END || ' hours')::interval AS luc,
        row_number() OVER (ORDER BY p.external_transaction_id, s.i) AS stt
    FROM payment_transactions p
    CROSS JOIN LATERAL generate_series(1, 22) AS s(i)
    WHERE p.metadata->>'nguon' = 'seed doanh thu goi AI'
      AND s.i <= CASE p.metadata->>'goi'
            WHEN 'Basic 1 Ngày' THEN 3
            WHEN 'Combo 3 Ngày' THEN 6
            WHEN 'VIP 7 Ngày'   THEN 9
            WHEN 'Cơ bản Tháng' THEN 14
            WHEN 'Pro Tháng'    THEN 22
            ELSE 0
          END
)
INSERT INTO ai_usage_logs (
    id, user_id, reading_id, chat_session_id,
    provider, model, prompt_tokens, completion_tokens, total_tokens,
    estimated_cost_usd, latency_ms, created_at
)
SELECT
    ('ac200000-0000-4000-8000-' || lpad(l.stt::text, 12, '0'))::uuid,
    l.user_id,
    NULL::uuid,
    NULL::uuid,
    'Gemini',
    -- Đúng model app gọi, xem GeminiProvider.java:233.
    'gemini-2.5-flash',
    -- Trải dãn số token bằng hệ số nguyên tố cùng nhau với modulo, để không
    -- ra dãy lặp đều. Khoảng giá trị lấy theo mấy dòng V2_5 đã có: lời nhắc
    -- một hai nghìn token, phần trả về vài trăm.
    (1200 + (l.stt * 137) % 2600)::int,
    (380 + (l.stt * 263) % 900)::int,
    (1200 + (l.stt * 137) % 2600 + 380 + (l.stt * 263) % 900)::int,
    -- Tính từ chính số token ở trên, không đặt tay. V2_5 đặt tay nên cột
    -- chi phí của nó không khớp số token nào: 1800+620 token mà ghi
    -- 0.000428.
    --
    -- Đơn giá dùng ở đây là GIẢ ĐỊNH để cột này nhất quán với cột token,
    -- không phải trích từ bảng giá tôi đã kiểm: 0,30 USD cho một triệu
    -- token vào và 2,50 USD cho một triệu token ra. Ai cần con số chi phí
    -- để báo cáo thì tra lại bảng giá Google rồi nhân lại.
    round(
        (1200 + (l.stt * 137) % 2600) * 0.30 / 1000000.0
      + (380 + (l.stt * 263) % 900) * 2.50 / 1000000.0
    , 6)::numeric,
    (850 + (l.stt * 71) % 2300)::int,
    l.luc
FROM luot l
-- Gói tháng mua hồi tháng này thì lượt cuối rơi vào tương lai. Cắt đi.
WHERE l.luc <= NOW()
  AND NOT EXISTS (
    SELECT 1 FROM ai_usage_logs a
    WHERE a.id = ('ac200000-0000-4000-8000-' || lpad(l.stt::text, 12, '0'))::uuid
);
