# Đăng nhập bằng Google

Mã đã xong cả ba đầu. Việc còn lại chỉ là **đặt hai biến trên Render** — chưa
đặt thì nút "Tiếp tục với Google" không hiện ra lỗi gì, nó chỉ đưa người dùng
tới một trang báo lỗi của Google.

## Đã làm sẵn trong Google Cloud (21/10/2026)

Project `astrotarot-play` — dùng chung với Play Billing và GA4.

| | |
| --- | --- |
| Màn hình chấp thuận | `AstroTarot`, kiểu **External**, liên hệ `megalit2578@gmail.com` |
| OAuth client | `AstroTarot Web`, kiểu *Web application* |
| Client ID | `777685600175-nrm4h4im160il6pm3jbjjqkmoaqgaph8.apps.googleusercontent.com` |
| Redirect URI | `https://api.astrotarot.date/login/oauth2/code/google`<br>`http://localhost:8080/login/oauth2/code/google` |
| JavaScript origin | `https://astrotarot.date` |

Client secret **không** ghi ở đây và không commit vào repo. Mất thì vào
Clients → `AstroTarot Web` → thêm secret mới, không phải tạo lại client.

## Việc duy nhất phải làm tay

Render → service backend → **Environment**:

| Tên biến | Giá trị |
| --- | --- |
| `GOOGLE_CLIENT_ID` | chuỗi `...apps.googleusercontent.com` ở bảng trên |
| `GOOGLE_CLIENT_SECRET` | chuỗi bắt đầu bằng `GOCSPX-` |

Lưu biến là Render tự dựng lại dịch vụ.

**Để trống thì máy chủ vẫn khởi động bình thường**, chỉ riêng đăng nhập Google
không chạy. Đây là điều có chủ đích: `SecurityConfig` hỏi
`ClientRegistrationRepository` bằng `ObjectProvider` chứ không tiêm thẳng.
Tiêm bắt buộc thì thiếu một biến là cả máy chủ không lên được — đã có tiền lệ
đúng kiểu này khi đặt khoá GA4 lên Render.

## Hiện chỉ 4 tài khoản đăng nhập được

App đang ở trạng thái **Testing**, nên Google chỉ cho những tài khoản trong
danh sách test user đi qua. Người lạ bấm nút sẽ bị từ chối ngay trên trang của
Google, không phải lỗi của mình.

Danh sách hiện tại (Google Auth Platform → **Audience** → Test users):

- `megalit2578@gmail.com`
- `tranduydatqtdl@gmail.com`
- `tranduydatquahay@gmail.com`
- `alloffdenchi13@gmail.com`

Thêm người thử thì vào đúng trang ấy, tối đa 100 tài khoản.

### Mở cho mọi người

Audience → **Publish app**. Với phạm vi đang dùng (`openid`, `email`,
`profile` — Google xếp là *non-sensitive*) thì **không cần gửi xét duyệt**,
bật là chạy.

Đổi sang xin thêm quyền đọc Gmail, Drive, Lịch… thì mới phải qua xác minh, và
quá trình ấy tính bằng tuần. Đừng thêm scope nếu không thật sự cần.

## Vì sao luồng này đi hai chặng

Trình duyệt rời khỏi máy chủ sang Google, rồi quay lại
`/login/oauth2/code/google`. Máy chủ tạo một **mã dùng-một-lần**, chuyển hướng
người dùng về `astrotarot.date/oauth-success?code=…`, và trang ấy đổi mã lấy
token.

Vì sao không trả thẳng token: máy chủ đưa người dùng về bằng một lần chuyển
hướng, nên token sẽ phải nằm trong **thanh địa chỉ** — chỗ bị lưu vào lịch sử
trình duyệt, bị gửi kèm làm referrer sang trang khác, và hiện nguyên trên màn
hình. Mã đổi thì vô dụng sau lần dùng đầu và chỉ sống vài phút.

## Cái bẫy đã làm tính năng này bị tắt suốt một thời gian

`.oauth2Login(...)` từng bị chú thích tắt trong `SecurityConfig`, và lý do
không hiện ra khi đọc code.

Máy chủ chạy `SessionCreationPolicy.STATELESS`. Spring mặc định nhớ yêu cầu uỷ
quyền — nhất là mảnh `state` chống giả mạo — bằng `HttpSession`. Không có
phiên thì khi Google trả người dùng về, Spring không tìm thấy gì và chặn với
`authorization_request_not_found`.

Hỏng ở **đúng bước cuối**: nút bấm được, Google hỏi quyền xong xuôi, rồi người
dùng rơi vào trang lỗi. Nên chỉ bỏ dấu chú thích là không đủ.

Bản sửa là `CookieOAuth2AuthorizationRequestRepository`: giữ yêu cầu trong một
cookie sống 3 phút, `HttpOnly`, `Secure`, và `SameSite=Lax` chứ **không phải
`Strict`** — web và API khác tên miền nên chặng Google trả về là điều hướng
xuyên site, `Strict` thì trình duyệt không gửi cookie kèm và ta quay lại đúng
lỗi đang muốn tránh.

## Tài khoản đã có mật khẩu thì sao

`OAuth2LoginSuccessHandler` tra theo `providerId` của Google trước; không thấy
thì tra theo **email**. Trùng email thì nó **nối** tài khoản Google vào tài
khoản sẵn có thay vì tạo thêm một cái mới. Người đăng ký bằng email từ trước,
sau này bấm nút Google, vẫn vào đúng tài khoản cũ với đủ lịch sử và số dư ví.

Google phải xác nhận email đã verify — `email_verified` sai thì từ chối. Thiếu
kiểm tra ấy, ai đó tạo tài khoản Google với email của người khác là chiếm được
tài khoản ở đây.

## Kiểm sau khi đặt biến

1. Mở astrotarot.date bằng cửa sổ ẩn danh → Đăng nhập → **Tiếp tục với
   Google**.
2. Chọn một trong bốn tài khoản test ở trên.
3. Phải quay về trang chủ trong trạng thái đã đăng nhập.
4. Thử lần hai bằng tài khoản đã từng đăng ký bằng mật khẩu → phải vào đúng
   tài khoản cũ, không sinh tài khoản trùng.

Bước 4 là bước đáng làm nhất. Sinh tài khoản trùng thì người dùng mất lịch sử
và số dư ví, mà lỗi ấy chỉ lộ ra sau khi đã xảy ra với người thật.
