# Bật số liệu Google Analytics 4 cho trang admin

Mã đã xong cả hai đầu: web đã gắn thẻ GA4 (thu thập), máy chủ đã có
`/api/v1/admin/analytics/report` (đọc về). Việc còn lại chỉ là **cấp khoá cho
máy chủ** — chưa làm thì tab Analytics trong trang admin vẫn mở được nhưng báo
"chưa cấu hình".

Hai việc khác nhau, đừng lẫn:

| | Thu thập | Đọc về |
|---|---|---|
| Ai làm | Trình duyệt của khách | Máy chủ |
| Cần gì | Measurement ID `G-6B0LQ5Q9D8` | Service account + Property ID |
| Đặt ở đâu | Đã nằm trong mã web | **Render** |
| Thiếu thì sao | GA4 không có dữ liệu | Có dữ liệu nhưng admin không xem được trong app |

## Việc 1 — Đặt hai biến trên Render

Render → service backend → **Environment**:

| Tên biến | Giá trị |
| --- | --- |
| `GA4_PROPERTY_ID` | `559041870` |
| `GA4_CREDENTIALS_JSON` | **Toàn bộ nội dung** tệp service account JSON |

Lưu biến là Render tự dựng lại dịch vụ.

**Dán nội dung tệp, KHÔNG dán đường dẫn.** Biến `GOOGLE_APPLICATION_CREDENTIALS`
quen dùng ở máy cá nhân nhận một đường dẫn tệp; trên Render không có tệp nào ở
đó cả. Mã đọc thẳng chuỗi JSON từ `GA4_CREDENTIALS_JSON` (xem
`DefaultGa4ClientProvider`). Máy cá nhân thì vẫn dùng `GA4_CREDENTIALS_PATH`
trỏ tới tệp được.

Để trống cả hai thì ứng dụng vẫn khởi động bình thường, chỉ riêng tab Analytics
báo chưa cấu hình — không làm sập gì.

## Việc 2 — Hai bước bên Google, thiếu là 403

1. **GA4 → Admin → Property Access Management** → thêm
   `astro-tarot-ga4-reader@astra-tarot-analytics.iam.gserviceaccount.com`
   với vai trò **Viewer**.
2. **Google Cloud Console** (project `astra-tarot-analytics`) → APIs & Services
   → Library → bật **Google Analytics Data API**.

Thiếu bước nào thì tab Analytics hiện câu lỗi "Không thể truy vấn Google
Analytics Data API: …PERMISSION_DENIED".

## Việc 3 — Kiểm hai mã có cùng một property không

Đây là chỗ dễ mất cả buổi mà không ai nghĩ ra: thẻ trên web gửi dữ liệu vào
**luồng** `G-6B0LQ5Q9D8`, còn máy chủ đọc **property** `559041870`. Nếu luồng ấy
nằm trong một property khác thì web vẫn ghi nhận bình thường, máy chủ vẫn trả
200, mà mọi con số đều bằng 0.

Kiểm: GA4 → Admin → **Data streams** → mở luồng có `G-6B0LQ5Q9D8` → xem nó thuộc
property nào → Admin → **Property details** của property đó phải hiện đúng số
`559041870`. Lệch thì sửa `GA4_PROPERTY_ID` cho khớp.

## Việc 4 — Gắn UTM vào mọi link đăng lên mạng xã hội

Mục đích của phần này là biết khách đến từ TikTok hay Facebook nhiều hơn. Nhưng
**trình duyệt trong ứng dụng TikTok và Facebook thường cắt referrer**, nên nếu
chỉ dán link trần thì GA4 gom phần lớn vào `(direct)` và bảng nguồn truy cập
gần như vô dụng.

Mỗi chỗ đăng dùng một link riêng:

```
https://astrotarot.date/?utm_source=tiktok&utm_medium=social&utm_campaign=ra-mat
https://astrotarot.date/?utm_source=facebook&utm_medium=social&utm_campaign=ra-mat
```

Mã web đã giữ nguyên tham số `utm_*` khi gửi lên GA4 (những tham số nhạy cảm
như token, email thì bị loại), và `src/lib/utm.ts` còn nhớ lại nguồn ấy cho
những bước sau như đăng ký hay góp ý.

## Kiểm lại sau khi làm xong

1. Mở https://astrotarot.date bằng cửa sổ ẩn danh, đợi vài giây, mở DevTools →
   Application → Cookies: phải thấy `_ga` và `_ga_6B0LQ5Q9D8`. **Không thấy là
   thẻ chưa chạy** — đó chính là lỗi đã sửa ở PR "Sửa GA4 không gửi được lượt
   nào".
2. GA4 → Reports → **Realtime**: phải thấy 1 người đang hoạt động.
3. Trang admin → tab Analytics: bảng có số, không còn câu "chưa cấu hình".

Dữ liệu trong báo cáo thường (không phải Realtime) trễ **24–48 giờ** sau lần
đầu bật, nên hôm nay bảng còn trống là bình thường.
