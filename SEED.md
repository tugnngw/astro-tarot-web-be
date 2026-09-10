# Seed data demo (Flyway V2_4)

Migration `V2_4__seed_demo_data.sql` tạo sẵn dữ liệu để test mọi vai trò.
Chạy khi backend khởi động (Flyway tự migrate) hoặc trên Neon sau khi deploy.

**Mật khẩu chung:** `admin123`

## Tài khoản theo role

| Email | Role | Trang chủ FE | Dùng để thử |
|-------|------|--------------|-------------|
| `admin@example.com` | ADMIN | `/admin` | Quản trị, phân quyền, shop, tiền |
| `manager@astrotarot.demo` | MANAGER | `/manager` | Duyệt Reader, báo cáo, xem hàng chờ hỗ trợ (không trả lời) |
| `staff.support@astrotarot.demo` | STAFF | `/staff` | Hỗ trợ khách + Reader nhẹ |
| `staff.lan@astrotarot.demo` | STAFF (Reader) | `/staff` | Lịch hẹn, thu nhập, hồ sơ Reader |
| `staff.minh@astrotarot.demo` | STAFF (Reader) | `/staff` | Reader thứ hai, booking PENDING |
| `user.an@astrotarot.demo` | USER | `/home` | Tarot AI, đặt lịch, ticket OPEN |
| `user.bich@astrotarot.demo` | USER | `/home` | Đã có booking COMPLETED + review |
| `user.cuong@astrotarot.demo` | USER | `/home` | Hồ sơ Reader đang PENDING |
| `user.banned@astrotarot.demo` | USER (BANNED) | — | Thử tài khoản bị khoá |

## Đã seed sẵn

- 3 hồ sơ Reader + lịch tuần T2–T6 + 1 ngày nghỉ của Lan
- Hồ sơ chiêm tinh (An, Bích + “Người ấy”)
- Hồ sơ Reader: PENDING / APPROVED / REJECTED
- Booking: COMPLETED, CONFIRMED, PENDING, CANCELLED
- Thanh toán, ký quỹ, lệnh rút PENDING/REJECTED
- 1 đánh giá 5★ cho Lan
- Báo cáo vi phạm PENDING + RESOLVED
- Ticket hỗ trợ OPEN / PENDING / RESOLVED kèm tin nhắn
- 1 lượt trải bài AI + chat
- Thông báo, activity log, lượt bấm shop affiliate

Shop sản phẩm + 78 lá Tarot đã có từ migration cũ (V1_3, V1_5).

## Lưu ý

- Seed **idempotent**: chạy lại không nhân đôi hàng (theo email/id cố định).
- Ciphertext chiêm tinh là placeholder; API đọc sẽ fallback sang cột plaintext.
- Muốn xoá data demo: xoá user `*@astrotarot.demo` (CASCADE phần lớn quan hệ) — **không** xoá `admin@example.com` nếu vẫn cần.
