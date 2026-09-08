-- ============================================================
-- Ảnh sản phẩm
--
-- image_url trỏ tới file tĩnh do frontend phục vụ (thư mục public/products/),
-- không phải file người dùng tải lên qua /uploads. Sản phẩm nào còn NULL thì
-- giao diện tự rơi về artwork SVG sinh sẵn.
--
-- Hiện chỉ điền được đúng một sản phẩm: Rider-Waite-Smith 1909 đã hết hạn
-- bản quyền nên dùng được ảnh thật của chính bộ bài đó. Ảnh của các bộ bài
-- thương mại khác (Thoth, Wild Unknown, Blue Owl, Maybe Lenormand, Moonology)
-- thuộc bản quyền nhà xuất bản; còn đá khoáng, nến, khăn, hộp gỗ thì ảnh trên
-- kho ảnh tự do không phải ảnh của đúng món đang bán, dùng vào là mô tả sai
-- hàng. Những món đó chờ ảnh nhà cung cấp hoặc ảnh tự chụp.
-- ============================================================

UPDATE products
SET image_url = '/products/rider-waite-tarot.jpg'
WHERE slug = 'rider-waite-tarot';
