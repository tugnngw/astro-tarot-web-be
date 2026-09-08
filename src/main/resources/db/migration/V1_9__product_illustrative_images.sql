-- ============================================================
-- Ảnh minh hoạ cho các sản phẩm chưa có ảnh chụp thật
--
-- 11 sản phẩm còn lại không có ảnh của đúng món đang bán: ảnh của các bộ bài
-- thương mại thuộc bản quyền nhà xuất bản, còn đá khoáng / nến / khăn / hộp
-- gỗ thì kho ảnh tự do chỉ có ảnh của món tương tự chứ không phải món này.
--
-- Dùng ảnh minh hoạ có giấy phép tự do (CC0, Public Domain, CC BY, CC BY-SA)
-- kèm CỜ ĐÁNH DẤU, để giao diện nói rõ với khách đây là ảnh minh hoạ. Gắn ảnh
-- không đúng món mà không nói gì là mô tả sai hàng — khách nhận hàng thấy
-- khác ảnh sẽ mất lòng tin và có quyền khiếu nại.
--
-- Nguồn và giấy phép từng ảnh ghi trong
-- astro-tarot-web-fe/public/products/README.md
-- ============================================================

ALTER TABLE products
    ADD COLUMN image_is_illustrative BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN products.image_is_illustrative IS
    'TRUE khi image_url là ảnh minh hoạ chứ không phải ảnh chụp đúng sản phẩm. Giao diện phải hiển thị ghi chú tương ứng.';

UPDATE products SET image_url = '/products/thoth-tarot.jpg',            image_is_illustrative = TRUE WHERE slug = 'thoth-tarot';
UPDATE products SET image_url = '/products/wild-unknown-tarot.jpg',     image_is_illustrative = TRUE WHERE slug = 'wild-unknown-tarot';
UPDATE products SET image_url = '/products/blue-owl-lenormand.jpg',     image_is_illustrative = TRUE WHERE slug = 'blue-owl-lenormand';
UPDATE products SET image_url = '/products/maybe-lenormand.jpg',        image_is_illustrative = TRUE WHERE slug = 'maybe-lenormand';
UPDATE products SET image_url = '/products/moonology-oracle.png',       image_is_illustrative = TRUE WHERE slug = 'moonology-oracle';
UPDATE products SET image_url = '/products/thach-anh-tim-tru-500g.jpg', image_is_illustrative = TRUE WHERE slug = 'thach-anh-tim-tru-500g';
UPDATE products SET image_url = '/products/vong-thach-anh-hong-8mm.jpg',image_is_illustrative = TRUE WHERE slug = 'vong-thach-anh-hong-8mm';
UPDATE products SET image_url = '/products/da-mat-trang-set-3.jpg',     image_is_illustrative = TRUE WHERE slug = 'da-mat-trang-set-3';
UPDATE products SET image_url = '/products/hop-go-dung-bai.jpg',        image_is_illustrative = TRUE WHERE slug = 'hop-go-dung-bai';
UPDATE products SET image_url = '/products/khan-trai-bai-nhung.jpg',    image_is_illustrative = TRUE WHERE slug = 'khan-trai-bai-nhung';
UPDATE products SET image_url = '/products/nen-tram-huong-200g.jpg',    image_is_illustrative = TRUE WHERE slug = 'nen-tram-huong-200g';

-- Rider-Waite giữ nguyên FALSE: bộ 1909 đã hết hạn bản quyền nên ảnh đang
-- dùng là ảnh THẬT của chính bộ bài đó.
