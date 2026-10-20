# Khuyến mãi dịp lễ và lời chúc

Quản trị nhập một đợt khuyến mãi, đặt khoảng ngày, mức giảm, và một lời chúc.
Trong khoảng ngày ấy giá gói AI tự giảm, và mỗi người nhận lời chúc **đúng một
lần** khi mở app hoặc web.

## Nhập một đợt

Trang quản trị → tab **Khuyến mãi** → *Thêm đợt*.

| Ô | Nghĩa |
| --- | --- |
| Tên đợt | hiện trên banner, ví dụ "Tết Bính Ngọ 2026" |
| Lời chúc | để trống thì đợt này chỉ giảm giá, không chúc gì |
| Kiểu giảm | `PHAN_TRAM` hoặc `SO_TIEN` |
| Mức giảm | phần trăm (1–100) hoặc số đồng |
| Giảm tối đa | **chỉ cho phần trăm** — xem bên dưới |
| Bắt đầu / Kết thúc | mốc kết thúc KHÔNG tính vào |
| Đang bật | tắt gấp mà không phải sửa ngày |

### Luôn đặt "Giảm tối đa" khi dùng phần trăm

"Giảm 50%" trên gói Pro tháng 239.000 là **119.500** — con số không hiện ra
trước mắt lúc gõ số 50. Đặt trần 50.000 thì dù gõ bao nhiêu phần trăm, mỗi
lượt cũng chỉ bớt nhiều nhất 50.000.

Đây là loại sai sót mất tiền thật, và tiền đã trừ thì không đòi lại được.

### Mốc kết thúc không tính vào

Đặt `Kết thúc = 00:00 ngày 20/02` nghĩa là **hết ngày 19/02**. Muốn chạy trọn
ngày 20 thì đặt `00:00 ngày 21/02`.

## Vài dịp lễ Việt Nam để nhập sẵn

| Dịp | Ngày |
| --- | --- |
| Tết Dương lịch | 01/01 |
| Tết Nguyên đán | theo âm lịch, đổi mỗi năm |
| Giỗ tổ Hùng Vương | 10/3 âm lịch |
| Giải phóng miền Nam | 30/04 |
| Quốc tế Lao động | 01/05 |
| Quốc khánh | 02/09 |
| Trung thu | 15/8 âm lịch |
| Phụ nữ Việt Nam | 20/10 |
| Nhà giáo Việt Nam | 20/11 |
| Giáng sinh | 24–25/12 |

Những ngày theo âm lịch đổi mỗi năm, nên không gieo sẵn trong mã — tra lịch
rồi nhập tay.

## Hai đợt chồng lịch thì sao

Chuyện này xảy ra thật: Tết dương và Tết âm có năm nằm sát nhau. Máy chủ chọn
đợt **bớt được nhiều tiền nhất trên đúng giá đang xét**, và chọn ở một chỗ
duy nhất (`KhuyenMaiServiceImpl.dotTotNhat`).

So bằng số tiền chứ không bằng phần trăm, vì "giảm 30%" và "giảm 50.000"
không so trực tiếp được:

- gói 239.000 → 30% = 71.700, lợi hơn 50.000
- gói 99.000 → 30% = 29.700, kém hơn 50.000

## Giá hiển thị và giá bị trừ luôn khớp

Giao diện **không tự tính** mức giảm. Nó gọi `GET /api/v1/khuyen-mai/gia-goi`,
máy chủ trả về giá cuối của từng gói, và giao diện chỉ việc hiện.

Lý do: lặp quy tắc ở web rồi lặp lần nữa ở app là ba bản cài đặt của cùng một
phép tính. Chỉ cần một bản quên cái trần giảm là trang hiện một giá còn ví
trừ một giá khác — và khách phát hiện ra điều đó **sau khi** tiền đã trừ.

## Lời chúc chỉ gửi một lần

Ứng dụng gọi `POST /api/v1/khuyen-mai/loi-chuc` sau khi đăng nhập. Máy chủ trả
lời chúc chưa xem và **đánh dấu đã xem ngay trong cùng lượt gọi**, rồi những
lần sau trả 204.

Là `POST` chứ không phải `GET` vì nó đổi trạng thái. Để `GET` thì một cú tải
lại của trình duyệt, hay một proxy nào đó gọi thử, là lời chúc biến mất trước
khi người dùng kịp thấy.

Bảng `khuyen_mai_da_chuc` dùng **khoá chính ghép** `(khuyen_mai_id, user_id)`.
Đó chính là thứ bảo đảm "một lần": mở app trên điện thoại và web gần như cùng
lúc thì cả hai đều đọc thấy "chưa xem", nhưng cơ sở dữ liệu chỉ nhận một hàng
— bên thua coi như đã xem.

## Google Play KHÔNG giảm giá được

Giá gói AI trên Android do **Play Console** quyết định, máy chủ không đổi
được. Nên khuyến mãi chỉ áp cho:

- mua bằng **ví** trên web và app
- mua qua **PayOS** trên web

Muốn giảm giá trên Android thì phải vào Play Console đặt giá khuyến mãi cho
từng sản phẩm, và nhớ sửa cột `subscription_plan.google_play_price` cho khớp
(xem `GOOGLE-PLAY-BILLING.md`).

Đây là giới hạn của Play, không phải thiếu sót của phần này.

## Đổi đợt thì mọi máy đang mở cập nhật ngay

Lưu hoặc xoá một đợt, máy chủ phát một gói lên `/topic/khuyen-mai`. Web và app
đang mở nghe kênh ấy và gọi lại danh sách.

Gói phát **không mang nội dung**, chỉ là một tín hiệu "có đổi". Gửi kèm nội
dung thì phải tự hỏi "người này có quyền thấy đợt đang tắt không", và một lần
trả lời sai là lộ đợt chưa công bố cho toàn bộ khách đang online.

Phát hỏng thì chỉ ghi log — đợt đã lưu xong rồi, lần tải trang sau vẫn đúng.

## Kiểm sau khi nhập

1. Tạo một đợt ngắn: bắt đầu từ bây giờ, kết thúc sau 1 giờ, giảm 10%.
2. Mở trang **Gói AI** bằng cửa sổ ẩn danh → giá phải hiện mức đã giảm.
3. Đăng nhập bằng một tài khoản thường → lời chúc hiện **một lần**; tải lại
   trang thì không hiện nữa.
4. Mua một gói bằng ví → số tiền trừ phải **khớp với số vừa nhìn thấy**.
5. Tắt đợt trong trang quản trị → giá ở tab đang mở trở về như cũ mà không
   cần tải lại.

Bước 4 là bước đáng làm nhất. Trang hiện một giá mà ví trừ một giá khác là
lỗi duy nhất ở đây khiến khách mất lòng tin, và nó không lộ ra ở bất kỳ bước
nào trước đó.
