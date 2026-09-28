# Lấy lại dữ liệu từ Neon sau khi hạn mức reset

Viết ngày **28/09/2026**. Việc này phải làm khoảng **09/10/2026**.

---

## Chuyện đã xảy ra

Neon project `astrotarot` bị **tạm dừng** ngày 28/09 sau khi dùng hết
**108.33 / 100 CU-hrs** của gói free. Compute không khởi động được, nên không
`pg_dump` ra được.

Dữ liệu **không mất**: storage 35.46 MB vẫn còn nguyên. Chỉ là không với tới
được cho tới khi hạn mức reset.

Production đã chuyển sang Supabase, nhưng **chưa chép dữ liệu sang** — nên nó
đang chạy trên một database chỉ có schema và dữ liệu mẫu của Flyway. Ba reader
`Trần Hỗ Trợ`, `Lê Thu Lan`, `Phạm Quang Minh` (mã `b1000000-…`) là seed từ
`V2_4__seed_demo_data.sql`, không phải người thật.

### Vì sao hết 100 CU-hrs trong 19 ngày

`.github/workflows/keep-warm.yml` chạy **mỗi 10 phút** để Render khỏi ngủ. Mỗi
lần ping, backend chạm database → **Neon không bao giờ scale về 0** → tính
compute 24/7. 19 ngày × 24 giờ × 0,25 CU ≈ 108 CU-hrs. Khớp đúng con số.

**Không cần tắt workflow ấy nữa.** Supabase free không tính theo giờ compute,
và nó còn giúp giữ cho Supabase khỏi ngủ (project free bị tạm dừng sau một
tuần không hoạt động). Bài học chỉ đúng cho Neon: *đừng chĩa một cái pinger
định kỳ vào database tính tiền theo giờ compute.*

---

## Vì sao chờ được mà không sợ phải trộn dữ liệu

Bình thường, để database mới chạy vài ngày rồi mới chép cái cũ sang là chuốc
lấy việc trộn hai nguồn — trùng khoá chính, trùng email, `id` lệch nhau.

Ở đây thì không, vì **Supabase đang rỗng nên không ai đăng nhập được**: tài
khoản của họ không tồn tại trong đó. Gần như không có dữ liệu mới nào sinh ra.

Nên tới ngày reset vẫn là một lượt **chép sạch**, không phải trộn: xoá hết
Supabase rồi phục hồi nguyên vẹn từ Neon.

> Nếu trong thời gian chờ có ai đăng ký tài khoản mới trên Supabase, những
> tài khoản ấy **sẽ mất** khi xoá schema. Chấp nhận được; nhưng nếu số đó
> nhiều thì dừng lại và tính cách trộn thay vì làm theo hướng dẫn này.

---

## Các bước, theo đúng thứ tự

### 1. Kiểm Neon đã mở lại chưa

console.neon.tech → project `astrotarot`. Dải đỏ *"paused after reaching its
monthly free plan limit"* phải **biến mất**.

Chưa mất thì dừng ở đây. Hạn mức reset theo ngày kỷ niệm thanh toán, ghi ở
dòng *"Usage since …"* trên trang Projects.

### 2. Điền chuỗi kết nối

Tạo `khoa-bi-mat-db.txt` ở thư mục gốc repo (`.gitignore` đã bỏ qua mẫu
`khoa-bi-mat-*.txt`):

```
NGUON=postgresql://<user>:<matkhau>@<host>.neon.tech/neondb?sslmode=require
DICH=postgresql://postgres.ikbzlitbqifprgypeuol:<matkhau>@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require
```

- `NGUON` lấy ở Neon Console → Connect → nút Copy. Chuỗi copy ra đã có sẵn mật
  khẩu.
- `DICH` chỉ cần thay mật khẩu; host và tên đăng nhập đã đúng. Lấy mật khẩu ở
  Supabase → Project Settings → Database.
- Mật khẩu có ký tự lạ thì mã hoá URL: `@` → `%40`, dấu cách → `%20`.

### 3. Hỏi Neon còn đọc được không (10 giây, không chép gì)

```bash
bash deploy/chuyen-database.sh khoa-bi-mat-db.txt --thu
```

Trên Windows PowerShell — gõ `bash` trần sẽ trúng WSL, phải gọi bash của Git:

```powershell
& "C:\Program Files\Git\bin\bash.exe" deploy/chuyen-database.sh khoa-bi-mat-db.txt --thu
```

Ra được số dòng của `users` và `bookings` thì đi tiếp.

### 4. Xoá sạch Supabase

Supabase → SQL Editor:

```sql
drop schema public cascade;
create schema public;
```

Bắt buộc, vì Flyway đã dựng đủ bảng ở đó rồi và script từ chối chạy khi đích
chưa rỗng — đổ đè lên sinh hàng trăm lỗi `already exists` lẫn giữa lỗi thật.

### 5. Chép

```powershell
& "C:\Program Files\Git\bin\bash.exe" deploy/chuyen-database.sh khoa-bi-mat-db.txt
```

Cuối cùng nó in bảng đối chiếu số dòng hai bên. **Có dòng `LỆCH` thì dừng
lại**, đọc phần restore ở trên để biết bảng nào hỏng.

### 6. Deploy lại

Render → `astra-tarot-api` → Manual Deploy → Deploy latest commit.

Bản dump mang theo cả bảng `flyway_schema_history` của Neon, nên Flyway sẽ chỉ
chạy tiếp những migration mới hơn (ví dụ `V2_18__user_last_seen.sql`), không
chạy lại từ đầu.

### 7. Kiểm bằng dữ liệu thật, không chỉ bằng health check

```bash
curl -s "https://astra-tarot-api.onrender.com/api/v1/readers?page=0&size=50"
```

Reader có mã bắt đầu bằng `b1000000-…` là **seed**. Thấy reader thật (mã ngẫu
nhiên) thì đã sang đủ. Rồi đăng nhập thử bằng một tài khoản có sẵn — dữ liệu
chép thiếu thì trang vẫn lên bình thường, chỉ là không đăng nhập được.

### 8. Dọn

- Xoá `khoa-bi-mat-db.txt`.
- Trên Render, **xoá ba biến** `SPRING_FLYWAY_URL`, `SPRING_FLYWAY_USER`,
  `SPRING_FLYWAY_PASSWORD`. Chúng là bản sao thừa của bộ datasource và chính
  chúng đã làm deploy chết ở bước Flyway hôm 28/09 (xem PR #30). Bỏ khỏi
  `render.yaml` là chưa đủ — biến đã đặt trong dịch vụ vẫn còn đó và vẫn thắng.
- **Giữ project Neon thêm ít nhất một tuần.** Nó là bản lùi duy nhất, và lỗi
  lúc chép dữ liệu thường chỉ lộ ra sau vài ngày.
