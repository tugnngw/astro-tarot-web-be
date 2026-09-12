package com.exe.astratarot.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Điểm dò "máy chủ đã dậy chưa", công khai và rẻ nhất có thể.
 *
 * <p>Vì sao cần một endpoint riêng thay vì dùng {@code /actuator/health}: cái
 * đó bị Cloudflare Worker ở phía trước chặn (trả 403 EDGE_BLOCKED_PATH), và
 * chặn như vậy là đúng — không nên phơi actuator ra ngoài.
 *
 * <p>Đường dẫn {@code /ping} vốn đã nằm trong danh sách công khai của
 * SecurityConfig từ trước, nhưng chưa từng có controller nào phục vụ nó. Hệ
 * quả: banner "Máy chủ đang khởi động" ở giao diện nhận 404 ngay từ lần tải
 * đầu, bật lên rồi treo vĩnh viễn dù máy chủ vẫn khoẻ.
 *
 * <p>KHÔNG chạm vào database. Mục đích duy nhất là trả lời "tiến trình đã nhận
 * được request chưa" — thêm một truy vấn vào đây là biến phép dò rẻ thành một
 * thứ có thể tự nó chậm.
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "at", System.currentTimeMillis()));
    }
}
