-- ============================================================
-- SHOP — phần 3 của sản phẩm: bán vật phẩm tarot
-- (bộ bài tarot, bài Lenormand, đá phong thuỷ, nến, khăn trải bài...)
--
-- Thanh toán CHƯA nối cổng thật: đơn tạo ra ở trạng thái PENDING và
-- payment_status UNPAID, chờ tích hợp VNPay/Momo sau.
-- ============================================================

-- TABLE: product_categories
CREATE TABLE product_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description TEXT,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_categories_slug ON product_categories(slug);

CREATE TRIGGER trg_product_categories BEFORE UPDATE ON product_categories
FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- TABLE: products
-- price lưu bằng VND nguyên (BIGINT), thống nhất với bookings.total_amount.
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID REFERENCES product_categories(id) ON DELETE SET NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL UNIQUE,
    description TEXT,
    price BIGINT NOT NULL CHECK (price >= 0),
    compare_at_price BIGINT CHECK (compare_at_price IS NULL OR compare_at_price >= 0),
    stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
    image_url TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_active ON products(active) WHERE active = TRUE;
CREATE INDEX idx_products_featured ON products(featured) WHERE featured = TRUE;

CREATE TRIGGER trg_products BEFORE UPDATE ON products
FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- TABLE: cart_items
-- Giỏ hàng gắn thẳng vào user, không cần bảng cart riêng: mỗi user một giỏ.
-- UNIQUE(user_id, product_id) để thêm lại cùng sản phẩm thì cộng dồn quantity.
CREATE TABLE cart_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cart_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_cart_items_user ON cart_items(user_id);

CREATE TRIGGER trg_cart_items BEFORE UPDATE ON cart_items
FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- TABLE: orders
CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_code VARCHAR(20) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'COMPLETED', 'CANCELLED')),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID'
        CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED', 'FAILED')),
    subtotal BIGINT NOT NULL CHECK (subtotal >= 0),
    shipping_fee BIGINT NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
    total_amount BIGINT NOT NULL CHECK (total_amount >= 0),
    receiver_name VARCHAR(150) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    shipping_address TEXT NOT NULL,
    note TEXT,
    cancel_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_code ON orders(order_code);

CREATE TRIGGER trg_orders BEFORE UPDATE ON orders
FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- TABLE: order_items
-- Chụp lại tên và giá tại thời điểm đặt, để đơn cũ không đổi theo khi
-- sản phẩm bị sửa giá hoặc đổi tên về sau.
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID REFERENCES products(id) ON DELETE SET NULL,
    product_name VARCHAR(200) NOT NULL,
    product_image_url TEXT,
    unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    line_total BIGINT NOT NULL CHECK (line_total >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_order_items_order ON order_items(order_id);

-- ============================================================
-- SEED — danh mục và sản phẩm mẫu
-- ============================================================

INSERT INTO product_categories (name, slug, description, display_order) VALUES
('Bài Tarot',      'bai-tarot',      'Các bộ bài Tarot 78 lá, từ Rider-Waite cổ điển đến các bộ hiện đại.', 1),
('Bài Lenormand',  'bai-lenormand',  'Bộ bài Lenormand 36 lá, đọc nhanh và trực diện.',                     2),
('Bài Oracle',     'bai-oracle',     'Bài Oracle nhiều chủ đề, không giới hạn số lá.',                      3),
('Đá & Khoáng',    'da-khoang',      'Đá phong thuỷ, trụ đá, vòng tay theo từng năng lượng.',               4),
('Phụ kiện',       'phu-kien',       'Khăn trải bài, hộp đựng, nến, xông trầm và các vật phẩm đi kèm.',     5);

INSERT INTO products (category_id, name, slug, description, price, compare_at_price, stock, active, featured) VALUES
((SELECT id FROM product_categories WHERE slug = 'bai-tarot'),
 'Rider-Waite Tarot — bản kinh điển', 'rider-waite-tarot',
 'Bộ 78 lá nguyên bản, hình vẽ Pamela Colman Smith. Lựa chọn chuẩn mực cho người mới bắt đầu và cũng là bộ được trích dẫn nhiều nhất trong sách vở.',
 390000, 450000, 25, TRUE, TRUE),

((SELECT id FROM product_categories WHERE slug = 'bai-tarot'),
 'Thoth Tarot — Aleister Crowley', 'thoth-tarot',
 'Bộ bài nặng tính biểu tượng và chiêm tinh, hợp với người đã quen hệ thống Rider-Waite và muốn đi sâu hơn.',
 520000, NULL, 12, TRUE, FALSE),

((SELECT id FROM product_categories WHERE slug = 'bai-tarot'),
 'Wild Unknown Tarot', 'wild-unknown-tarot',
 'Phong cách tối giản, hình muông thú và thiên nhiên. Dễ đọc bằng trực giác, được ưa chuộng để trải bài hằng ngày.',
 480000, 550000, 18, TRUE, TRUE),

((SELECT id FROM product_categories WHERE slug = 'bai-lenormand'),
 'Blue Owl Lenormand', 'blue-owl-lenormand',
 'Bộ 36 lá theo phong cách cổ điển Đức, hình ảnh sáng và rõ nghĩa. Phù hợp cho câu hỏi cần câu trả lời dứt khoát.',
 320000, NULL, 20, TRUE, FALSE),

((SELECT id FROM product_categories WHERE slug = 'bai-lenormand'),
 'Maybe Lenormand', 'maybe-lenormand',
 'Bản vẽ tay hiện đại, tông màu ấm. Kèm sách nhỏ hướng dẫn trải bài 3 lá và Grand Tableau.',
 365000, 410000, 14, TRUE, FALSE),

((SELECT id FROM product_categories WHERE slug = 'bai-oracle'),
 'Moonology Oracle — 44 lá', 'moonology-oracle',
 'Bài Oracle theo chu kỳ mặt trăng, ghép rất hợp với việc xem theo bản đồ sao.',
 350000, NULL, 22, TRUE, TRUE),

((SELECT id FROM product_categories WHERE slug = 'da-khoang'),
 'Thạch anh tím — trụ tự nhiên 500g', 'thach-anh-tim-tru-500g',
 'Trụ thạch anh tím tự nhiên, khoảng 500g. Thường dùng để đặt nơi làm việc hoặc góc thiền.',
 680000, 790000, 8, TRUE, TRUE),

((SELECT id FROM product_categories WHERE slug = 'da-khoang'),
 'Vòng tay thạch anh hồng 8mm', 'vong-thach-anh-hong-8mm',
 'Vòng tay thạch anh hồng tự nhiên, hạt 8mm, dây co giãn. Gắn với năng lượng tình cảm và sự chữa lành.',
 250000, NULL, 40, TRUE, FALSE),

((SELECT id FROM product_categories WHERE slug = 'da-khoang'),
 'Đá mặt trăng — set 3 viên', 'da-mat-trang-set-3',
 'Ba viên đá mặt trăng đánh bóng, dùng kèm khi trải bài hoặc mang theo người.',
 180000, 220000, 30, TRUE, FALSE),

((SELECT id FROM product_categories WHERE slug = 'phu-kien'),
 'Khăn trải bài nhung — thêu cung hoàng đạo', 'khan-trai-bai-nhung',
 'Khăn nhung 60x60cm, thêu vòng 12 cung hoàng đạo bằng chỉ ánh kim. Mặt dưới chống trượt.',
 210000, NULL, 35, TRUE, TRUE),

((SELECT id FROM product_categories WHERE slug = 'phu-kien'),
 'Hộp gỗ đựng bài — khắc mặt trăng', 'hop-go-dung-bai',
 'Hộp gỗ thông khắc hoạ tiết pha trăng, lót nhung bên trong. Vừa hầu hết bộ bài khổ tiêu chuẩn.',
 290000, 340000, 16, TRUE, FALSE),

((SELECT id FROM product_categories WHERE slug = 'phu-kien'),
 'Nến thơm trầm hương — 200g', 'nen-tram-huong-200g',
 'Nến sáp đậu nành pha tinh dầu trầm, cháy khoảng 40 giờ. Dùng để tạo không gian trước khi trải bài.',
 195000, NULL, 50, TRUE, FALSE);
