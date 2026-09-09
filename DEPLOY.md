# Đưa backend + cơ sở dữ liệu lên VPS

> **Không có VPS, không muốn tốn tiền?** Đọc [`DEPLOY-RENDER.md`](DEPLOY-RENDER.md)
> thay cho file này: Render + Neon + Upstash, miễn phí vĩnh viễn, không cần thẻ,
> và có sẵn tên miền kèm HTTPS nên bỏ qua được toàn bộ phần nginx và Let's Encrypt.

Backend, PostgreSQL và Redis chạy chung trên một VPS bằng Docker Compose. nginx
đứng trước làm nhiệm vụ kết thúc TLS. Frontend nằm ở Vercel (xem `DEPLOY.md`
của repo frontend).

```
Trình duyệt ──https──▶ Vercel (FE)
     │
     └────https──▶ VPS :443 ──▶ nginx ──▶ backend:8080 ──▶ postgres:5432
                                                        └▶ redis:6379
```

Postgres và Redis **không** mở ra internet. Backend cũng không. Đường duy nhất
đi vào là cổng 80/443 của nginx.

---

## Cần chuẩn bị trước

| Thứ | Ghi chú |
|---|---|
| VPS Ubuntu 22.04 trở lên | RAM tối thiểu 2 GB. Bản build Maven ngốn ~1,5 GB; máy 1 GB sẽ bị hệ điều hành giết giữa chừng. |
| Một tên miền trỏ về IP của VPS | **Bắt buộc**, không phải tuỳ chọn — đọc phần "Vì sao bắt buộc HTTPS" bên dưới. |
| Cổng 80 và 443 mở | Certbot cần cổng 80 để xin chứng chỉ lần đầu. |

### Vì sao bắt buộc phải có tên miền và HTTPS

Vercel phục vụ frontend qua `https://`. Trình duyệt **cấm** một trang https gọi
API qua `http://` (mixed content). Nếu backend chỉ có IP trần và chạy http thì
trang vẫn hiện ra, nhìn như bình thường, nhưng mọi thao tác đăng nhập, đặt lịch,
trải bài đều chết lặng lẽ trong console. Chứng chỉ Let's Encrypt cũng chỉ cấp
cho tên miền, không cấp cho địa chỉ IP.

Tên miền `.io.vn` hoặc `.id.vn` giá vài chục nghìn một năm là đủ.

---

## Các bước

### 1. Trỏ tên miền về VPS

Ở trang quản lý tên miền, tạo bản ghi A:

```
Loại: A    Tên: api    Giá trị: <IP của VPS>    TTL: 300
```

Chờ vài phút rồi kiểm tra từ máy mình — **làm bước này trước, đừng bỏ qua**.
Certbot chạy khi tên miền chưa trỏ đúng sẽ thất bại và Let's Encrypt tính vào
hạn mức 5 lần thất bại mỗi giờ:

```bash
dig +short api.ten-mien-cua-ban.com
```

Kết quả phải đúng bằng IP của VPS.

### 2. Cài Docker trên VPS

```bash
ssh root@<IP-VPS>
curl -fsSL https://get.docker.com | sh
```

### 3. Lấy mã nguồn

```bash
git clone <URL-repo-backend> /opt/astra-tarot
cd /opt/astra-tarot
git checkout feat/dat-branch
```

### 4. Điền biến môi trường

```bash
cp .env.prod.example .env
nano .env
chmod 600 .env
```

Sinh khoá bí mật ngay trên VPS:

```bash
echo "DB_PASSWORD=$(openssl rand -base64 24)"
echo "REDIS_PASSWORD=$(openssl rand -base64 24)"
echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n')"
echo "ASTRO_ENCRYPTION_KEY=$(openssl rand -base64 32)"
```

`ASTRO_ENCRYPTION_KEY` mã hoá ngày sinh và nơi sinh trong cơ sở dữ liệu. Sinh
một lần rồi **cất bản sao ở nơi khác ngoài VPS**. Đổi khoá sau khi đã có dữ
liệu nghĩa là không giải mã lại được nữa.

`FRONTEND_URL` và `CORS_ALLOWED_ORIGINS` để tạm domain `.vercel.app` mặc định;
sau khi frontend lên xong thì quay lại sửa cho khớp rồi khởi động lại backend.

### 5. Điền tên miền vào cấu hình nginx

`deploy/nginx.conf` đang để chỗ trống `api.ten-mien-cua-ban.com` ở **bốn** chỗ:

```bash
sed -i 's/api\.ten-mien-cua-ban\.com/api.ten-mien-that.com/g' deploy/nginx.conf
grep -c 'api.ten-mien-that.com' deploy/nginx.conf   # phải in ra 4
```

### 6. Dựng cơ sở dữ liệu và backend

nginx chưa chạy được ở bước này vì cấu hình của nó trỏ tới chứng chỉ chưa tồn
tại — nginx sẽ không khởi động nổi. Dựng ba dịch vụ kia trước:

```bash
docker compose -f docker-compose.prod.yml up -d --build postgres redis backend
```

Lần đầu mất khoảng 3–5 phút vì phải tải phụ thuộc Maven. Flyway tự chạy toàn bộ
migration khi backend khởi động, không cần tạo bảng bằng tay.

Kiểm tra:

```bash
docker compose -f docker-compose.prod.yml ps        # cả ba phải "healthy"
docker compose -f docker-compose.prod.yml logs -f backend
```

Dòng cần thấy trong log: `Started AstraTarotApplication`.

### 7. Xin chứng chỉ TLS

Cần một nginx chỉ nghe cổng 80 để certbot đặt file thử thách. Dựng tạm:

```bash
docker run -d --name nginx-tam -p 80:80 \
  -v /opt/astra-tarot/certbot-www:/usr/share/nginx/html \
  nginx:1.27-alpine

docker run --rm \
  -v astra-tarot_certbot_conf:/etc/letsencrypt \
  -v /opt/astra-tarot/certbot-www:/var/www/certbot \
  certbot/certbot certonly --webroot -w /var/www/certbot \
  -d api.ten-mien-that.com \
  --email email-cua-ban@gmail.com --agree-tos --no-eff-email

docker rm -f nginx-tam
```

Tên volume `astra-tarot_certbot_conf` lấy theo tên thư mục dự án. Kiểm tra tên
thật bằng `docker volume ls | grep certbot`.

Muốn thử trước mà không sợ chạm hạn mức của Let's Encrypt thì thêm
`--dry-run` vào lệnh certbot.

### 8. Bật nginx và certbot

```bash
docker compose -f docker-compose.prod.yml up -d
curl https://api.ten-mien-that.com/actuator/health
```

Kết quả mong đợi: `{"status":"UP"}`.

Dịch vụ `certbot` trong compose tự thức dậy mỗi 12 tiếng để gia hạn — chứng chỉ
Let's Encrypt sống 90 ngày. Sau khi gia hạn, nginx cần nạp lại chứng chỉ mới:

```bash
# Thêm vào crontab của VPS:  crontab -e
0 3 * * 1 docker exec astra-tarot-nginx nginx -s reload
```

### 9. Tạo tài khoản quản trị đầu tiên

Đăng ký một tài khoản bình thường trên trang web, rồi nâng quyền bằng SQL:

```bash
docker exec -it astra-tarot-postgres \
  psql -U postgres -d astra-tarot \
  -c "UPDATE users SET role='ADMIN' WHERE email='email-cua-ban@gmail.com';"
```

---

## Cập nhật mã nguồn về sau

```bash
cd /opt/astra-tarot
git pull
docker compose -f docker-compose.prod.yml up -d --build backend
```

Chỉ backend dựng lại; Postgres và Redis giữ nguyên nên dữ liệu không mất.

---

## Sao lưu cơ sở dữ liệu

Dữ liệu nằm trong volume `postgres_data`. Volume sống sót qua `up -d --build`,
nhưng **`docker compose down -v` xoá sạch**. Sao lưu định kỳ:

```bash
docker exec astra-tarot-postgres \
  pg_dump -U postgres astra-tarot | gzip > backup-$(date +%F).sql.gz
```

Đặt vào crontab và chép bản sao ra khỏi VPS. Bản sao lưu nằm cùng máy với dữ
liệu gốc không cứu được gì khi ổ đĩa hỏng hoặc VPS bị xoá.

Phục hồi:

```bash
gunzip -c backup-2026-09-09.sql.gz | \
  docker exec -i astra-tarot-postgres psql -U postgres -d astra-tarot
```

---

## Nối vào cơ sở dữ liệu từ máy mình

Cổng 5433 chỉ nghe trên loopback của VPS, không mở ra internet. Dùng đường hầm
SSH thay vì mở cổng:

```bash
ssh -L 5433:localhost:5433 root@<IP-VPS>
```

Rồi trỏ pgAdmin/DBeaver vào `localhost:5433`.

---

## Khi có sự cố

**Backend `unhealthy`, log không có `Started AstraTarotApplication`**

```bash
docker compose -f docker-compose.prod.yml logs backend | tail -50
```

- `Could not resolve placeholder` → thiếu một biến trong `.env`.
- `NOAUTH Authentication required` → thiếu `REDIS_PASSWORD`, hoặc profile không
  phải `prod` nên `application-prod.properties` không được nạp.
- `Validation failed ... migration` → Flyway đụng độ. Xem bảng
  `flyway_schema_history`.

**Trang FE lên nhưng đăng nhập không được, console báo lỗi CORS**

`CORS_ALLOWED_ORIGINS` chưa khớp domain thật của Vercel. Sửa `.env` rồi:

```bash
docker compose -f docker-compose.prod.yml up -d backend
```

Chú ý: viết đúng scheme và không có dấu `/` ở cuối — `https://abc.vercel.app`,
không phải `https://abc.vercel.app/`.

**Console báo "Mixed Content" hoặc "blocked"**

Frontend vẫn đang trỏ tới `http://`. Sửa `VITE_API_BASE_URL` trên Vercel thành
`https://api.ten-mien-that.com` rồi **Redeploy** — biến này nhúng lúc build, chỉ
refresh trang không ăn thua.

**Mail xác minh có link trỏ về localhost**

`FRONTEND_URL` trong `.env` chưa đặt. Sửa rồi khởi động lại backend.
