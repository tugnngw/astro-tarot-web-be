# Cloudflare Edge / DDoS / Load Balancing — AstroTarot

## Hiện trạng (EXE201 / free tier)

| Thành phần | Trạng thái |
|---|---|
| FE | `https://astro-tarot-web-fe.vercel.app` (không gắn CF được — hostname thuộc Vercel) |
| BE origin | `https://astra-tarot-api.onrender.com` (không gắn CF được — hostname thuộc Render) |
| Edge gateway | **Live:** `https://astrotarot-edge.megalit2578.workers.dev` (proxy + rate limit + chặn scanner) |
| Zone / DNS riêng | **Chưa có** — tài khoản CF hiện không có zone nào |
| Cloudflare Load Balancing | **Paid** — cần ≥2 origin + custom hostname trên CF |

Không thể “orange-cloud” `*.vercel.app` hay `*.onrender.com`. Muốn CDN + DDoS unmetered + WAF đầy đủ → **mua domain riêng** rồi trỏ nameserver về Cloudflare.

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
https://astrotarot-edge.megalit2578.workers.dev
```

### Trỏ FE sang Edge

Trên Vercel đặt:

```text
VITE_API_BASE_URL=https://astrotarot-edge.megalit2578.workers.dev
```

Rồi **Redeploy** FE (biến này được nhúng lúc build).

Giữ CORS origin FE trên BE (`CORS_ALLOWED_ORIGINS`) như cũ — Worker forward `Origin` tới Spring.

### Kiểm tra nhanh

```bash
curl.exe -s https://astrotarot-edge.megalit2578.workers.dev/__edge/health
curl.exe -s https://astrotarot-edge.megalit2578.workers.dev/api/v1/readers
```

---

## Khi có domain riêng (bước tiếp theo)

Giả sử domain `astrotarot.vn`:

### 1. DNS (proxied = orange cloud)

| Type | Name | Target | Proxy |
|---|---|---|---|
| CNAME | `api` | `astra-tarot-api.onrender.com` **hoặc** Worker custom domain | ✅ Proxied |
| CNAME | `@` / `www` | `cname.vercel-dns.com` (Vercel) | ✅ Proxied (hoặc chỉ DNS nếu dùng Vercel SSL riêng) |

Khuyến nghị EXE201:

- `api.astrotarot.vn` → **Worker route** `astrotarot-edge` (giữ rate limit), Worker vẫn proxy Render  
  **hoặc** CNAME thẳng Render (mất rate-limit Worker trừ khi gắn Worker route)
- `www` / apex → Vercel theo docs Vercel + Cloudflare

SSL/TLS mode: **Full (strict)** sau khi origin có cert hợp lệ.

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
3. LB hostname: `api.astrotarot.vn` → pool  
4. Steering: Off (failover) hoặc Dynamic

Với **1 origin Render free**, LB **không mang lại failover thật** — chỉ tốn tiền. Edge Worker + KeepAwake đủ cho demo EXE201.

---

## Kiến trúc khuyến nghị theo giai đoạn

```text
[Browser]
    │
    ▼
[Vercel FE]  ──API──►  [CF Worker Edge]  ──►  [Render Spring Boot]
                            │
                     rate limit / block
                     DDoS (workers.dev /
                     hoặc zone khi có domain)
```

Sau khi có domain + budget:

```text
[Browser] → [CF Zone: WAF + DDoS]
                ├─ www → Vercel
                └─ api → (Worker hoặc LB pool) → origin(s)
```

---

## Checklist vận hành

- [x] Deploy Worker `astrotarot-edge` → `https://astrotarot-edge.megalit2578.workers.dev`
- [ ] Đổi `VITE_API_BASE_URL` → URL Worker, redeploy FE
- [ ] Xác nhận login, PayOS webhook, WebSocket chat qua Edge
- [ ] (Tuỳ chọn) Mua domain → add zone CF → `api` + `www`
- [ ] SSL Full (strict) + Bot Fight Mode
- [ ] Chỉ mua Load Balancing khi có origin thứ 2

---

## Giới hạn cần biết

- Worker vẫn phụ thuộc cold start Render — Edge **không** thay KeepAwake / warm banner.
- Rate limit Workers là per-colo (mỗi PoP); đủ cho EXE201, không phải quota global cứng.
- `workers.dev` đã nằm sau mạng CF (có lớp chống abuse), nhưng branding/SEO nên dùng domain riêng.
