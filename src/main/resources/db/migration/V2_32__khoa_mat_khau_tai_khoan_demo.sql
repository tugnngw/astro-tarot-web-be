-- ============================================================
-- Vô hiệu hoá mật khẩu của các tài khoản demo.
-- ============================================================
--
-- Repo này CÔNG KHAI, và mật khẩu chung của mọi tài khoản demo
-- từng được ghi thẳng ở ba file tài liệu, cùng mấy comment trong migration. Trong
-- số tài khoản ấy có `admin@example.com` mang vai trò ADMIN, tức quyền cao nhất:
-- xem mọi tài khoản, mọi giao dịch, đổi bảng giá, xoá người dùng.
--
-- Gỡ khỏi tài liệu là chưa đủ: hash bcrypt của nó vẫn nằm
-- nguyên trong V1_2 và V2_4 — hai migration đã chạy, không sửa nội dung được
-- vì Flyway so checksum. Mà đó là một chuỗi dò ra tức thì. Nên muốn đóng
-- thật thì phải đổi thứ nằm trong cơ sở dữ liệu.
--
-- ------------------------------------------------------------
-- Vì sao không đặt một mật khẩu mới ở đây
-- ------------------------------------------------------------
-- Vì migration cũng nằm trong repo công khai. Đặt mật khẩu mới vào đây là lặp
-- lại đúng sai lầm cũ, chỉ khác chuỗi ký tự.
--
-- Nên ở đây chỉ XOÁ khả năng đăng nhập: thay hash bằng một chuỗi ngẫu nhiên
-- không ứng với mật khẩu nào. Muốn dùng lại tài khoản demo thì quản trị viên
-- đặt mật khẩu mới qua màn quản trị (POST /api/v1/admin/users/{id}/password-
-- reset) — đường đó không để lại mật khẩu ở bất kỳ tệp nào.
--
-- ------------------------------------------------------------
-- Chốt chặn không tự khoá mình ra ngoài
-- ------------------------------------------------------------
-- Tài khoản ADMIN chỉ bị khoá khi hệ thống CÒN một quản trị viên khác đang
-- hoạt động. Nếu `admin@example.com` đang là quản trị viên duy nhất thì lệnh
-- dưới không đụng tới nó, và cảnh báo ở cuối file sẽ nói rõ.
--
-- Đây là lý do migration này an toàn để chạy thẳng lên production mà không cần
-- biết trước nhóm đang dùng tài khoản nào.

-- ---------- 1. Tài khoản demo KHÔNG phải quản trị viên ----------
-- Nhóm này khoá vô điều kiện: mất quyền đăng nhập vào một tài khoản demo thì
-- không ai bị kẹt, mà để ngỏ thì người lạ vào xem được trò chuyện với Reader,
-- phiếu hỗ trợ và hồ sơ chiêm tinh của khách thật.
UPDATE users
SET password_hash = '{noop}da-khoa-' || gen_random_uuid(),
    updated_at = NOW()
WHERE email IN (
    'manager@astrotarot.demo',
    'staff.support@astrotarot.demo',
    'staff.lan@astrotarot.demo',
    'staff.minh@astrotarot.demo',
    'user.an@astrotarot.demo',
    'user.bich@astrotarot.demo',
    'user.cuong@astrotarot.demo',
    'user.banned@astrotarot.demo'
)
  AND password_hash NOT LIKE '{noop}da-khoa-%';

-- ---------- 2. Tài khoản quản trị demo ----------
-- Chỉ khoá khi còn quản trị viên khác. `AND EXISTS` ở đây là chốt chặn, không
-- phải tối ưu: thiếu nó thì một hệ thống chỉ có đúng tài khoản này làm quản
-- trị sẽ không còn ai vào được màn quản trị để sửa chữa.
UPDATE users u
SET password_hash = '{noop}da-khoa-' || gen_random_uuid(),
    updated_at = NOW()
WHERE u.email = 'admin@example.com'
  AND u.password_hash NOT LIKE '{noop}da-khoa-%'
  AND EXISTS (
      SELECT 1 FROM users k
      WHERE k.role = 'ADMIN'
        AND k.deleted_at IS NULL
        AND k.status = 'ACTIVE'
        AND k.email <> 'admin@example.com'
  );

-- ---------- 3. Thu hồi mọi phiên đang mở của các tài khoản trên ----------
-- Đổi mật khẩu mà không thu hồi phiên thì ai đang đăng nhập sẵn vẫn ở nguyên
-- trong đó.
UPDATE user_sessions s
SET revoked = TRUE
WHERE s.revoked = FALSE
  AND s.user_id IN (
      SELECT id FROM users
      WHERE password_hash LIKE '{noop}da-khoa-%'
  );

-- ---------- 4. Nói ra nếu tài khoản quản trị demo vẫn còn mở ----------
DO $$
DECLARE con_mo BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1 FROM users
        WHERE email = 'admin@example.com'
          AND password_hash NOT LIKE '{noop}da-khoa-%'
    ) INTO con_mo;

    IF con_mo THEN
        RAISE WARNING 'admin@example.com VAN DUNG DUOC MAT KHAU CU vi he thong khong con quan tri vien nao khac. Hay cap quyen ADMIN cho mot tai khoan that, roi doi mat khau tai khoan nay qua man quan tri.';
    END IF;
END $$;
