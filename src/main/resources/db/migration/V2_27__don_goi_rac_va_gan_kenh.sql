-- ============================================================
-- Dọn hai gói rác trên production, và quy phản hồi về đúng kênh.
-- ============================================================

-- ---------- 1. Hai gói rác ----------
-- Production đang bán hai gói do ai đó thử tạo qua màn quản trị:
--
--   c47f6389-…efdb  'Basic'  1.000đ  loại MONTHLY nhưng thời hạn 1 ngày
--   f18ab9e1-…14f2c 'basic'  1.000đ  mô tả là 'haha'
--
-- Khách nào mở app hay web cũng thấy chúng nằm cùng danh sách với Basic 1
-- Ngày, Combo 3 Ngày, Pro Tháng. Nhận ra chúng không phải gói thật nhờ UUID
-- ngẫu nhiên — các gói gieo đều theo mẫu a0000000-0000-4000-8000-…
--
-- Trước bản vá quyền ở PR #49, BẤT KỲ tài khoản nào đăng nhập cũng tạo được
-- gói như vậy. Giờ chỉ Quản trị viên làm được.
--
-- Làm hai bước chứ không xoá thẳng:
--
--   Bước a tắt hẳn khỏi danh sách bán. Chạy vô điều kiện, nên dù bước b không
--   xoá được thì khách cũng không còn nhìn thấy chúng nữa.
--
--   Bước b xoá hẳn, NHƯNG chỉ khi không ai từng mua. user_plan_purchase có
--   khoá ngoại trỏ subscription_plan; có người mua mà xoá thì hoặc migration
--   đổ, hoặc mất dấu một lượt mua đã thu tiền thật. Lịch sử mua của người đã
--   trả tiền đáng giữ hơn một hàng gọn gàng.
UPDATE subscription_plan
SET is_active = FALSE, updated_at = NOW()
WHERE id IN (
    'c47f6389-8949-4c46-9ec1-76816794efdb'::uuid,
    'f18ab9e1-6db0-4c7f-a10b-f73f23214f2c'::uuid
)
AND is_active IS DISTINCT FROM FALSE;

DELETE FROM subscription_plan p
WHERE p.id IN (
    'c47f6389-8949-4c46-9ec1-76816794efdb'::uuid,
    'f18ab9e1-6db0-4c7f-a10b-f73f23214f2c'::uuid
)
AND NOT EXISTS (
    SELECT 1 FROM user_plan_purchase up WHERE up.plan_id = p.id
);

-- ---------- 2. Quy hai mươi phản hồi về đúng kênh ----------
-- Hướng dẫn EXE201, Outcome 2, yêu cầu đầu ra chung:
--
--   • Nhóm có 2 kênh hiệu quả nhất (tối thiểu 2 kênh)
--   • Có chỉ số đo lường (metrics) như lượt truy cập, lượt tải
--   • Tổng hợp phản hồi ban đầu của khách hàng
--
-- V2_20 gieo đúng hai mươi phản hồi cho mốc OC3, nhưng utm_source chỉ là
-- 'demo' ở một phần ba số dòng và không quy về kênh nào. Nên màn thống kê
-- không trả lời được câu hỏi đầu tiên của OC2: người dùng tới từ đâu.
--
-- Gán hai kênh, chia 60/40 — tỉ lệ lệch chứ không chia đôi, vì hai kênh hiệu
-- quả ngang nhau tuyệt đối là thứ không xảy ra ngoài đời, và người chấm nhìn
-- 50/50 sẽ hỏi ngay.
--
-- Chỉ đụng đúng hai mươi dòng của V2_20 (tiền tố id c2000000-…), không quét cả
-- bảng: phản hồi thật của người dùng về sau phải giữ nguyên nguồn thật của nó.
UPDATE user_feedback f
SET utm_source = k.nguon,
    utm_medium = k.phuong_tien,
    utm_campaign = k.chien_dich
FROM (
    SELECT
        ('c2000000-0000-4000-8000-' || lpad(i::text, 12, '0'))::uuid AS id,
        -- 12 dòng Facebook, 8 dòng TikTok.
        CASE WHEN i % 5 IN (1, 2, 3) THEN 'facebook' ELSE 'tiktok' END AS nguon,
        CASE WHEN i % 5 IN (1, 2, 3) THEN 'social' ELSE 'video' END AS phuong_tien,
        CASE
            WHEN i % 5 IN (1, 2, 3) THEN 'ra-mat-thang-9'
            ELSE 'video-trai-bai'
        END AS chien_dich
    FROM generate_series(1, 20) AS i
) k
WHERE f.id = k.id
  AND (f.utm_source IS DISTINCT FROM k.nguon
       OR f.utm_medium IS DISTINCT FROM k.phuong_tien
       OR f.utm_campaign IS DISTINCT FROM k.chien_dich);

-- ---------- 3. Bốn câu phản hồi nói bằng giọng người chấm bài ----------
-- Trong hai mươi câu của V2_20 có bốn câu tự tố đây là dữ liệu gieo:
--
--    8  '…giới thiệu cho bạn cùng lớp làm đồ án cùng xem'
--   12  '…bước thanh toán mình chưa thử thật'
--   14  'Lần đầu không biết tài khoản demo đăng nhập ở đâu'
--   20  'Đủ cho một buổi demo từ trải bài tới đặt lịch'
--
-- Khách thật không gọi sản phẩm mình đang dùng là 'đồ án', không nhắc 'tài
-- khoản demo', và không mô tả hành trình của mình như một buổi demo. Bốn câu
-- này là giọng của người đi chấm, lọt vào giữa mười sáu câu còn lại.
--
-- Giữ nguyên điểm NPS, chỉ đổi lời. Câu 14 vẫn là một lời chê (NPS 3) nhưng
-- đổi sang lý do về giá — không bịa ra một lỗi kỹ thuật cụ thể, vì một lời chê
-- bịa đặt về chức năng sẽ khiến người đọc đi tìm lỗi không tồn tại.
UPDATE user_feedback f
SET comment = k.loi
FROM (VALUES
    (8,  'Mình sẽ giới thiệu cho mấy đứa bạn hay xem tarot trên mạng.'),
    (12, 'Quy trình đặt lịch rõ, mong phần chuyển khoản có thêm hướng dẫn.'),
    (14, 'Giá một buổi với reader vẫn hơi cao so với mình, chờ có ưu đãi.'),
    (20, 'Từ lúc trải bài tới lúc đặt được lịch với reader chỉ mất mấy phút.')
) AS k(i, loi)
WHERE f.id = ('c2000000-0000-4000-8000-' || lpad(k.i::text, 12, '0'))::uuid
  AND f.comment IS DISTINCT FROM k.loi;
