# Chạy cả stack ở máy: web và app

Ba phần chạy độc lập nhưng phải lên theo thứ tự, vì phần sau nói chuyện với
phần trước:

```
Postgres + Redis  →  Backend (8080)  →  Web (8081)
                                     →  App Flutter (máy ảo)
```

Mỗi bước dưới đây đều có cách tự kiểm trước khi sang bước sau. Bỏ qua việc tự
kiểm thì lỗi sẽ hiện ra ở tầng trên cùng và nói sai chỗ hỏng.

---

## 0. Cần sẵn những gì

| Thứ | Kiểm bằng |
|---|---|
| Java 21 | `java -version` |
| Node 20+ | `node -v` |
| Flutter | `flutter --version` |
| Postgres 16 và Redis 7 | qua Docker, hoặc cài thẳng (xem 1b) |

---

## 1. Postgres và Redis

### 1a. Bằng Docker (cách thường)

```bash
cd "C:\Users\LENOVO\OneDrive\Máy tính\astro-tarot-web-be"
docker compose up -d postgres redis
```

`docker-compose.yml` map Postgres ra **cổng 5433** của máy, không phải 5432.
Cố ý, để không giẫm lên một Postgres nào đã cài sẵn. Mọi chỗ cấu hình ở máy
đều phải là 5433.

Chỉ dựng hai dịch vụ này, **không** dựng service `backend` trong compose: nó
build lại cả image Docker cho mỗi lần sửa mã, còn chạy bằng Maven thì sửa xong
nạp lại ngay.

Tự kiểm:

```bash
docker compose ps
```

Cả hai phải ở trạng thái `healthy`, không phải `starting`.

> **Docker Desktop báo không tìm thấy `dockerDesktopLinuxEngine`** thì socket
> của nó hỏng. Chuyện này ở máy hiện tại chỉ hết sau khi khởi động lại máy —
> không có cách nào chữa nhanh hơn. Trong lúc chờ, dùng 1b.

### 1b. Không có Docker

Cài Postgres 16 và Redis cho Windows, rồi tạo database:

```sql
CREATE DATABASE "astra-tarot";
```

Nếu Postgres của bạn nghe ở **5432** thay vì 5433, sửa `.env`:

```
DB_PORT=5432
```

Flyway sẽ tự tạo toàn bộ bảng lúc backend khởi động lần đầu. Không phải chạy
tay file SQL nào.

---

## 2. Backend

### Lần đầu: tạo `.env`

```bash
cp .env.example .env
```

`.env.example` ghi rõ từng biến. Bốn biến **bắt buộc**, thiếu là app chết ngay
lúc khởi động chứ không chạy lỗi ngầm:

| Biến | Ràng buộc |
|---|---|
| `DB_PASSWORD` | trùng với mật khẩu Postgres ở bước 1 |
| `JWT_SECRET` | **≥ 32 ký tự**, `JwtSecretValidationConfig` kiểm lúc start |
| `ASTRO_ENCRYPTION_KEY` | **base64 của đúng 32 byte** |
| `GEMINI_API_ENDPOINT` | có thể là giá trị giả, nhưng không được để trống |

Sinh hai khoá:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # ASTRO_ENCRYPTION_KEY
```

Những biến còn lại (PayOS, Google, SMTP, Cloudflare) để trống được — app vẫn
lên, chỉ tính năng tương ứng tắt và nói rõ trong log.

### Chạy

```bash
./mvnw spring-boot:run
```

Tự kiểm:

```bash
curl http://localhost:8080/ping
```

Phải trả `200`. `/ping` cố ý **không** hỏi database, nên 200 ở đây chỉ nghĩa
là tiến trình còn sống. Muốn biết database đã thông thì gọi một đường có đọc
dữ liệu:

```bash
curl "http://localhost:8080/api/v1/readers?size=1"
```

Trong log khởi động phải thấy Flyway chạy tới bản mới nhất:

```
Successfully applied N migrations to schema "public", now at version vX.Y
```

### Tài khoản có sẵn

Migration `V2_4` gieo sẵn dữ liệu cho mọi vai trò. Mật khẩu chung `admin123`.
Danh sách đầy đủ ở [`SEED.md`](../SEED.md); hay dùng nhất:

| Email | Vai trò |
|---|---|
| `admin@example.com` | Quản trị viên |
| `staff.lan@astrotarot.demo` | Reader (có lịch, có đánh giá) |
| `user.an@astrotarot.demo` | Khách |

---

## 3. Web

```bash
cd "C:\Users\LENOVO\OneDrive\Máy tính\astro-tarot-web-fe"
npm install        # chỉ lần đầu
cp .env.example .env
npm run dev
```

Mở http://localhost:8081

**Cổng 8081 là cố định** (`strictPort: true` trong `vite.config.ts`). Nếu cổng
đang bận thì Vite báo lỗi và dừng chứ không tự nhảy sang cổng khác — cố ý, vì
backend khai `FRONTEND_URL=http://localhost:8081` cho CORS và cho link trong
mail. Vite tự đổi cổng thì CORS gãy mà thông báo lỗi lại không nhắc gì tới
cổng.

`VITE_API_BASE_URL` được **nhúng lúc build**, không đọc lúc chạy. Đổi giá trị
thì phải khởi động lại `npm run dev`; nhấn F5 không ăn thua.

Tự kiểm: mở Console của trình duyệt, phải thấy

```
API_BASE = http://localhost:8080
```

---

## 4. App Flutter

Dự án nằm ở `C:\src\astrotarot_mobile`, **không** nằm cùng chỗ với hai repo
kia. Lý do: `flutter analyze` chết khi đường dẫn có dấu tiếng Việt, mà hai
repo web lại nằm trong `OneDrive\Máy tính`. Đừng chuyển nó vào thư mục có dấu.

```bash
cd C:\src\astrotarot_mobile
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080 ^
            --dart-define=WEB_BASE_URL=http://10.0.2.2:8081
```

### Vì sao `10.0.2.2` chứ không phải `localhost`

Trong máy ảo Android, `localhost` là **chính máy ảo đó**, không phải máy tính
của bạn. `10.0.2.2` là địa chỉ mà máy ảo dùng để gọi ngược về máy chủ. Máy ảo
iOS thì ngược lại, `localhost` mới đúng.

### Vì sao phải truyền cả `WEB_BASE_URL`

Ảnh sản phẩm được backend trả về dạng đường dẫn tương đối (`/products/...`).
Trên web chúng tự khớp vì trang và ảnh cùng tên miền; trong app thì không có
"trang" nào để khớp nên phải ghép tay. Để mặc định thì app đi lấy ảnh từ
production trong khi dữ liệu lấy từ máy — hai nguồn lệch nhau, ảnh vỡ.

### Bẫy: Android chặn HTTP thường

`flutter.targetSdkVersion` hiện là **36**, mà từ API 28 Android chặn mọi kết
nối HTTP không mã hoá trừ khi ứng dụng khai báo cho phép. Chạy lệnh trên mà
không khai thì app lên được nhưng mọi lời gọi API đều chết với:

```
CLEARTEXT communication to 10.0.2.2 not permitted by network security policy
```

Đã mở sẵn trong `android/app/src/debug/AndroidManifest.xml` — **chỉ cho bản
debug**, bản phát hành vẫn chặn như cũ.

### Hai bẫy khác của máy này

Đã ghi trong [README của repo app](https://github.com/Megalit2578/astro-tarot-mobile#hai-cái-bẫy-đã-gặp-trên-máy-này):
thư mục `android-37.0` phải tạo junction thành `android-37`, và `compileSdk`
phải ghim 37.

---

## Thứ tự tắt

Ngược lại thứ tự bật. Riêng Postgres thì dữ liệu nằm trong volume Docker nên
`docker compose down` không mất gì; chỉ `docker compose down -v` mới xoá sạch
database, và lần chạy sau Flyway sẽ dựng lại từ đầu kèm dữ liệu gieo.

---

## Khi có gì đó không chạy

| Triệu chứng | Chỗ cần nhìn |
|---|---|
| Web hiện "Máy chủ đang khởi động" mãi | backend chưa lên, hoặc `VITE_API_BASE_URL` sai |
| Backend chết lúc start, nhắc `JWT_SECRET` | khoá ngắn hơn 32 ký tự |
| Backend chết, `Encryption key must be 256 bits` | `ASTRO_ENCRYPTION_KEY` không phải base64 của 32 byte |
| `Connection refused` tới 5433 | Postgres chưa lên, hoặc bạn đang trỏ 5432 |
| App trắng trơn, log có `CLEARTEXT` | xem mục bẫy ở phần 4 |
| App gọi API lỗi mạng | dùng `localhost` thay vì `10.0.2.2` |
| Đăng nhập được nhưng mọi trang trả 403 | đăng nhập bằng tài khoản sai vai trò — xem `SEED.md` |
