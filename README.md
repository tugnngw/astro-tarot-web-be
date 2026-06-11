1. TỔNG QUAN DỰ ÁN

Astro Tarot Web là nền tảng sử dụng AI kết hợp chiêm tinh và bài Tarot để cung cấp dịch vụ xem bói cá nhân hóa. Nền tảng kết nối người dùng với các chuyên gia Tarot để thực hiện các buổi xem trực tiếp, tạo ra một thị trường nơi AI cung cấp dịch vụ xem ban đầu và chuyên gia con người cung cấp dịch vụ sâu hơn.

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



