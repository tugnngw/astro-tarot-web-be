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

## Đang chạy ở đâu

| | |
|---|---|
| Frontend | https://astro-tarot-web-fe.vercel.app |
| Backend (origin) | https://astra-tarot-api.onrender.com |
| Edge gateway (CF Worker) | https://astrotarot-edge.megalit2578.workers.dev |
| Postgres | Neon, project `astrotarot`, Singapore |
| Redis | Upstash, `astra-tarot-redis`, Singapore |

Edge (rate limit / chống scanner / DDoS qua Cloudflare): xem [`docs/CLOUDFLARE.md`](docs/CLOUDFLARE.md). Muốn FE đi qua Edge thì đặt `VITE_API_BASE_URL` trên Vercel = URL Worker rồi Redeploy.

Dựng ngày 2026-09-09. Đã kiểm chứng trên bản chạy thật: 13 migration áp sạch
lên Neon (PostgreSQL 18.6), 12 sản phẩm hiện đúng ở `/shop` với giá và nhãn
"Ảnh minh hoạ", endpoint admin trả 403 với khách, Swagger trả 404, CORS nhận
đúng domain Vercel và từ chối domain lạ, và `/auth/login` trả 400 đúng nghiệp
vụ — nghĩa là bộ đếm chống dò mật khẩu ghi được vào Upstash qua TLS.

## Đổi lại, phải chấp nhận bốn điều

| Điều | Ảnh hưởng thật |
|---|---|
| Dịch vụ **ngủ sau 15 phút** không ai truy cập | **Đây là vấn đề lớn nhất, và nó tệ hơn con số Render quảng cáo.** Đo thật lúc dựng xong: lần gọi đầu sau khi ngủ **hết 90 giây vẫn chưa xong**, lần thứ hai mất 44 giây. Lý do là phải cộng dồn ba thứ: Render dựng lại container, Spring Boot khởi động trên 0,1 CPU (log cho thấy riêng phần này ~3 phút), và Neon đánh thức compute. Xem mục "Giữ cho dịch vụ khỏi ngủ" — với đồ án cần demo thì gần như bắt buộc phải làm. |
| **512 MB RAM** | Đủ chạy, nhưng phải ghì JVM lại (đã cấu hình sẵn trong `render.yaml`). Đo thật: 360 MB lúc nhàn rỗi, 399 MB sau 270 request liên tiếp — còn dư khoảng 20%. Bỏ `JAVA_TOOL_OPTIONS` đi là bị giết vì hết bộ nhớ. |
| **0,1 CPU** | Khởi động rất chậm. Cùng image đó chạy ở máy với 0,5 CPU mất 28 giây; trên Render free, log đo được ~3 phút. Trong lúc chờ, Render in liên tục `No open ports detected, continuing to scan...` — **đó không phải lỗi**, chỉ là Render quét trong khi Spring chưa mở cổng (Tomcat mở connector ở bước cuối cùng). |
| **Đĩa không lưu được gì** | Ảnh đại diện người dùng tải lên sẽ mất sau mỗi lần deploy và mỗi lần dịch vụ ngủ dậy. Xem phần cuối. |

Vì sao **không** dùng Postgres free của Render: nó bị xoá sau 30 ngày (trước là
90). Neon thì miễn phí vĩnh viễn.

## Cập nhật code về sau — tự động trên main

Trước đây repo được thêm vào Render bằng **URL public**, không qua GitHub App,
nên ô Auto-Deploy "On Commit" vô tác dụng và phải **Manual Deploy** sau mỗi lần
push.

Giờ có hai cách tự động:

### Cách 1 (khuyên dùng): Deploy Hook + GitHub Actions

Mỗi lần push/merge vào `main`, workflow `.github/workflows/cd.yml` gọi Deploy
Hook của Render — URL đó chỉ kích hoạt lại đúng dịch vụ `astra-tarot-api`.

1. Render → `astra-tarot-api` → **Settings**  
   - Branch: **`main`** (khớp `render.yaml`)  
   - Auto-Deploy có thể để Off nếu dùng hook từ Actions
2. Cùng trang Settings → **Deploy Hook** → Create Hook → chép URL
3. GitHub → repo `astro-tarot-web-be` → **Settings → Secrets and variables →
   Actions** → New repository secret  
   - Name: `RENDER_DEPLOY_HOOK`  
   - Value: URL vừa chép

### Cách 2: cài Render GitHub App

Nhờ chủ repo `tugnngw` cài [Render GitHub App](https://github.com/apps/render)
cho repo, rồi nối lại nguồn trong Settings, bật Auto-Deploy, chọn nhánh `main`.
Cách này không cần secret `RENDER_DEPLOY_HOOK`.

Deploy tay khi cần: Render → `astra-tarot-api` → **Manual Deploy** →
*Deploy latest commit*.

(Frontend trên Vercel: xem `DEPLOY.md` bên FE — cùng kiểu Deploy Hook hoặc
`npx vercel deploy --prod`.)

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
   `main`. Render đọc `render.yaml` và điền sẵn hầu hết cấu hình.

   Nếu repo thuộc tài khoản GitHub của người khác và bạn chưa cài được Render
   lên đó, dùng **New → Web Service → Public Git Repository** rồi dán URL repo;
   sau đó chọn **Docker** và tự điền biến môi trường theo `render.yaml`.

3. Render sẽ hỏi những biến đánh dấu `sync: false`. Điền:

| Biến | Lấy ở đâu |
|---|---|
| `SPRING_DATASOURCE_URL` | chuỗi JDBC của Postgres, giữ `?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | user của Postgres |
| `SPRING_DATASOURCE_PASSWORD`, `DB_PASSWORD` | password (hai biến, cùng một giá trị) |
| `REDIS_HOST` | Endpoint của Upstash |
| `SPRING_DATA_REDIS_PASSWORD` | Password của Upstash |
| `ASTRO_ENCRYPTION_KEY` | tự sinh: `openssl rand -base64 32` |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | domain Vercel, không có `/` ở cuối |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM_EMAIL` | Gmail + mật khẩu ứng dụng 16 ký tự |

`JWT_SECRET` để Render tự sinh.

> **Không còn `SPRING_FLYWAY_URL` / `SPRING_FLYWAY_USER` / `SPRING_FLYWAY_PASSWORD`.**
> Ba biến ấy từng tồn tại và tài liệu này từng ghi rõ chúng phải *giống hệt* ba
> biến của datasource — tức là ba thứ thừa phải nhớ chép lại mỗi lần đổi cơ sở
> dữ liệu. Bỏ đi thì Flyway dùng lại datasource của ứng dụng, và không còn hai
> nơi để lệch nhau. Nếu dịch vụ trên Render còn giữ ba biến cũ thì **xoá chúng
> đi**: Spring Boot thấy `SPRING_FLYWAY_URL` là lập tức mở một kết nối riêng
> bằng đúng chuỗi đó, bỏ qua datasource.

**`ASTRO_ENCRYPTION_KEY` phải là base64 của đúng 32 byte, và cất một bản sao ở
nơi khác.** Nó mã hoá ngày sinh và nơi sinh trong CSDL; đổi khoá sau khi đã có
dữ liệu là không giải mã lại được nữa.

4. Bấm **Apply**. Lần đầu mất 5–10 phút vì phải tải phụ thuộc Maven.

Flyway tự chạy toàn bộ migration khi khởi động — không cần tạo bảng bằng tay.

## Chuyển sang Supabase (hoặc bất kỳ Postgres nào khác)

### Hai cái bẫy, và cả hai đều báo lỗi sai chỗ

**1. Host trực tiếp chỉ có IPv6.** `db.<project-ref>.supabase.co` không có bản
ghi A nào:

```
$ nslookup db.ikbzlitbqifprgypeuol.supabase.co
*** no data of the requested type
```

Render gói free không có IPv6, nên nó sẽ **không bao giờ** nối được — bất kể
mật khẩu và chứng chỉ đúng hay sai. Phải dùng host **pooler**:

| | Host | Cổng |
|---|---|---|
| Session pooler (dùng cái này) | `aws-0-<region>.pooler.supabase.com` | 5432 |
| Transaction pooler | `aws-0-<region>.pooler.supabase.com` | 6543 |

Session pooler giữ nguyên một phiên Postgres cho mỗi kết nối, nên Flyway và
Hibernate chạy bình thường. Transaction pooler trả kết nối về hồ sau *mỗi câu
lệnh*, nên nó phá prepared statement — phải thêm `prepareThreshold=0`, và ngay
cả thế thì advisory lock của Flyway cũng không còn nghĩa.

Số ở đầu (`aws-0`, `aws-1`…) là **cụm máy chủ, không phải số thứ tự để đoán**.
Trỏ nhầm cụm thì lỗi là `tenant/user ... not found`, một câu không hề gợi ra
rằng vấn đề nằm ở tên miền.

**2. Tên đăng nhập phải kèm project-ref.** `postgres.<project-ref>`, không phải
`postgres` trần — pooler dựa vào phần đuôi ấy để biết định tuyến tới dự án nào.

### Dò trước khi deploy

Không đoán. Có sẵn một công cụ dò dùng **đúng driver và đúng JVM** mà Render
dùng:

```bash
export JDBC_URL='jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres?sslmode=require'
export DB_USER='postgres.<project-ref>'
export DB_PASSWORD='...'
JAR=$(ls ~/.m2/repository/org/postgresql/postgresql/*/postgresql-*.jar | tail -1)
java -Dstdout.encoding=UTF-8 -cp "$JAR" deploy/KiemKetNoi.java
```

Nó tách ba câu hỏi mà một thông báo lỗi duy nhất gộp lại: tên miền phân giải ra
IPv4 hay chỉ IPv6, cổng TCP có mở không, và bắt tay TLS cộng đăng nhập có qua
không. Mật khẩu đọc từ biến môi trường và không bao giờ được in ra.

**"psql nối được" không chứng minh được gì.** psql dùng OpenSSL, ứng dụng dùng
tầng TLS của Java — hai thứ thương lượng khác nhau và tin bộ chứng chỉ khác
nhau. Đó là lý do công cụ trên chạy bằng Java chứ không phải một lệnh `psql`.

### Dữ liệu KHÔNG tự đi theo

Đổi biến môi trường chỉ đổi chỗ ứng dụng trỏ tới. Cơ sở dữ liệu mới **rỗng**:
Flyway sẽ dựng đủ bảng rồi chạy tiếp như không có gì, và toàn bộ tài khoản,
lịch hẹn, giao dịch nằm lại ở nơi cũ. Không có thông báo lỗi nào — trang vẫn
chạy, chỉ là trống trơn.

Chép dữ liệu sang **trước** khi đổi biến. Có sẵn script làm trọn việc:

```bash
bash deploy/chuyen-database.sh
```

Không muốn dán chuỗi bằng tay thì để sẵn trong một file rồi đưa đường dẫn vào:

```bash
bash deploy/chuyen-database.sh khoa-bi-mat-db.txt
```

File hai dòng, `NGUON=postgresql://...` và `DICH=postgresql://...`. Đặt tên
theo mẫu `khoa-bi-mat-*.txt` để `.gitignore` tự bỏ qua — nó chứa mật khẩu.

Mật khẩu có ký tự lạ (`@ : / ? # & khoảng trắng`) thì mã hoá URL: `@` thành
`%40`, dấu cách thành `%20`. Đó là yêu cầu của chuỗi kết nối, không phải của
script.

**Trên Windows, gọi bash của Git chứ đừng gõ `bash` trần** trong PowerShell —
`bash` ở đó trỏ vào WSL, và WSL không thấy được ổ đĩa theo cùng đường dẫn:

```powershell
& "C:\Program Files\Git\bin\bash.exe" deploy/chuyen-database.sh khoa-bi-mat-db.txt
```

Nó chỉ cần Docker, không cần cài `pg_dump`. Ba thứ nó làm mà hai dòng
`pg_dump`/`pg_restore` trần không làm:

- **Chọn đúng bản `pg_dump`.** Bản cũ hơn máy chủ nguồn sẽ lặng lẽ bỏ qua thứ
  nó không hiểu — dump báo thành công và thiếu dữ liệu. Script đọc phiên bản
  hai bên rồi chọn ảnh Docker cho khớp.
- **Từ chối nếu đích chưa rỗng.** Đổ đè lên database đã có bảng sinh hàng trăm
  lỗi `already exists` lẫn giữa lỗi thật, và không ai đọc hết.
- **Đếm lại từng bảng ở cả hai bên.** `pg_restore` trả mã 0 không có nghĩa là
  đủ dòng; đây mới là chỗ chứng minh dữ liệu sang tới nơi.

Chuỗi kết nối gõ vào lúc chạy chứ không truyền qua tham số — tham số thì hiện
ra trong `ps` và nằm lại trong lịch sử shell.

Giữ cơ sở dữ liệu cũ thêm ít nhất một tuần. Nó là bản lùi duy nhất, và một lỗi
lúc chép dữ liệu thường chỉ lộ ra sau vài ngày.

---

## 4. Kiểm tra

```bash
curl https://astra-tarot-api.onrender.com/actuator/health
```

Mong đợi `{"status":"UP","groups":["liveness","readiness"]}`.

Lần gọi đầu sau khi dịch vụ ngủ có thể **hết cả 90 giây mà vẫn chưa xong** — cứ
gọi lại lần nữa (lần hai đo được 44 giây). Đừng kết luận là hỏng cho tới khi đã
thử ít nhất hai lần.

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

Với đồ án cần demo thì gần như bắt buộc, vì 90 giây chờ trước mặt người chấm là
không chấp nhận được.

Dùng [cron-job.org](https://cron-job.org) (free), tạo một job gọi
`https://astra-tarot-api.onrender.com/actuator/health` mỗi **10 phút** (phải
nhỏ hơn 15 phút, nếu không dịch vụ vẫn kịp ngủ).

Cách chắc ăn hơn cho hôm demo: mở trang trước **5 phút** rồi bấm quanh vài lần.

Lưu ý cho sòng phẳng: bản free của Render có hạn mức 750 giờ chạy mỗi tháng cho
cả tài khoản. Một tháng có khoảng 730 giờ, nên giữ thức 24/7 vừa đủ **một** dịch
vụ. Có dịch vụ thứ hai là vượt hạn mức.

---

## Khi có sự cố

**Log in `No open ports detected, continuing to scan...`**

Trong lúc khởi động thì **đây là bình thường, không phải lỗi.** Render quét cổng
liên tục trong khi Spring còn đang dựng bean; Tomcat chỉ mở connector ở bước
cuối cùng, và trên 0,1 CPU bước đó tới sau khoảng 3 phút. Bạn sẽ thấy dòng này
lặp lại vài lần rồi mọi thứ vẫn chạy.

Chỉ coi là lỗi khi Render **huỷ hẳn bản deploy** với thông báo hết giờ quét
cổng. Khi đó mới là ứng dụng nghe sai cổng: `server.port=${PORT:8080}` trong
`application.properties` lo việc này, kiểm tra xem có ai đặt đè biến `PORT`
bằng giá trị khác không.

Cách phân biệt nhanh: tìm trong log dòng `Tomcat started on port 10000`. Có
dòng đó là cổng đã mở đúng.

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

## PayOS (thanh toán booking)

1. Vào [my.payos.vn](https://my.payos.vn) → **Thông tin kết nối** → chép
   `Client ID`, `API Key`, `Checksum Key`.
2. Render → Environment thêm:
   - `PAYOS_CLIENT_ID`
   - `PAYOS_API_KEY`
   - `PAYOS_CHECKSUM_KEY`
   - `PAYOS_WEBHOOK_URL=https://astra-tarot-api.onrender.com/api/v1/payments/payos/webhook`
   - (tuỳ chọn) `PAYOS_RETURN_URL` / `PAYOS_CANCEL_URL` — mặc định trỏ
     `FRONTEND_URL/bookings?payment=success|cancel`
3. Trên PayOS dashboard cũng dán cùng webhook URL (hoặc để app tự `confirm`
   lúc khởi động khi đã set `PAYOS_WEBHOOK_URL`).
4. Redeploy backend. Khách bấm **Thanh toán** sẽ nhận link PayOS; webhook xác
   nhận → tiền vào ký quỹ (không cần admin bấm confirm nữa).
