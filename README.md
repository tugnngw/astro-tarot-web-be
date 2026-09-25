1. TỔNG QUAN DỰ ÁN

Astro Tarot Web là nền tảng sử dụng AI kết hợp chiêm tinh và bài Tarot để cung cấp dịch vụ xem bói cá nhân hóa. Nền tảng kết nối người dùng với các chuyên gia Tarot để thực hiện các buổi xem trực tiếp, tạo ra một thị trường nơi AI cung cấp dịch vụ xem ban đầu và chuyên gia con người cung cấp dịch vụ sâu hơn.

**Dữ liệu demo để test:** xem [SEED.md](./SEED.md) (Flyway `V2_4`, mật khẩu chung `admin123`).

Công nghệ sử dụng



|Thành phần|Công nghệ|Phiên bản|Mục đích|
|-|-|-|-|
|**Backend**|Java 21+|OpenJDK|Ngôn ngữ lập trình chính|
||Spring Boot|3.1.x|Framework Monothilic|
||Spring Cloud|2022.0.x|Gateway, Config, Discovery|
|**Database**|PostgreSQL|15+|Database quan hệ chính|
||Flyway|-|Quản lý migration database|
|**Caching**|Redis|7+|Lưu trữ session, giới hạn tỷ lệ|
|**Messaging**|RabbitMQ|-|Xử lý async, event-driven|
|**AI Integration**|Swiss Ephemeris|2.01+|Tính toán chiêm tinh|
||OpenCage/Google Maps|-|Dịch vụ geocoding|
||OpenAI/Gemini/Claude|-|Giải thích Tarot|
|**Payment**|VNPay/Momo/Stripe|-|Xử lý thanh toán|
|**Frontend**|React.js|18+|Ứng dụng web|
||Tailwind CSS|-|Framework CSS|
||D3.js/Fabric.js|-|Trực quan hóa bản đồ sao|
|**Real-time**|WebSocket|-|Chat, gọi video|
|**Monitoring**|Prometheus|-|Thu thập metrics|
||Grafana|-|Trực quan hóa dashboard|




---

## Kiểm thử và độ phủ

```bash
./mvnw test
```

**863 kiểm**, độ phủ dòng **82,7%** (báo cáo ở `target/site/jacoco/index.html`).

### Chạy SonarQube

Cấu hình đã khai sẵn trong `pom.xml`, nên chỉ cần một token:

```bash
docker run -d --name sonarqube -p 9000:9000 sonarqube:community
```

Mở http://localhost:9000, đăng nhập, vào **My Account → Security** tạo token, rồi:

```bash
./mvnw clean test
./mvnw sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=THAY_TOKEN_VAO_DAY
```

### Ba điều dễ nhầm

**Sonar không tự chạy test.** Nó chỉ đọc báo cáo JaCoCo đã có, nên `mvn test`
phải chạy **trước**. Đảo thứ tự thì Sonar báo 0% và người đọc tưởng bộ kiểm
không tồn tại.

**Sonar không dùng độ phủ dòng.** Công thức của nó là
`(CT + CF + LC) / (2B + EL)` — gộp cả nhánh vào. Nên **82,7% dòng** ở đây quy
ra khoảng **80,3%** trên Sonar, và đó mới là con số Quality Gate đọc. Hai con
số khác nhau là bình thường, không phải lỗi cấu hình.

**Bộ kiểm cố tình đi vào nhánh lỗi.** `PayOsWebhookRegistrarTest` kiểm nhánh
"đã thử sáu lần vẫn không đăng ký được webhook", nên log build có một dòng
`ERROR ... không đăng ký được PayOS webhook`. Đó là mã sản xuất đang chạy đúng
kịch bản được kiểm, không phải bản dựng hỏng.

### Quy ước của bộ kiểm

Không viết kiểm cho mọi nhánh. Chọn những bất biến mà nếu vỡ thì **mất tiền
hoặc rò dữ liệu**, và mỗi phép kiểm nói rõ hậu quả nếu nó đỏ — để người sửa
sau biết mình đang phá cái gì, chứ không chỉ thấy một dòng đỏ không rõ nghĩa.

Vài ví dụ:

| Bất biến | Hậu quả nếu vỡ |
|---|---|
| Bấm "Thanh toán" hai lần trả lại **cùng một** mã chuyển khoản | Hai lần đối soát, một lần thu trùng |
| Webhook PayOS mang `orderCode` lạ **không** được thành 5xx | PayOS kết luận URL hỏng và từ chối đăng ký webhook |
| Không hoàn tất buổi xem **trước** giờ hẹn | Nhận tiền cho một buổi chưa từng diễn ra |
| Tiền phạt **không** đụng `pendingBalance` | Lấy tiền của người thứ ba (khách chưa xong buổi) |
| "Quên mật khẩu" trả **cùng một câu** dù email có thật hay không | Trang này thành công cụ dò xem ai có tài khoản |
