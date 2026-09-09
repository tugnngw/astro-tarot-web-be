| **512 MB RAM** | Đủ chạy, nhưng phải ghì JVM lại (đã cấu hình sẵn trong `render.yaml`). Đã đo thật: 360 MB lúc nhàn rỗi, 399 MB sau 270 request liên tiếp — còn dư khoảng 20%. Bỏ `JAVA_TOOL_OPTIONS` đi là bị giết vì hết bộ nhớ. |
| **0,1 CPU** | Khởi động chậm. Ở máy với 0,5 CPU mất 28 giây; trên Render free có thể tới 1–2 phút. Lần deploy đầu đừng vội tưởng là hỏng. |
# Đưa backend lên Render + Neon + Upstash (miễn phí, không hết hạn)

Đây là đường rẻ tiền nhất: **không tốn đồng nào, không cần thẻ, không hết hạn
sau một tháng**. Nếu bạn có VPS thì dùng `DEPLOY.md` thay cho file này.

```
Trình duyệt ──https──▶ Vercel (FE)
     │
     └────https──▶ Render (backend)  ──▶ Neon (Postgres)
                                      └▶ Upstash (Redis)
```

Điểm ăn tiền: Render cấp sẵn tên miền `*.onrender.com` **kèm HTTPS tự động**.
Không phải mua tên miền, không phải xin chứng chỉ Let's Encrypt, không phải
dựng nginx — ba thứ khó nhất của đường VPS biến mất.

## Đổi lại, phải chấp nhận ba điều

| Điều | Ảnh hưởng thật |
|---|---|
| Dịch vụ **ngủ sau 15 phút** không ai truy cập | Lần tải đầu tiên sau khi ngủ mất khoảng 50 giây. Khi demo, hãy mở trang trước vài phút cho nó thức dậy. |
| **512 MB RAM** | Đủ chạy, nhưng phải ghì JVM lại (đã cấu hình sẵn trong `render.yaml`). Đã đo thật: 360 MB lúc nhàn rỗi, 399 MB sau 270 request liên tiếp — còn dư khoảng 20%. Bỏ `JAVA_TOOL_OPTIONS` đi là bị giết vì hết bộ nhớ. |
| **0,1 CPU** | Khởi động chậm. Đo ở máy với 0,5 CPU mất 28 giây; trên Render free có thể tới 1–2 phút. Lần deploy đầu đừng vội tưởng là hỏng. |
| **Đĩa không lưu được gì** | Ảnh đại diện người dùng tải lên sẽ mất sau mỗi lần deploy và mỗi lần dịch vụ ngủ dậy. Xem phần cuối. |

Vì sao **không** dùng Postgres free của Render: nó bị xoá sau 30 ngày (trước là
90). Neon thì miễn phí vĩnh viễn.

---

## 1. Tạo Postgres ở Neon

1. Vào [neon.com](https://neon.com), đăng ký (không cần thẻ).
2. Create project → chọn region **Singapore (ap-southeast-1)** cho gần Việt Nam.
3. Vào **Connection Details**, đổi kiểu hiển thị sang **Java / JDBC**. Chép ba
   giá trị: URL, user, password.

Chuỗi JDBC trông như:

```
jdbc:postgresql://ep-xxx-123.ap-southeast-1.aws.neon.tech/neondb?sslmode=require
```

**Giữ nguyên `?sslmode=require`.** Bỏ đi là Neon từ chối kết nối.

Neon tự ngủ sau 5 phút không dùng và thức lại trong khoảng một giây — không cần
làm gì thêm.

## 2. Tạo Redis ở Upstash

1. Vào [upstash.com](https://upstash.com), đăng ký (không cần thẻ).
2. Create Database → region **ap-southeast-1**.
3. Vào tab **Details**, chép **Endpoint** (dạng `abc-12345.upstash.io`) và
   **Password**.

Cổng là `6379`, và **bắt buộc TLS** — `render.yaml` đã đặt sẵn
`REDIS_SSL_ENABLED=true`.

## 3. Dựng backend trên Render

1. Vào [render.com](https://render.com), đăng ký bằng GitHub.
2. **New → Blueprint**, trỏ vào repo `astro-tarot-web-be`, nhánh
   `feat/dat-branch`. Render đọc `render.yaml` và điền sẵn hầu hết cấu hình.

   Nếu repo thuộc tài khoản GitHub của người khác và bạn chưa cài được Render
   lên đó, dùng **New → Web Service → Public Git Repository** rồi dán URL repo;
   sau đó chọn **Docker** và tự điền biến môi trường theo `render.yaml`.

3. Render sẽ hỏi những biến đánh dấu `sync: false`. Điền:

| Biến | Lấy ở đâu |
|---|---|
| `SPRING_DATASOURCE_URL`, `SPRING_FLYWAY_URL` | chuỗi JDBC của Neon (giống nhau) |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_FLYWAY_USER` | user của Neon |
| `SPRING_DATASOURCE_PASSWORD`, `SPRING_FLYWAY_PASSWORD`, `DB_PASSWORD` | password của Neon (cả ba giống nhau) |
| `REDIS_HOST` | Endpoint của Upstash |
| `SPRING_DATA_REDIS_PASSWORD` | Password của Upstash |
| `ASTRO_ENCRYPTION_KEY` | tự sinh: `openssl rand -base64 32` |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | domain Vercel, không có `/` ở cuối |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM_EMAIL` | Gmail + mật khẩu ứng dụng 16 ký tự |

`JWT_SECRET` để Render tự sinh.

**`ASTRO_ENCRYPTION_KEY` phải là base64 của đúng 32 byte, và cất một bản sao ở
nơi khác.** Nó mã hoá ngày sinh và nơi sinh trong CSDL; đổi khoá sau khi đã có
dữ liệu là không giải mã lại được nữa.

4. Bấm **Apply**. Lần đầu mất 5–10 phút vì phải tải phụ thuộc Maven.

Flyway tự chạy toàn bộ migration khi khởi động — không cần tạo bảng bằng tay.

## 4. Kiểm tra

```bash
curl https://astra-tarot-api.onrender.com/actuator/health
```

Mong đợi `{"status":"UP"}`. Lần gọi đầu có thể mất 50 giây nếu dịch vụ đang ngủ.

## 5. Nối FE với BE

Trên Vercel, đặt biến `VITE_API_BASE_URL` = `https://astra-tarot-api.onrender.com`
rồi **Redeploy** (biến này nhúng lúc build, không đọc lúc chạy).

Rồi quay lại Render, sửa `FRONTEND_URL` và `CORS_ALLOWED_ORIGINS` thành domain
Vercel thật. Render tự deploy lại khi đổi biến môi trường.

## 6. Tạo tài khoản quản trị đầu tiên

Đăng ký một tài khoản bình thường trên trang, rồi vào Neon → **SQL Editor**:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'email-cua-ban@gmail.com';
```

---

## Ảnh đại diện sẽ bị mất

Đĩa của Render bản free không lưu được gì: mọi file ứng dụng ghi ra sẽ biến mất
sau mỗi lần deploy và mỗi lần dịch vụ ngủ dậy. Ảnh đại diện người dùng tải lên
(`app.upload.dir`) nằm trong số đó.

Ba cách xử lý, tuỳ mức độ bạn quan tâm:

- **Kệ nó.** Với đồ án thì chấp nhận được — người dùng tải lại ảnh là xong.
- **Chuyển sang lưu ngoài** (Cloudinary có bản free vĩnh viễn). Cần sửa code
  phần upload.
- **Trả tiền đĩa Render** (7 USD/tháng cho 1 GB) — nhưng như vậy không còn free.

## Giữ cho dịch vụ khỏi ngủ

Có thể dùng [cron-job.org](https://cron-job.org) (free) gọi
`https://astra-tarot-api.onrender.com/actuator/health` mỗi 10 phút.

Lưu ý cho sòng phẳng: bản free của Render có hạn mức 750 giờ chạy mỗi tháng cho
cả tài khoản. Một tháng có khoảng 730 giờ, nên giữ thức 24/7 vừa đủ **một** dịch
vụ. Có dịch vụ thứ hai là vượt hạn mức.

---

## Khi có sự cố

**Deploy hỏng: "no open ports detected"**

Ứng dụng nghe sai cổng. `server.port=${PORT:8080}` trong `application.properties`
lo việc này; kiểm tra xem có ai đặt đè biến `PORT` không.

**Log báo `Connection refused` tới Redis, hoặc treo rồi hết giờ**

Thiếu TLS. Đặt `REDIS_SSL_ENABLED=true`.

**Log báo Postgres từ chối kết nối**

Chuỗi JDBC mất `?sslmode=require`.

**Container bị giết giữa chừng, log cụt ngang không có ngoại lệ**

Hết RAM. Kiểm tra `JAVA_TOOL_OPTIONS` có đúng như trong `render.yaml` không.

**Trình duyệt báo lỗi CORS**

`CORS_ALLOWED_ORIGINS` chưa khớp domain Vercel. Chép đúng domain trên thanh địa
chỉ, kể cả `https://`, và không có `/` ở cuối.

**Mail xác minh có link trỏ về localhost**

Chưa đặt `FRONTEND_URL`.
