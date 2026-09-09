-- ============================================================
-- Chuyển trụ cột 3 sang mô hình tiếp thị liên kết (affiliate)
--
-- Trước đây shop bán hàng trực tiếp: có giỏ, có đơn, có tồn kho. Nay hàng nằm
-- trên Shopee, mình giới thiệu và ăn hoa hồng. Ba thay đổi kéo theo:
--
--  1. Sản phẩm cần đường dẫn liên kết và tỉ lệ hoa hồng.
--  2. Cần đếm lượt bấm sang sàn, vì đó là số liệu duy nhất mình tự đo được —
--     đơn hàng và hoa hồng thật nằm ở báo cáo của Shopee, không gọi API lấy
--     về được nếu chưa đăng ký chương trình đối tác.
--  3. Tồn kho mất ý nghĩa: hàng không phải của mình nữa.
--
-- KHÔNG xoá bảng cart_items / orders / order_items ở migration này. Chúng đang
-- giữ đơn hàng thật đã phát sinh, và xoá là không lấy lại được. Việc dừng dùng
-- chúng là quyết định của chủ dự án, không phải của một migration.
-- ============================================================

ALTER TABLE products
    ADD COLUMN affiliate_url TEXT,
    ADD COLUMN affiliate_platform VARCHAR(30) NOT NULL DEFAULT 'SHOPEE',
    ADD COLUMN commission_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN click_count BIGINT NOT NULL DEFAULT 0;

ALTER TABLE products
    ADD CONSTRAINT chk_product_affiliate_platform
    CHECK (affiliate_platform IN ('SHOPEE', 'LAZADA', 'TIKI', 'TIKTOK', 'OTHER')),
    ADD CONSTRAINT chk_product_commission
    CHECK (commission_percent >= 0 AND commission_percent <= 100);

COMMENT ON COLUMN products.affiliate_url IS
    'Đường dẫn tiếp thị liên kết. NULL nghĩa là sản phẩm chưa gắn link và không hiện nút mua.';
COMMENT ON COLUMN products.commission_percent IS
    'Tỉ lệ hoa hồng sàn trả, chỉ để ước lượng doanh thu. Số thật lấy từ báo cáo của sàn.';
COMMENT ON COLUMN products.click_count IS
    'Đếm dồn, tăng mỗi lượt bấm sang sàn. Giữ ở đây để xếp hạng nhanh mà không phải gom bảng product_clicks.';

-- ---------- Lượt bấm sang sàn ----------
-- Mỗi dòng là một lần khách bấm "Mua trên Shopee". Không lưu địa chỉ IP đầy đủ
-- và không lưu gì nhận dạng được người chưa đăng nhập: mục đích là đo sản phẩm
-- nào được quan tâm, không phải theo dõi người dùng.
CREATE TABLE product_clicks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id     UUID REFERENCES users(id),
    referrer    VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_clicks_product ON product_clicks (product_id, created_at DESC);
CREATE INDEX idx_product_clicks_created ON product_clicks (created_at DESC);

COMMENT ON TABLE product_clicks IS
    'Lượt bấm sang sàn liên kết. user_id NULL khi khách chưa đăng nhập — vẫn đếm, vì phần lớn lượt bấm đến từ khách vãng lai.';

-- ---------- Gắn link mẫu cho hàng đang có ----------
-- Dùng đường dẫn tìm kiếm theo tên thay vì bịa mã sản phẩm: link tìm kiếm thì
-- luôn mở được, còn mã sản phẩm bịa sẽ dẫn tới trang lỗi. Quản trị viên thay
-- bằng link tiếp thị thật ở màn quản lý sản phẩm.
UPDATE products
SET affiliate_url = 'https://shopee.vn/search?keyword=' || replace(name, ' ', '%20'),
    affiliate_platform = 'SHOPEE',
    commission_percent = 5.00
WHERE affiliate_url IS NULL;
