# Biết khách đến từ Facebook rồi làm gì trên web

Trước đây web chỉ gửi lên GA4 **mỗi lần đổi trang**. Nên nhìn vào báo cáo chỉ
biết được "người từ Facebook đã mở /tarot rồi mở /readers" — mở trang nào.
Không biết họ **có làm gì không**: mở `/tarot` rồi đóng, với mở `/tarot` rồi
trải bài và nhắn mười câu, trông y hệt nhau.

Giờ mỗi việc đáng kể đều được ghi lại thành một sự kiện riêng.

## Sáu việc đang được ghi

| Tên sự kiện | Ghi khi nào |
| --- | --- |
| `dang_ky_tai_khoan` | máy chủ nhận đăng ký thành công |
| `bat_dau_chat_ai` | vào được màn chat Tarot AI (kèm `co_la_so`) |
| `rut_bai_tarot` | máy chủ trả bài về, không đếm lần hết hạn mức |
| `dat_lich_reader` | đặt lịch thành công |
| `gui_gop_y` | gửi phiếu góp ý (kèm `nguon_gop_y`, `sao`) |
| `cta_shopee_click` | bấm sang Shopee |

Tất cả đi qua đúng **một** hàm — `trackCta` trong `src/lib/track.ts` — và hàm
ấy ghi vào **hai** nơi cùng lúc. Giữ một chỗ gọi là có chủ đích: bắn tay ở
từng nơi thì sớm muộn cũng có nút chỉ ghi vào một bên, và lúc hai bảng ra số
khác nhau thì không ai biết bên nào đúng.

## Hai nơi nhận, trả lời hai câu hỏi khác nhau

| | Bảng của mình | Google Analytics 4 |
| --- | --- | --- |
| Xem ở đâu | Quản trị → **Lưu lượng & GA4** → *Khách đến từ đâu, rồi làm gì* | analytics.google.com |
| Có số sau bao lâu | ngay lập tức | Thời gian thực: vài phút. Báo cáo thường: **24–48 giờ** |
| Trình chặn quảng cáo | không cắt được, vì đi qua API của mình | cắt được, mất khoảng 10–30% lượt |
| Đếm người | được, nối với `user_id` | chỉ đếm thiết bị |
| Phễu, so sánh kênh, giữ lịch sử dài | phải tự làm | có sẵn |

Nói ngắn: cần biết **ngay bây giờ** thì xem bảng của mình; cần phân tích sâu
thì xem GA4.

## Ví dụ: một người vào từ Facebook

Giả sử bạn đăng lên fanpage đúng link có gắn UTM (xem `LINK-UTM.md`):

```
https://astrotarot.date/?utm_source=facebook&utm_medium=social&utm_campaign=page
```

Một bạn bấm vào, xem thử, rồi đăng ký và trải một quẻ. Chuyện xảy ra theo thứ
tự:

1. **Mở link.** `src/lib/utm.ts` đọc `utm_source=facebook` ngay lúc trang tải
   và nhớ lại trong trình duyệt. GA4 cũng ghi nhận phiên này có nguồn
   `facebook / social`.
2. **Bấm "Trải bài ngay" ở trang chủ** → `cta_tarot_draw`.
3. **Đăng ký tài khoản** → `dang_ky_tai_khoan`.
4. **Vào màn Tarot AI** → `bat_dau_chat_ai` kèm `co_la_so=false` nếu họ không
   khai ngày sinh.
5. **Trải bài xong** → `rut_bai_tarot`.
6. Họ đóng trang, không đặt lịch.

### Trong trang quản trị, ngay sau đó

Quản trị → **Lưu lượng & GA4** → kéo xuống bảng *Khách đến từ đâu, rồi làm
gì*:

| Nguồn | Lượt | Người | dang_ky_tai_khoan | bat_dau_chat_ai | rut_bai_tarot | dat_lich_reader |
| --- | --- | --- | --- | --- | --- | --- |
| facebook | 21 | 4 | 4 | 12 | 7 | — |
| tiktok | 9 | 3 | 1 | 5 | 3 | 1 |
| (không rõ nguồn) | 46 | 11 | 3 | 20 | 15 | 2 |

Đọc bảng này:

- **Facebook mang về 4 người, cả 4 đều đăng ký** — kênh này ra tài khoản tốt.
- **Nhưng không ai đặt lịch.** TikTok ít người hơn mà có 1 lượt đặt. Nếu muốn
  bán buổi xem với Reader thì TikTok đang đáng đăng hơn.
- **21 lượt nhưng chỉ 4 người.** Cột *người* có mặt đúng để tránh cái bẫy này:
  nhìn cột *lượt* rồi nói "Facebook mang về 21 khách" là sai gấp năm lần.
- **"(không rõ nguồn)" thường chiếm nhiều nhất.** Đó là người tự gõ địa chỉ,
  hoặc bấm từ link **chưa gắn UTM**. Nhóm này càng to thì bảng càng ít giá
  trị — dán đúng bộ link trong `LINK-UTM.md` là cách duy nhất làm nó nhỏ lại.

### Trong GA4, sau 24–48 giờ

analytics.google.com → tài sản `AstroTarot`:

1. **Báo cáo → Mức độ tương tác → Sự kiện**: thấy danh sách sáu tên sự kiện
   trên kèm số lượt.
2. **Khám phá → Tạo khám phá trống**, kéo chiều *Nguồn phiên* vào hàng, chiều
   *Tên sự kiện* vào cột. Ra đúng bảng chéo như trên, nhưng tính cả khách
   chưa đăng nhập.
3. **Thời gian thực**: vào ngay sau khi bấm thì thấy sự kiện hiện trong vài
   phút — dùng để kiểm xem đã ăn chưa, đừng dùng để đọc số.

Không phải gắn `utm_source` vào từng sự kiện. GA4 tự gắn mọi sự kiện trong một
phiên vào nguồn của phiên ấy; tự thêm vào là tạo một chiều **trùng** với thứ
GA4 đã có, và hai thứ ấy lệch nhau ngay khi khách quay lại qua kênh khác.

## Kiểm xem đã chạy chưa

1. Mở astrotarot.date bằng cửa sổ ẩn danh, bấm một nút có trong bảng trên.
2. DevTools → **Network**, lọc `collect` → phải thấy một request đi
   googletagmanager, trong đó có `en=<tên sự kiện>`.
3. Trang quản trị → Lưu lượng & GA4 → bảng *Khách đến từ đâu, rồi làm gì* phải
   tăng **ngay**, không cần đợi.

Bước 3 là bước đáng tin nhất: nó đọc thẳng cơ sở dữ liệu của mình. Nếu bước 3
có số mà bước 2 không thấy gì thì vấn đề nằm ở phía GA4 (thẻ chưa chạy, trình
chặn quảng cáo), không phải ở chỗ ghi nhận.

## Thêm một sự kiện mới

Gọi `trackCta` ngay sau khi việc đó **thành công**, không phải lúc bấm nút:

```ts
import { trackCta } from "@/lib/track";

await muaGoi(maGoi);
trackCta("mua_goi_ai", { ma_goi: maGoi });
```

Tên viết **thường, không dấu, nối bằng gạch dưới**. GA4 phân biệt hoa thường
(`Mua_Goi` và `mua_goi` thành hai sự kiện khác nhau) và cắt tên dài quá 40 ký
tự.

Đặt sau khi thành công là có lý do: đặt ở `onClick` thì mỗi lần người dùng bấm
hụt, hết hạn mức, hay mạng rớt đều được đếm như một lượt mua.
