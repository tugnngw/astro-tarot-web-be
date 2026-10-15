-- V2_33: Bán gói AI qua Google Play Billing trên Android.
--
-- VÌ SAO BẮT BUỘC: chính sách Thanh toán của Google xếp gói cước nội dung số
-- dùng trong ứng dụng vào diện PHẢI dùng hệ thống thanh toán của Play. Việt
-- Nam không nằm trong chương trình user choice billing hay alternative
-- billing nào (kiểm lại 14/10/2026: chỉ 30 nước EEA, Anh, Mỹ, Nhật, Úc,
-- Brazil, Indonesia, Nam Phi, cộng Hàn Quốc và Ấn Độ), nên không có cửa nào
-- khác. Buổi xem với Reader thì KHÁC: đó là dịch vụ một-kèm-một giữa hai cá
-- nhân, Google miễn trừ, nên vẫn trả qua PayOS như cũ.
--
-- Bán kiểu SẢN PHẨM MỘT LẦN chứ không phải gói tự gia hạn. Mô hình hiện tại
-- vốn đã là mua N ngày rồi hết hạn; chuyển sang tự gia hạn thì phải dựng
-- thêm thông báo thời gian thực qua Pub/Sub, xử lý gia hạn, ân hạn, huỷ giữa
-- kỳ — một hệ thống khác hẳn, không nằm trong phạm vi lần này.

ALTER TABLE subscription_plan
    ADD COLUMN IF NOT EXISTS google_play_product_id VARCHAR(100);

COMMENT ON COLUMN subscription_plan.google_play_product_id IS
    'Mã sản phẩm khai trong Play Console. NULL = gói không bán trên Android.';

CREATE UNIQUE INDEX IF NOT EXISTS ux_subscription_plan_gp_product
    ON subscription_plan (google_play_product_id)
    WHERE google_play_product_id IS NOT NULL;

-- Giá bán trên Android, cao hơn giá web.
--
-- VÌ SAO LỆCH: Google thu hoa hồng mỗi lượt mua — 15% nếu đã vào Small
-- Business Program (dưới 1 triệu USD doanh thu một năm), 30% nếu chưa. Bán
-- đúng giá web thì thực nhận còn thấp hơn bán trên web, nên giá app được kê
-- lên để sau hoa hồng vẫn nhận về nhiều hơn giá web một chút.
--
-- CON SỐ NÀY CHỈ ĐỂ GHI SỔ. Giá khách thật sự trả do Play Console quyết, và
-- app hiện đúng con số Google trả về chứ không hiện con số ở đây. Đổi giá
-- bên Play Console thì phải sửa cả cột này, không thì thống kê doanh thu
-- lệch khỏi tiền thật.
ALTER TABLE subscription_plan
    ADD COLUMN IF NOT EXISTS google_play_price BIGINT;

COMMENT ON COLUMN subscription_plan.google_play_price IS
    'Giá niêm yết trên Play Console (VND). Dùng để ghi vào lượt mua, phải '
    'khớp tay với giá thật bên Play Console.';

UPDATE subscription_plan SET google_play_product_id = 'ai_basic_1d'
    WHERE id = 'a0000000-0000-4000-8000-000000000002';
UPDATE subscription_plan SET google_play_product_id = 'ai_combo_3d'
    WHERE id = 'a0000000-0000-4000-8000-000000000003';
UPDATE subscription_plan SET google_play_product_id = 'ai_vip_7d'
    WHERE id = 'a0000000-0000-4000-8000-000000000004';
UPDATE subscription_plan SET google_play_product_id = 'ai_month_basic'
    WHERE id = 'a0000000-0000-4000-8000-000000000005';
UPDATE subscription_plan SET google_play_product_id = 'ai_month_pro'
    WHERE id = 'a0000000-0000-4000-8000-000000000006';

UPDATE subscription_plan SET google_play_price = 19000
    WHERE google_play_product_id = 'ai_basic_1d';
UPDATE subscription_plan SET google_play_price = 49000
    WHERE google_play_product_id = 'ai_combo_3d';
UPDATE subscription_plan SET google_play_price = 95000
    WHERE google_play_product_id = 'ai_vip_7d';
UPDATE subscription_plan SET google_play_price = 119000
    WHERE google_play_product_id = 'ai_month_basic';
UPDATE subscription_plan SET google_play_price = 239000
    WHERE google_play_product_id = 'ai_month_pro';

-- Nới ràng buộc purchase_type để nhận thêm GOOGLE_PLAY.
--
-- V2_21 khai CHECK (purchase_type IN ('STRIPE','PAYOS','WALLET','MANUAL')).
-- Thêm hằng số vào enum Java mà quên chỗ này thì mọi lượt mua qua Play đều
-- đổ ở bước ghi cơ sở dữ liệu — SAU KHI Google đã trừ tiền khách.
ALTER TABLE user_plan_purchase
    DROP CONSTRAINT IF EXISTS user_plan_purchase_purchase_type_check;

ALTER TABLE user_plan_purchase
    ADD CONSTRAINT user_plan_purchase_purchase_type_check
    CHECK (purchase_type IN ('STRIPE', 'PAYOS', 'WALLET', 'MANUAL', 'GOOGLE_PLAY'));

-- Mỗi lượt mua của Play chỉ được đổi lấy gói ĐÚNG MỘT LẦN.
--
-- Không có bảng này thì cùng một purchaseToken gửi lại nhiều lần là nhận
-- nhiều gói. Máy khách có thể gửi lại thật mà không hề gian: mạng rớt giữa
-- chừng, app khởi động lại và phát lại luồng mua chưa hoàn tất — thư viện
-- in_app_purchase cố ý phát lại cho tới khi được xác nhận.
CREATE TABLE IF NOT EXISTS google_play_purchase (
    purchase_token   VARCHAR(512) PRIMARY KEY,
    order_id         VARCHAR(128),
    product_id       VARCHAR(100) NOT NULL,
    user_id          UUID         NOT NULL REFERENCES users (id),
    plan_id          UUID         NOT NULL REFERENCES subscription_plan (id),
    user_purchase_id UUID         REFERENCES user_plan_purchase (id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS ix_google_play_purchase_user
    ON google_play_purchase (user_id, created_at DESC);
