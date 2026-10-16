# Bật thanh toán Google Play cho app AstroTarot

Mã đã xong cả hai đầu (máy chủ và app). Những việc dưới đây **chỉ chủ tài
khoản làm được**: khai sản phẩm, đặt giá, tạo khoá. Làm hết thì nút "Mua qua
Google Play" trong app mới hiện giá và bấm được — chưa làm thì app tự khoá nút
lại và ghi "Chưa mở bán trong ứng dụng".

## Vì sao phải dùng thanh toán của Google

Chính sách Thanh toán của Google xếp **gói nội dung số dùng trong ứng dụng**
vào diện bắt buộc dùng hệ thống thanh toán của Play. Việt Nam **không** nằm
trong chương trình thanh toán thay thế nào (user choice billing / alternative
billing chỉ mở ở 30 nước EEA, Anh, Mỹ, Nhật, Úc, Brazil, Indonesia, Nam Phi,
Hàn Quốc, Ấn Độ — kiểm lại 14/10/2026), nên không có cửa nào khác.

Bán gói AI bằng ví trong app Android là vi phạm, và hậu quả thường là bị gỡ
app chứ không phải nhắc nhở.

**Buổi xem với Reader thì khác.** Đó là dịch vụ một-kèm-một giữa hai cá nhân,
Google miễn trừ khoản này, nên đặt lịch với Reader vẫn trả qua PayOS và ví như
cũ — cả trên app. Nói cách khác, sau thay đổi này ví trên Android chỉ còn dùng
để trả buổi xem.

## Việc 1 — Khai 5 sản phẩm trong Play Console

Play Console → **Kiếm tiền** → **Sản phẩm** → **Sản phẩm trong ứng dụng** →
**Tạo sản phẩm**.

Mã sản phẩm phải khớp **từng chữ**, vì máy chủ tra gói theo đúng mã này:

| Mã sản phẩm (product ID) | Tên hiện cho khách | Giá đặt trong Play Console |
| --- | --- | --- |
| `ai_basic_1d` | Gói Basic 1 ngày | 19.000 ₫ |
| `ai_combo_3d` | Gói Combo 3 ngày | 49.000 ₫ |
| `ai_vip_7d` | Gói VIP 7 ngày | 95.000 ₫ |
| `ai_month_basic` | Gói Cơ bản tháng | 119.000 ₫ |
| `ai_month_pro` | Gói Pro tháng | 239.000 ₫ |

Mỗi sản phẩm nhớ bấm **Kích hoạt** (Activate) — tạo xong mà để trạng thái nháp
thì app hỏi giá vẫn không thấy gì.

### Vì sao giá trên app cao hơn giá web

Google thu hoa hồng mỗi lượt mua: **15%** cho 1 triệu USD doanh thu đầu tiên
mỗi năm (tài khoản này đã ghi danh mức ấy — xem Việc 4), **30%** cho phần vượt. Bán đúng giá web thì thực nhận còn thấp hơn bán trên web.

Giá trên bảng đã kê lên để sau hoa hồng 15% vẫn nhận về nhiều hơn giá web một
chút:

| Gói | Giá web | Giá app | Tăng | Thực nhận sau 15% |
| --- | --- | --- | --- | --- |
| Basic 1 ngày | 15.000 | 19.000 | 27% | 16.150 |
| Combo 3 ngày | 39.000 | 49.000 | 26% | 41.650 |
| VIP 7 ngày | 79.000 | 95.000 | 20% | 80.750 |
| Cơ bản tháng | 99.000 | 119.000 | 20% | 101.150 |
| Pro tháng | 199.000 | 239.000 | 20% | 203.150 |

**Đổi giá bên Play Console thì phải sửa cả cột `subscription_plan.google_play_price`
trong cơ sở dữ liệu.** App hiện giá do Google trả về, nên khách luôn thấy đúng
giá; nhưng thống kê doanh thu cộng theo cột ấy, lệch đi là báo cáo sai tiền
thật. Câu lệnh sửa:

```sql
UPDATE subscription_plan SET google_play_price = 25000
WHERE google_play_product_id = 'ai_basic_1d';
```

## Việc 2 — Tài khoản dịch vụ (ĐÃ TẠO XONG 16/10/2026)

Máy chủ phải tự hỏi Google "lượt mua này có thật không, đã trả tiền chưa" —
không được tin app. Phần này đã dựng sẵn, ghi lại để sau này còn biết nó nằm ở
đâu:

| | |
| --- | --- |
| Project Google Cloud | `astrotarot-play` (AstroTarot Play) |
| Tài khoản dịch vụ | `astrotarot-play-billing@astrotarot-play.iam.gserviceaccount.com` |
| API đã bật | Google Play Android Developer API |
| Quyền trong Play Console | Xem dữ liệu tài chính + Quản lý đơn hàng và đăng ký |
| Tệp khoá | `astrotarot-play-c39ef10aa29f.json` (tải về máy, KHÔNG commit) |

Khoá này tách riêng khỏi khoá GA4 (`astro-tarot-ga4-reader@astra-tarot-analytics`)
có chủ đích: hai việc khác nhau, lộ cái nào thì chỉ mất cái ấy. Khoá GA4 còn
thuộc tài khoản Google của người khác trong nhóm, nên càng không dùng chung.

Cần tạo lại (khoá lộ, hoặc mất tệp) thì: Cloud Console → project `astrotarot-play`
→ IAM & Admin → Service accounts → `astrotarot-play-billing` → Keys → Add key →
Create new key → JSON. Khoá cũ xoá ở cùng trang ấy.

## Việc 3 — Đặt khoá lên Render

Render → service của backend → **Environment** → thêm biến:

- **Tên:** `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
- **Giá trị:** dán **toàn bộ nội dung** tệp `astrotarot-play-c39ef10aa29f.json`,
  một dòng cũng được, nhiều dòng cũng được.

Lưu biến là Render tự dựng lại dịch vụ, không phải bấm gì thêm. (Đẩy
`main` cũng tự deploy: workflow `cd.yml` gọi Deploy Hook của Render.)

Để trống biến này thì tính năng tự tắt chứ không làm sập máy chủ: mọi thứ khác
chạy bình thường, chỉ riêng việc đổi lượt mua trả lỗi "Máy chủ chưa cấu hình
khoá Google Play".

## Việc 4 — Hai thứ nên làm luôn

- **Mức phí 15%**: ĐÃ ghi danh (kiểm 16/10/2026 ở Play Console → Associated
  developer accounts → "Programs and services you're enrolled in" → ✓ 15%
  service fee). Không phải làm gì thêm.
- **Hồ sơ thanh toán (merchant profile)**: chưa có thì Play Console không cho
  tạo sản phẩm trong ứng dụng. Play Console → **Kiếm tiền** → **Thiết lập hồ sơ
  thanh toán**, điền thông tin ngân hàng và thuế.

## Việc 5 — Thử trước khi mở cho khách

1. Play Console → **Kiểm thử** → **Kiểm thử bằng cấp phép** (License testing):
   thêm email Google của máy sẽ thử. Email trong danh sách này mua **không bị
   trừ tiền thật**.
2. Tải app từ kênh thử nội bộ (internal testing) — **không** chạy bản `flutter
   run` để thử mua: Play Billing chỉ làm việc với bản dựng đã ký và đã tải qua
   Play.
3. Mở màn **Gói AI**, kiểm ba điều:
   - giá hiện ra là giá Play Console (19.000 ₫…), không phải giá web;
   - mua xong gói vào ngay, màn Hạn mức hôm nay đổi theo;
   - mua lại gói đang dùng thì nút khoá và ghi "Bạn đang dùng gói này rồi."

## Những chỗ đã lường trước, để khỏi tìm lại

- **Mạng rớt giữa lúc mua.** App chỉ báo "đã nhận hàng" với Google **sau khi**
  máy chủ cấp gói xong. Hỏng giữa đường thì lần mở app sau Google phát lại lượt
  mua ấy và app đổi lại — khách không mất tiền.
- **Gửi lại cùng một lượt mua.** Máy chủ lấy `purchaseToken` làm khoá chính,
  nên một lượt mua chỉ đổi được đúng một gói, gửi lại bao nhiêu lần cũng vậy.
- **Đơn đang chờ thanh toán** (chuyển khoản, thẻ cào): Google giữ ở trạng thái
  PENDING, máy chủ **không** cấp gói, app nói "khi nào tiền về, mở lại app là
  gói được cấp".
- **Đơn không được xác nhận trong 3 ngày** thì Google tự hoàn tiền. Máy chủ gọi
  `acknowledge` ngay sau khi cấp gói; nếu bước ấy hỏng thì **không** làm hỏng
  lượt mua của khách, nhưng ghi log mức ERROR với chữ "KHÔNG acknowledge được"
  — thấy dòng ấy trong log Render là phải xử lý tay trong vòng 3 ngày.
- **Máy không có Google Play** (một số máy Trung Quốc): app ghi "Chưa mở bán
  trong ứng dụng" và khoá nút, chứ không lặng lẽ quay về mua bằng ví.

## Chưa làm, và vì sao

- **Gói tự gia hạn (subscription).** Đang bán kiểu sản phẩm một lần, vì mô hình
  hiện tại vốn là mua N ngày rồi hết hạn. Chuyển sang tự gia hạn phải dựng thêm
  thông báo thời gian thực qua Pub/Sub, xử lý gia hạn, ân hạn, huỷ giữa kỳ — một
  hệ thống khác hẳn.
- **Hoàn tiền từ phía Google** (Voided Purchases API): khách được Google hoàn
  tiền thì gói vẫn còn hiệu lực tới hết hạn. Với gói 1–7 ngày thì thiệt hại nhỏ;
  nếu về sau bán gói dài hơn thì nên đọc API ấy và thu hồi gói.
- **iOS.** Chưa có app iOS. Khi nào có thì Apple cũng có luật tương tự và phải
  làm thêm StoreKit.
