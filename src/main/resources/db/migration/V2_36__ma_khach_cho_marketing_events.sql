-- ============================================================
-- Mã khách, để nối các hành động của CÙNG một người lại với nhau.
--
-- Trước cột này, bảng marketing_events là một đống lượt bấm rời rạc: biết
-- có 11 lượt sang Shopee, nhưng không biết đó là 11 người bấm một lần hay
-- một người bấm 11 lần. Và nhất là không dựng lại được hành trình —
-- "khách này vào từ Facebook, xem trang chủ, mở Tarot, rút một quẻ rồi đi".
--
-- Mã sinh ở trình duyệt và cất trong localStorage. Nó KHÔNG phải danh tính:
-- không tên, không email, không số điện thoại; xoá dữ liệu trình duyệt là
-- thành khách mới. Đúng mức cần để đọc hành trình, không hơn.
-- ============================================================

ALTER TABLE marketing_events
    ADD COLUMN IF NOT EXISTS visitor_id VARCHAR(64);

-- Truy vấn nóng: "những khách gần đây nhất, mỗi khách làm gì".
-- Gộp theo visitor_id rồi sắp theo thời gian, nên index ghép đúng thứ tự ấy.
CREATE INDEX IF NOT EXISTS idx_marketing_events_visitor
    ON marketing_events (visitor_id, created_at DESC);

-- Dòng cũ không có mã khách và sẽ không bao giờ có. Để NULL chứ không gán
-- bừa một giá trị chung: gán chung thì mọi lượt cũ trông như của một người,
-- và con số "bao nhiêu khách" sai ngay từ hàng đầu tiên.
