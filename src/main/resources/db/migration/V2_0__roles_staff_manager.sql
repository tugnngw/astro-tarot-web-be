-- ============================================================
-- Phân tầng quyền: USER / STAFF / MANAGER / ADMIN
--
-- GUEST không nằm trong bảng này: đó là trạng thái CHƯA đăng nhập, không có
-- hàng nào trong users. Đưa GUEST thành một giá trị role sẽ tạo ra tài khoản
-- "khách" có thật, đăng nhập được — sai với ý nghĩa của nó.
--
-- READER gộp vào STAFF theo quyết định của chủ dự án. Người đã được duyệt làm
-- Reader vẫn giữ nguyên ReaderProfile và lịch làm việc; chỉ nhãn role đổi tên,
-- nên không mất dữ liệu và không ai bị mất quyền đang có.
-- ============================================================

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

-- Đổi dữ liệu TRƯỚC khi gắn ràng buộc mới, nếu không những hàng READER cũ sẽ
-- làm ALTER TABLE thất bại và cả migration bị rollback.
UPDATE users SET role = 'STAFF' WHERE role = 'READER';

ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'STAFF', 'MANAGER', 'ADMIN'));

COMMENT ON COLUMN users.role IS
    'USER: khách đã đăng ký. STAFF: nhân viên hỗ trợ khách, gồm cả Reader nhận booking. MANAGER: quản lý nhân sự, duyệt hồ sơ Reader. ADMIN: toàn quyền, gồm sản phẩm, đơn hàng và phân quyền.';
