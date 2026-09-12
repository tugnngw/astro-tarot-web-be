# ASTROTAROT — tóm tắt dự án để trợ lý code nắm việc

Dán toàn bộ file này vào Cursor trước khi nhờ nó làm gì. Viết ngày 12/09/2026.

---

## 1. Dự án là gì

Nền tảng Tarot + Chiêm tinh, tiếng Việt, ba trụ cột:

1. **Trải bài Tarot bằng AI**, đọc theo bản đồ sao ngày sinh của người dùng.
2. **Đặt lịch với Reader thật** — trả tiền trước, tiền giữ ở ký quỹ, nhả cho
   Reader sau khi buổi xem hoàn tất.
3. **Gian hàng tiếp thị** — bộ bài, đá khoáng, phụ kiện. Chỉ là liên kết sang
   sàn (Shopee), nền tảng ăn hoa hồng, KHÔNG tự bán và không giữ kho.

Đây là đồ án môn học, chạy toàn bộ trên các gói miễn phí. Ràng buộc "miễn phí,
không hết hạn" chi phối rất nhiều quyết định kỹ thuật bên dưới.

---

## 2. Hai repo, và nơi chúng chạy

| | Backend | Frontend |
|---|---|---|
| Repo | `github.com/tugnngw/astro-tarot-web-be` | `github.com/tugnngw/astro-tarot-web-fe` |
| Chạy tại | https://astra-tarot-api.onrender.com | https://astro-tarot-web-fe.vercel.app |
| Nền tảng | Render (Docker, gói free) | Vercel |

**Nhánh làm việc: `feat/dat-branch`.** Xong việc thì merge vào `main`; đẩy lên
`main` là CI/CD tự deploy cả hai nơi (GitHub Actions → Render deploy hook /
Vercel CLI).

Hạ tầng khác: **Neon** (PostgreSQL 18), **Upstash** (Redis), **Brevo** (SMTP,
cổng 2525), **PayOS** (cổng thanh toán).

---

## 3. Công nghệ

**Backend** — Java 21, Spring Boot 3.5.3, Spring Security (JWT), JPA/Hibernate
6.6, Flyway (19 migration, mới nhất `V2_8`), Lombok, MapStruct, PostgreSQL.
31 entity, 20 controller, 134 bài test.

**Frontend** — React 19, TanStack Start + Router (định tuyến theo file),
TanStack Query v5, TypeScript 5.8, Tailwind 4, Vite 7, Recharts, sonner.

Xây bằng Node **24**. Node 20 KHÔNG chạy được: `@tanstack/react-start` đòi
≥22.12, và npm 10.8 đọc sai lockfile của npm 11.

---

## 4. Phân quyền

Năm vai trò. **Cấp trên KHÔNG tự thừa kế quyền cấp dưới** — liệt kê tường minh.

```
USER    : USER_BASIC, READER_APPLY
STAFF   : USER_BASIC, READER_APPLY, READER_MANAGE_PROFILE,
          SUPPORT_VIEW, SUPPORT_RESPOND, PAYOUT_REQUEST
MANAGER : USER_BASIC, SUPPORT_VIEW, STAFF_VIEW, STAFF_MANAGE,
          ADMIN_READERS_VIEW, ADMIN_READERS_REVIEW, REPORT_REVIEW,
          CATALOG_MANAGE
ADMIN   : tất cả ở trên, trừ READER_APPLY, cộng thêm
          ORDERS_MANAGE, USERS_MANAGE, AUDIT_VIEW,
          PAYMENTS_MANAGE, PAYOUT_REVIEW
```

Vài lựa chọn có chủ ý, đừng "sửa" nếu không có lý do mới:

- **MANAGER không có `SUPPORT_RESPOND`** — quản lý giám sát hàng chờ chứ không
  trực tiếp trả lời khách.
- **ADMIN không có `READER_APPLY`** — admin không đi xin làm Reader.
- **MANAGER có `CATALOG_MANAGE`, STAFF thì không.** Gian hàng là việc vận hành,
  không phải việc tài chính. Không giao cho Nhân viên vì Nhân viên chính là
  Reader đang tư vấn cho khách — để họ chọn sản phẩm đẩy lên trước mặt khách
  của mình là xung đột lợi ích.
- **Tiền (`ORDERS_MANAGE`, `PAYMENTS_MANAGE`, `PAYOUT_REVIEW`) chỉ ADMIN.**

> ⚠️ **Bảng quyền tồn tại ở HAI nơi và phải luôn khớp:**
> `CustomUserDetails.ROLE_PERMISSIONS` (BE) và `src/lib/roles.ts` (FE).
> Trang "Bảng phân quyền" của Quản trị vẽ từ bản FE — lệch là trang đó nói dối.

Mỗi vai trò có khu làm việc riêng: `/home` (thành viên), `/staff`, `/manager`,
`/admin`. Nhãn "Trang chủ" trên thanh nav luôn trỏ `/` cho mọi vai trò.

---

## 5. Dòng tiền — phần dễ sai nhất

```
Khách đặt lịch
   → trả tiền (PayOS hoặc chuyển khoản, admin xác nhận tay)
   → escrow HOLD        (pendingBalance tăng, Reader thấy nhưng chưa rút được)
   → buổi xem diễn ra
   → Reader bấm hoàn tất, HOẶC job tự chốt sau 24 giờ
   → escrow RELEASE     (trừ phí nền tảng 15%, sang balance — rút được)
   → Reader xin rút     (PAYOUT_RESERVE: trừ ngay lúc tạo lệnh)
   → admin duyệt → đánh dấu đã chuyển (PAYOUT_SETTLE)
```

Điểm cần nhớ:

- **Trừ số dư NGAY lúc tạo lệnh rút**, không đợi duyệt. Nếu chỉ kiểm tra mà
  chưa trừ, Reader tạo mười lệnh cùng một khoản và admin duyệt lần lượt là chi
  thừa chín lần.
- **`escrow_transactions` là sổ cái** — mọi lần tiền nhúc nhích đều ghi một
  dòng, kèm số dư SAU nghiệp vụ. Đừng sửa số dư mà không ghi sổ.
- **Phạt vi phạm**: chỉ áp khi kết luận báo cáo là `RESOLVED`. Phần vượt quá số
  dư ghi vào `penalty_owed` rồi trừ dần ở các lần nhả sau — KHÔNG bao giờ lấy từ
  `pendingBalance`, đó là tiền của buổi xem chưa xong, còn có thể phải hoàn cho
  khách.
- **Job tự chốt bỏ qua buổi đang có tố cáo chưa xử lý.** Nhả tiền trước rồi đòi
  lại sau là đòi từ một cái ví có thể đã rỗng.
- **Rút tiền qua QR ngân hàng.** `VietQrService` tự dựng chuỗi EMV chuẩn
  VietQR — không gọi dịch vụ sinh ảnh bên ngoài. Chuỗi QR chỉ trả cho người có
  quyền duyệt chi và chỉ khi lệnh còn phải chi.
- **Chi ra thẻ Visa: KHÔNG làm được.** Cần Visa Direct qua đối tác có giấy
  phép. PayOS có API chi hộ nhưng phải đăng ký riêng (ví Bảo Kim) và chưa đăng
  ký. Đường đi thật là tài khoản ngân hàng + QR.

---

## 6. Những cái bẫy đã cắn — ĐỌC TRƯỚC KHI SỬA

Tất cả đều **im lặng**: biên dịch sạch, test cũ xanh, chỉ nổ trên production.

### 6.1 `SecurityConfig` dùng mẫu một dấu sao

`"/api/v1/readers/*/slots"` chỉ khớp ĐÚNG một đoạn. `/slots/next-available` có
thêm một đoạn nên rơi xuống `.anyRequest().authenticated()` và trả **403 dù đã
có `@PreAuthorize("permitAll()")`** — annotation chạy sau, không cứu được.

→ Thêm endpoint công khai mới thì phải thêm mẫu vào đúng danh sách đó. Đừng
dùng `/**` vì nó nuốt cả `/readers/profile/me`.

### 6.2 Test dùng H2, không bắt được lệch kiểu với Postgres

Hồ sơ test dùng H2 với `ddl-auto=create-drop`, tức Hibernate tự tạo bảng theo
ánh xạ của chính nó nên **không bao giờ có gì để lệch**. Từng để lọt `@Lob
byte[]` (Postgres cần `bytea`, `@Lob` đòi `oid`) làm backend không khởi động
nổi, trong khi 169/169 test vẫn xanh.

### 6.3 Thêm trường vào entity — hai cái bẫy liên tiếp

**(a) Annotation bị đẩy lệch.** Annotation Java nằm ở dòng riêng phía trên khai
báo. Chèn trường mới ngay trên dòng `@Column(name = "created_at")` thì
`@CreationTimestamp` ở dòng trên nữa sẽ rơi sang trường mới. Hậu quả: Hibernate
chèn `localtimestamp` vào cột `bigint` (insert nổ), **và vứt bỏ giá trị tầng
trên gửi xuống mà không báo lỗi**. Đã dính ở 4 entity cùng lúc.

**(b) Lombok `@Builder` BỎ QUA giá trị khởi tạo.** `private Long x = 0L;` mà
thiếu `@Builder.Default` thì `builder().build()` cho ra `null` → ghi xuống cột
`NOT NULL` là nổ.

→ Hai bài test canh sẵn, chạy dưới một giây, không cần database:
`TimestampAnnotationTest` và `BuilderDefaultTest`.

### 6.4 Render gói free

- **Chặn cổng SMTP 25/465/587.** Mail phải đi qua `smtp-relay.brevo.com:2525`.
  JavaMail mặc định KHÔNG có timeout nên cổng bị chặn = luồng `@Async` treo
  vĩnh viễn, không log gì. Đã đặt timeout 10s.
- **Ngủ sau 15 phút không ai gọi**, lần gọi kế tiếp chờ 50–70 giây. `KeepAwakeJob`
  tự gọi URL công khai của chính mình mỗi 10 phút (dùng biến `RENDER_EXTERNAL_URL`
  mà Render tiêm sẵn). **Đừng dựa vào `schedule` của GitHub Actions** — đo thực
  tế: đặt 10 phút nhưng chạy mỗi ~2 tiếng, có lúc im 8 tiếng liền.
- **Hộp 512 MB.** Cờ JVM đặt ở `ENTRYPOINT` của Dockerfile (heap 35%, metaspace
  192 MB, SerialGC, `ExitOnOutOfMemoryError`). Từng bị `OutOfMemoryError:
  Metaspace` vì chia heap 65% / metaspace 128 MB. Lưu ý `ENTRYPOINT` phải là
  dạng shell — dạng exec không khai triển `$JAVA_OPTS`.

### 6.5 Webhook PayOS

Lúc đăng ký, PayOS **gọi ngược lại URL** bằng gói tin thử mang `orderCode=123`.
Trả bất cứ mã nào khác 2xx là nó từ chối đăng ký. Nên:

- orderCode không khớp giao dịch nào → ghi log rồi thôi, KHÔNG ném ngoại lệ.
- 5xx chỉ dành cho hỏng tạm thời thật (`DataAccessException`), vì lúc đó ta
  MUỐN PayOS gửi lại.
- Việc đăng ký nằm ở `PayOsWebhookRegistrar`, chạy **sau** khi ứng dụng mở
  cổng. Đặt trong `@PostConstruct` thì cú gọi ngược rơi vào instance CŨ.

### 6.6 Tên trường JSON của auth

`AuthResponse` dùng **camelCase** (`accessToken`, `refreshToken`). Từng có lỗi
`client.ts` gửi `refresh_token` → backend trả 400 → **làm mới token chưa bao
giờ chạy**, mọi người dùng bị đăng xuất cứng sau đúng 15 phút.

Kèm theo: `SecurityConfig` phải khai `authenticationEntryPoint` trả **401** cho
chưa xác thực. Không khai thì Spring mặc định trả 403 cho cả hai, và giao diện
chỉ làm mới token khi gặp 401.

---

## 7. Quy ước giao diện

### Danh sách và phân trang

Mọi danh sách dữ liệu dùng `src/components/Pagination.tsx`:

- `<Pagination>` — thanh điều khiển, luôn hiện dòng đếm "1–7 trên 20 …".
- `<PagedList>` — chừa sẵn chỗ bằng **một trang đầy**, để trang cuối ít dòng
  không làm bố cục tụt lên. Nhớ kèm cỡ trang lúc đo được.
- `useCoTrangVuaManHinh(ref)` — tính cỡ trang theo chiều cao màn hình thật, để
  một trang vừa một màn và không phải cuộn xuống mới bấm sang trang. Đo được:
  màn 900px → 7 dòng, màn 768px → 4 dòng.

Lưới thẻ truyền `buoc` bằng số cột để hàng cuối không lẻ (shop 4, danh bạ
Reader 2).

Bảy khối "top N" cố định số lượng (trang chủ, sản phẩm liên quan, thẻ tóm tắt)
KHÔNG phân trang — chúng không đổi số dòng nên cũng không nhảy.

### Khoá nút theo dòng

`src/lib/row-busy.ts` — `useRowBusy()`. Mọi màn danh sách dùng chung một
mutation cho tất cả các dòng; viết `disabled={mutation.isPending}` là bấm một
nút thì cả bảng cùng xám. Hỏi `dong.ban(id)` để chỉ khoá đúng dòng đang chạy.

### Báo lỗi

`src/components/ListError.tsx` — dịch mã lỗi sang câu người đọc hiểu. Đừng in
thẳng `error.message`: người dùng từng nhận đúng ba chữ "Invalid JSON".

`src/api/client.ts` tự thử lại GET/HEAD khi lỗi tạm thời (nhịp 2s–5s–12s, đủ
đi qua một lần máy chủ ngủ dậy). **Không thử lại POST** — gọi lại một POST có
thể đặt hai lịch hẹn.

### Khác

- Thông báo (toast) ở **góc dưới bên phải**, 2,5 giây.
- Tiếng Việt toàn bộ phần người dùng thấy.

---

## 8. Chạy ở máy

**Thứ tự khởi động:** PostgreSQL → Redis → backend → frontend.

```bash
# Backend (cổng 8080)
cd astro-tarot-web-be && ./mvnw spring-boot:run

# Frontend (cổng 8081)
cd astro-tarot-web-fe && npm run dev
```

Backend cần file `.env` (KHÔNG commit). Database cục bộ mặc định ở cổng
**5433**, không phải 5432.

**Chạy test đúng điều kiện CI** — tạm giấu `.env` đi, vì nhiều test chỉ xanh
nhờ file đó:

```bash
mv .env .env.hidden && ./mvnw test ; mv .env.hidden .env
```

Frontend lấy URL backend từ `VITE_API_BASE_URL`, **nhúng lúc build**. Đổi biến
trên Vercel xong phải Redeploy.

---

## 9. Việc còn dang dở

- **Khoá Brevo SMTP và mật khẩu tài khoản cần đổi** — đã lộ trong lịch sử hội
  thoại. Cả khoá Groq/Cerebras cũ.
- **PayOS chi hộ chưa đăng ký**, nên chi tiền vẫn là chuyển khoản tay (có QR để
  quét cho nhanh).
- **`src/features/{admin,dashboard,tarot}` còn mã chết** — `tsc --noEmit` báo
  lỗi ở đó, nên chưa bật được `tsc` trong CI. Dọn xong thì bật.
- **Dữ liệu kiểm thử trên production**: đã phạt tài khoản demo "Phạm Quang Minh"
  tổng 25.000 ₫ qua hai báo cáo thử, và có vài báo cáo `KIEM_THU*`.

---

## 10. Cách làm việc mong muốn

- **Đo, đừng đoán.** Đã có nhiều lỗi trong dự án này đến từ việc ước lượng
  chiều cao dòng, thời điểm chạy, hay nguyên nhân lỗi. Đo thì không phải đoán.
- **Test phải chạy ngược để kiểm.** Một bài test không thể thất bại còn tệ hơn
  không có test — nó tạo cảm giác an toàn giả. Đã gặp đúng chuyện này.
- **Comment giải thích VÌ SAO, không phải LÀM GÌ.** Phong cách comment hiện tại
  trong repo ghi lại cái bẫy đã cắn và lý do chọn cách này; giữ nguyên phong
  cách đó.
- **Không sửa hai bảng quyền lệch nhau.** Sửa một bên là phải sửa bên kia.
