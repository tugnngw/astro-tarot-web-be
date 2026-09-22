-- ============================================================
-- TÍNH LẠI ĐIỂM VÀ SỐ ĐÁNH GIÁ CỦA READER TỪ BẢNG REVIEWS
-- ============================================================
-- Triệu chứng đo được trên production:
--
--   Reader           hồ sơ ghi          reviews thật có
--   Trần Hỗ Trợ      4.5 · 2 đánh giá   0
--   Phạm Quang Minh  4.2 · 0 đánh giá   0
--
-- Khách nhìn danh sách thấy "4.5 (2 đánh giá)", bấm vào thì trống trơn.
--
-- Nguồn: V2_4__seed_demo_data.sql chèn thẳng rating/total_reviews vào
-- reader_profiles mà không chèn số review tương ứng vào bảng reviews. Mã ứng
-- dụng KHÔNG sai — ReviewServiceImpl.recalculateReaderRating() đọc lại toàn
-- bộ bảng mỗi lần có đánh giá mới nên luôn đúng. Nó chỉ chưa bao giờ chạy cho
-- những hồ sơ seed, vì chưa ai đánh giá họ qua ứng dụng.
--
-- Nên đây là sửa DỮ LIỆU một lần, không phải sửa logic. Sau migration này,
-- mọi đánh giá mới vẫn đi qua đường tính lại vốn đã đúng.
--
-- Làm cho TẤT CẢ hồ sơ chứ không chỉ hai hồ sơ đang sai: nếu chỉ chữa đúng
-- cái nhìn thấy thì những hồ sơ seed khác lệch ít hơn vẫn nằm im ở đó.

UPDATE reader_profiles rp
SET rating = COALESCE(r.avg_rating, 0),
    total_reviews = COALESCE(r.cnt, 0),
    updated_at = NOW()
FROM (
    SELECT p.id AS profile_id,
           ROUND(AVG(rv.rating)::numeric, 2) AS avg_rating,
           COUNT(rv.id) AS cnt
      FROM reader_profiles p
      LEFT JOIN reviews rv ON rv.reader_profile_id = p.id
     GROUP BY p.id
) r
WHERE rp.id = r.profile_id
  -- Chỉ đụng hàng thật sự lệch. Không có điều kiện này thì migration bump
  -- updated_at của mọi hồ sơ, làm nhiễu cột "sửa lần cuối" mà chẳng sửa gì.
  AND (rp.rating IS DISTINCT FROM COALESCE(r.avg_rating, 0)
       OR rp.total_reviews IS DISTINCT FROM COALESCE(r.cnt, 0));
