# Cloudflare Edge / DDoS / Load Balancing — AstroTarot

## Hiện trạng (EXE201 / free tier)

Cập nhật **18/09/2026** — đã có domain riêng, nên phần "khi có domain" ở cuối file này đã làm xong một nửa.

| Thành phần | Trạng thái |
|---|---|
| Domain | `astrotarot.date` — Cloudflare Registrar, hết hạn 18/09/2027, **auto-renew đã tắt** (chủ ý: chỉ đăng ký 1 năm) |
| Zone / DNS | **Có** — NS `martin.ns.cloudflare.com` / `tara.ns.cloudflare.com` |
| FE | `https://astrotarot.date` (Vercel). `www` → 308 về apex. Cả hai record để **DNS only** (grey cloud) vì Vercel tự cấp cert Let's Encrypt |
| BE origin | `https://astra-tarot-api.onrender.com` (vẫn không orange-cloud được — hostname thuộc Render) |
| Edge gateway | **Live:** `https://api.astrotarot.date` — Worker Custom Domain, **proxied** qua zone. `astrotarot-edge.megalit2578.workers.dev` vẫn bật, giữ làm đường dự phòng |
| Cloudflare Load Balancing | **Paid**, chưa mua — và với đúng 1 origin Render thì mua cũng không có failover thật |

Đường đi của một request API hiện tại:

```text
Browser → api.astrotarot.date (CF zone, proxied) → Worker astrotarot-edge → Render
```

Vẫn không thể orange-cloud `*.vercel.app` hay `*.onrender.com` — đó là lý do FE để grey cloud còn API thì đi qua Worker.

---

## Edge Worker (đã ship trong repo)

Thư mục: `cloudflare-edge/`

Chức năng:

- Reverse-proxy tới `ORIGIN_API` (mặc định Render)
- Rate limit theo IP: **120 req/phút** API, **30 req/phút** `/auth` + `/oauth2`
- Bỏ qua rate limit webhook PayOS (tránh làm hỏng thanh toán)
- Chặn path scanner (`/wp-admin`, `/.env`, `/.git`, …)
- Passthrough WebSocket (`Upgrade: websocket` → `/ws`)
- Security headers + `Cache-Control: private, no-store`
- Health: `GET /__edge/health` (không đụng origin)

### Deploy

```bash
cd cloudflare-edge
npm install
npx wrangler login   # lần đầu
npm run deploy
```

URL production hiện tại:

```text
https://api.astrotarot.date                        ← dùng cái này
https://astrotarot-edge.megalit2578.workers.dev    ← vẫn sống, để dự phòng
```

`api.astrotarot.date` được gắn bằng **Workers → astrotarot-edge → Domains → Add Domain**, không phải bằng cách tự tạo DNS record. Cloudflare tự tạo record và tự cấp cert; đừng thêm CNAME `api` bằng tay, sẽ chọi nhau.

### Trỏ FE sang Edge

Trên Vercel đặt:

```text
VITE_API_BASE_URL=https://api.astrotarot.date
```

Rồi **Redeploy** FE — biến này được nhúng lúc build, sửa biến mà không build lại thì bundle cũ vẫn gọi URL cũ.

`CORS_ALLOWED_ORIGINS` trên BE phải chứa **origin của trình duyệt**, không phải URL của Worker. Worker forward nguyên `Origin` sang Spring, nên giá trị đúng là domain FE:

```text
CORS_ALLOWED_ORIGINS=https://astrotarot.date,https://www.astrotarot.date,https://astro-tarot-web-fe.vercel.app
FRONTEND_URL=https://astrotarot.date
```

Quên bước này thì Spring trả **403 text/plain** cho mọi request từ domain mới, và giao diện trông y như server sập (danh sách rỗng + banner "đang khởi động" chạy mãi) dù `/ping` gọi bằng curl vẫn 200. Triệu chứng đánh lừa, nhớ để khỏi mất thời gian đi tìm nhầm chỗ.

### Kiểm tra nhanh

```bash
curl.exe -s https://api.astrotarot.date/__edge/health
curl.exe -s https://api.astrotarot.date/api/v1/readers

# CORS: phải 200, KHÔNG phải 403
curl.exe -s -o NUL -w "%{http_code}" https://api.astrotarot.date/ping -H "Origin: https://astrotarot.date"
```

---

## Khi có domain riêng — **đã làm, 18/09/2026**

Domain thật: `astrotarot.date`.

### 1. DNS

| Type | Name | Target | Proxy | Trạng thái |
|---|---|---|---|---|
| CNAME | `@` | `5c87ea28bf49d3b5.vercel-dns-017.com` | ❌ DNS only | ✅ xong |
| CNAME | `www` | `5c87ea28bf49d3b5.vercel-dns-017.com` | ❌ DNS only | ✅ xong |
| — | `api` | Worker Custom Domain (CF tự quản) | ✅ Proxied | ✅ xong |

Vì sao FE để **DNS only** chứ không orange-cloud: Vercel tự cấp và tự gia hạn cert cho `astrotarot.date`. Bật proxy vào sẽ thành hai lớp CDN chồng nhau và dễ vòng lặp cert. Muốn WAF/cache của Cloudflare cho FE thì phải chuyển hẳn sang Cloudflare Pages, không phải bật nút.

DDoS + WAF của zone hiện **chỉ áp cho `api.astrotarot.date`**, vì đó là hostname duy nhất đang proxied.

SSL/TLS mode: giữ mặc định. Render đã có cert hợp lệ nên chuyển sang **Full (strict)** được, nhưng vì FE grey-cloud nên setting này chỉ ảnh hưởng nhánh `api`.

### 2. DDoS (tự động khi Proxied)

HTTP DDoS mitigation của Cloudflare **bật sẵn** khi traffic đi qua proxy (orange cloud). Không cần “bật nút DDoS” riêng trên Free.

Bot Fight Mode (Free): Security → Bots → bật **Bot Fight Mode**.

### 3. WAF / Rate Limiting (zone)

Free:

- Security → WAF → managed rules cơ bản / custom rule đơn giản  
- VD: Block nếu URI chứa `.env` / `wp-admin` (Worker đã chặn, zone rule là lớp 2)

Pro+: Rate Limiting rules theo path, OWASP managed ruleset đầy đủ hơn.

### 4. Load Balancing thật

Cloudflare Load Balancing **không nằm trong Free**. Cần khi:

- Có **≥2 origin** khỏe (vd Render + Railway, hoặc 2 region)
- Muốn health check + failover / geo steering

Cấu hình khái niệm:

1. Create Pool `astrotarot-api` — origins: primary Render, standby thứ 2  
2. Health check: `GET /ping` kỳ vọng 200  
3. LB hostname: `api.astrotarot.date` → pool  
4. Steering: Off (failover) hoặc Dynamic

Với **1 origin Render free**, LB **không mang lại failover thật** — chỉ tốn tiền. Edge Worker + KeepAwake đủ cho demo EXE201.

---

## Kiến trúc hiện tại

```text
                    astrotarot.date / www          api.astrotarot.date
[Browser] ──────────────┬──────────────────────────────────┬──────────────
                        │ DNS only (grey)                  │ Proxied (orange)
                        ▼                                  ▼
                   [Vercel FE]                      [CF Zone: DDoS + WAF]
                                                            │
                                                            ▼
                                                 [Worker astrotarot-edge]
                                                   rate limit / chặn scanner
                                                            │
                                                            ▼
                                                 [Render Spring Boot]
```

Bước còn thiếu để "đủ bộ" (chỉ làm khi có nhu cầu thật):

```text
[CF Zone] ─ api → [LB pool] ─┬─ Render   (primary)
                             └─ origin 2 (standby)   ← cần Load Balancing trả phí
```

---

## Checklist vận hành

- [x] Deploy Worker `astrotarot-edge`
- [x] Mua domain → add zone CF → `@`, `www` (Vercel) + `api` (Worker Custom Domain)
- [x] Tắt auto-renew domain (chủ ý chỉ 1 năm — nhớ set nhắc trước 18/09/2027 nếu muốn giữ)
- [x] Đổi `VITE_API_BASE_URL` → `https://api.astrotarot.date`, redeploy FE
- [ ] **Đổi `CORS_ALLOWED_ORIGINS` + `FRONTEND_URL` trên Render** — chưa làm, đang là thứ chặn cuối cùng
- [ ] Xác nhận login, PayOS webhook, WebSocket chat qua Edge domain mới
- [ ] Bot Fight Mode (Security → Bots) — chỉ có tác dụng cho `api`, vì FE grey-cloud
- [ ] Đổi `PAYOS_RETURN_URL` / `PAYOS_CANCEL_URL` nếu chúng đang trỏ `.vercel.app` (để trống thì tự lấy `FRONTEND_URL`, không cần đụng)
- [ ] Chỉ mua Load Balancing khi có origin thứ 2

---

## Giới hạn cần biết

- Worker vẫn phụ thuộc cold start Render — Edge **không** thay KeepAwake / warm banner.
- Rate limit Workers là per-colo (mỗi PoP); đủ cho EXE201, không phải quota global cứng.
- WAF / Bot Fight Mode / Rate Limiting rules của zone **không áp được cho FE**, vì `astrotarot.date` để DNS only. Chỉ nhánh `api` được bảo vệ ở tầng zone.
- `workers.dev` vẫn bật. Ai biết URL đó vẫn gọi API thẳng được, bỏ qua mọi rule của zone. Muốn đóng thì tắt toggle Production ở **Workers → astrotarot-edge → Domains**, nhưng làm vậy là mất luôn đường dự phòng khi zone có sự cố.
