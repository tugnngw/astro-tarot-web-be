-- ============================================================
-- Phần còn trống sau V2_4 và V2_19.
--
-- user_feedback: màn Tổng quan của quản trị đếm đến mốc 20 (OC3).
-- Không gán cho user.an và user.bich, để hai tài khoản demo khách
-- vẫn thấy hộp hỏi ý kiến khi đăng nhập.
--
-- blogs: API /api/v1/blogs đọc bảng này. Trang chủ hiện đang lấy bài
-- tĩnh từ frontend, nhưng hàng chờ duyệt bài đọc database.
-- Mỗi trạng thái một bài: nháp, chờ duyệt, đã duyệt, bị từ chối,
-- và hai bài đã xuất bản.
--
-- Không gieo cart_items / orders / order_items. Gian hàng đã chuyển
-- sang liên kết Shopee; giao diện không đọc ba bảng đó nữa.
-- ============================================================

INSERT INTO user_feedback (
    id, user_id, source, nps, rating, comment, utm_source, created_at
)
SELECT
    ('c2000000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid,
    CASE
        WHEN g.i <= 16 THEN (
            SELECT u.id FROM users u
            WHERE u.email = (ARRAY[
                'manager@astrotarot.demo',
                'staff.support@astrotarot.demo',
                'staff.lan@astrotarot.demo',
                'staff.minh@astrotarot.demo',
                'user.cuong@astrotarot.demo'
            ])[1 + ((g.i - 1) % 5)]
        )
        ELSE NULL
    END,
    (ARRAY['TAROT_AI', 'BOOKING', 'GENERAL', 'LANDING'])[1 + ((g.i - 1) % 4)],
    g.nps,
    CASE WHEN g.i > 16 THEN NULL ELSE greatest(1, least(5, (g.nps + 1) / 2)) END,
    g.comment,
    CASE WHEN g.i % 3 = 0 THEN 'demo' ELSE NULL END,
    NOW() - (g.i || ' days')::interval
FROM (VALUES
    (1,  9::smallint, 'Trải bài AI trả lời đúng câu em đang phân vân, giọng văn dễ đọc.'),
    (2, 10::smallint, 'Đặt lịch reader xong thấy rõ đã cọc hay trả hết, không phải hỏi lại.'),
    (3,  8::smallint, 'Trang sạch, dễ tìm reader theo chủ đề.'),
    (4,  7::smallint, 'Vào từ bài giới thiệu, mất một chút mới thấy nút đặt lịch.'),
    (5, 10::smallint, 'Buổi với reader đúng giờ, chat trước buổi rất hữu ích.'),
    (6,  6::smallint, 'Trải bài hơi dài khi mạng chậm, nhưng nội dung ổn.'),
    (7,  9::smallint, 'Lịch tháng dễ nhìn hơn danh sách.'),
    (8,  8::smallint, 'Mình sẽ giới thiệu cho bạn cùng lớp làm đồ án cùng xem.'),
    (9,  4::smallint, 'Muốn lưu lại bài trải để đọc hôm sau, hiện phải chụp màn hình.'),
    (10, 10::smallint, 'Reader phản hồi nhanh sau khi mình đặt cọc.'),
    (11, 9::smallint, 'Bài trải hợp với hồ sơ chiêm tinh mình đã khai.'),
    (12, 7::smallint, 'Quy trình đặt lịch rõ, bước thanh toán mình chưa thử thật.'),
    (13, 8::smallint, 'Gian hàng gợi ý sản phẩm đúng thứ mình vừa hỏi.'),
    (14, 3::smallint, 'Lần đầu không biết tài khoản demo đăng nhập ở đâu.'),
    (15, 10::smallint, 'Hàng chờ hỗ trợ trả lời được câu về lịch bị huỷ.'),
    (16, 9::smallint, 'Ghi chú sau buổi xem là phần mình giữ lại để đọc.'),
    (17, 8::smallint, 'Xem thử không cần đăng nhập, thấy đủ để quay lại.'),
    (18, 7::smallint, 'Bố cục điện thoại ổn, chữ hơi nhỏ ở lịch.'),
    (19, 6::smallint, 'Tìm reader theo giá mất thêm một nhịp lọc.'),
    (20, 9::smallint, 'Đủ cho một buổi demo từ trải bài tới đặt lịch.')
) AS g(i, nps, comment)
WHERE NOT EXISTS (
    SELECT 1 FROM user_feedback f
    WHERE f.id = ('c2000000-0000-4000-8000-' || lpad(g.i::text, 12, '0'))::uuid
)
  AND (
      g.i > 16
      OR EXISTS (
          SELECT 1 FROM users u
          WHERE u.email = (ARRAY[
              'manager@astrotarot.demo',
              'staff.support@astrotarot.demo',
              'staff.lan@astrotarot.demo',
              'staff.minh@astrotarot.demo',
              'user.cuong@astrotarot.demo'
          ])[1 + ((g.i - 1) % 5)]
      )
  );

INSERT INTO blogs (
    id, title, slug, summary, content, status,
    author_id, reviewer_id, rejection_reason, created_at, updated_at
)
SELECT
    v.id, v.title, v.slug, v.summary, v.content, v.status,
    author.id,
    reviewer.id,
    v.rejection_reason,
    NOW() - v.cach_day,
    NOW() - v.cach_day
FROM (VALUES
    ('d3000000-0000-4000-8000-000000000001'::uuid,
     'Cách đặt một câu hỏi trước khi trải bài',
     'cach-dat-cau-hoi-truoc-khi-trai-bai',
     'Một câu hỏi hẹp giúp buổi xem khỏi loãng.',
     E'Đừng hỏi "tình yêu của tôi thế nào". Hãy hỏi một việc bạn đang phải chọn trong hai tuần tới.\n\nCâu hỏi tốt có thời điểm, có người liên quan, và có một quyết định. Reader dựa vào đó để chọn cách trải, còn bạn rời buổi xem với một việc làm được ngay.',
     'PUBLISHED', 'staff.lan@astrotarot.demo', 'manager@astrotarot.demo', NULL::text, INTERVAL '12 days'),

    ('d3000000-0000-4000-8000-000000000002'::uuid,
     'Đặt cọc và trả hết khác nhau chỗ nào',
     'dat-coc-va-tra-het',
     'Cọc giữ chỗ. Trả hết thì Reader nhận tiền sau buổi.',
     E'Đặt cọc 50% để giữ khung giờ. Phần còn lại trả sau khi Reader đánh dấu buổi đã xong, không bị huỷ tự động trước giờ hẹn.\n\nThanh toán hết ngay từ đầu thì không còn bước trả nốt. Cả hai cách đều mở được chat với Reader sau khi tiền đã vào.',
     'PUBLISHED', 'staff.minh@astrotarot.demo', 'manager@astrotarot.demo', NULL::text, INTERVAL '8 days'),

    ('d3000000-0000-4000-8000-000000000003'::uuid,
     'Ba lá cho một tuần thi cử',
     'ba-la-cho-mot-tuan-thi-cu',
     'Bài đã duyệt, chờ xuất bản.',
     E'Lá thứ nhất là việc đã chuẩn bị. Lá giữa là môn đang lệch. Lá cuối chỉ ra một thói quen nên giữ trong tuần này, không phải một kết quả điểm số.',
     'APPROVED', 'staff.lan@astrotarot.demo', 'manager@astrotarot.demo', NULL::text, INTERVAL '3 days'),

    ('d3000000-0000-4000-8000-000000000004'::uuid,
     'Khi nào nên nhắn Reader trước buổi',
     'khi-nao-nen-nhan-reader-truoc-buoi',
     'Đang chờ quản lý duyệt.',
     E'Nhắn trước khi bạn cần Reader biết một mốc thời gian hoặc một người thứ ba trong câu chuyện. Không cần kể hết — hai câu là đủ để buổi khỏi mở đầu bằng việc tóm tắt.',
     'PENDING', 'staff.support@astrotarot.demo', NULL::text, NULL::text, INTERVAL '1 day'),

    ('d3000000-0000-4000-8000-000000000005'::uuid,
     'Dự đoán trúng số theo ngày sinh',
     'du-doan-trung-so-theo-ngay-sinh',
     'Bị từ chối vì hứa một kết quả không có cơ sở.',
     E'Bản nháp hứa đọc được dãy số từ ngày sinh.',
     'REJECTED', 'staff.lan@astrotarot.demo', 'manager@astrotarot.demo',
     'Không đăng bài hứa một kết quả số. Viết lại theo hướng câu hỏi mở và giới hạn của một buổi xem.',
     INTERVAL '6 days'),

    ('d3000000-0000-4000-8000-000000000006'::uuid,
     'Ghi chú sau buổi xem nên viết những gì',
     'ghi-chu-sau-buoi-xem',
     'Nháp, chưa gửi duyệt.',
     E'Ghi ba ý khách cần nhớ, không chép lại cả buổi. Một câu về câu hỏi, một câu về hướng đang thấy, một câu về việc khách tự làm tuần tới.',
     'DRAFT', 'staff.minh@astrotarot.demo', NULL::text, NULL::text, INTERVAL '5 hours')
) AS v(id, title, slug, summary, content, status, email_tac_gia, email_duyet, rejection_reason, cach_day)
JOIN users author ON author.email = v.email_tac_gia
LEFT JOIN users reviewer ON reviewer.email = v.email_duyet
WHERE NOT EXISTS (SELECT 1 FROM blogs b WHERE b.id = v.id OR b.slug = v.slug);
