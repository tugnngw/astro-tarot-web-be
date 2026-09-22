-- ============================================================
-- BỎ HẲN VAI TRÒ 'READER', GỘP VÀO 'STAFF'
-- ============================================================
-- V2_0 đã làm đúng việc này một lần (UPDATE users SET role='STAFF' WHERE
-- role='READER') nhưng chỉ sửa DỮ LIỆU, không sửa MÃ: ReaderServiceImpl vẫn
-- gán UserRole.READER mỗi khi duyệt đơn, nên vai trò đã bỏ cứ được đẻ lại.
-- V2_12 rồi lại nới CHECK constraint cho 'READER' đi qua, khoá cuối cùng
-- cũng mất nốt. Lần này sửa cả ba: dữ liệu, mã, và ràng buộc.
--
-- VÌ SAO GỘP chứ không giữ hai vai:
--
-- 'READER' là TẬP CON THẬT SỰ của 'STAFF' — nó không thêm quyền nào, chỉ
-- thiếu SUPPORT_RESPOND (và quyền duyệt đơn người khác). Nên một nhân viên
-- được duyệt làm Reader sẽ BỊ GIÁNG QUYỀN: vẫn nhìn thấy hàng chờ hỗ trợ
-- nhưng không trả lời khách được nữa. Thăng chức mà mất quyền.
--
-- Và hệ thống vốn đã có cách tốt hơn để trả lời "người này có phải Reader
-- không": SỰ TỒN TẠI của một hàng trong reader_profiles. Danh sách Reader
-- công khai đọc từ đó, đặt lịch join vào đó, màn hình hồ sơ kiểm tra nó.
-- Vai trò 'READER' chỉ là bản sao thứ hai của cùng một sự thật, nằm chỗ
-- khác và phải tự tay giữ cho khớp — nên nó đã lệch.
--
-- Đánh đổi đã cân nhắc: gộp nghĩa là mọi Reader đều có SUPPORT_VIEW, tức đọc
-- được phiếu hỗ trợ của khách khác. Chấp nhận được khi Reader là người trong
-- nhóm. Ngày tuyển Reader cộng tác viên bên ngoài thì thứ cần thêm là một
-- BẬC TIN CẬY THẤP HƠN STAFF (không có SUPPORT_VIEW) — không phải khôi phục
-- 'READER', vì 'READER' cũng có SUPPORT_VIEW nên chưa từng giải quyết được
-- vấn đề đó.

UPDATE users SET role = 'STAFF' WHERE role = 'READER';

-- Siết ràng buộc lại. Đây mới là thứ khiến lần gộp này khác lần trước: không
-- còn đường nào để 'READER' quay lại, kể cả khi ai đó lỡ tay viết vào DB.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'STAFF', 'MANAGER', 'ADMIN'));
