package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Danh sách máy chủ ICE cho WebRTC, đọc từ biến môi trường.
 *
 * <h3>Vì sao là một endpoint chứ không phải hằng số trong mã giao diện</h3>
 *
 * <p>Dự án hiện chạy <b>chỉ STUN, không TURN</b> — đây là lựa chọn có ý thức
 * của chủ dự án để khỏi tốn tiền. Hệ quả cần biết: STUN chỉ giúp hai máy tự
 * tìm nhau; khi cả hai nằm sau NAT đối xứng (rất phổ biến với 4G ở Việt Nam,
 * do nhà mạng dùng CGNAT) thì <b>cuộc gọi sẽ không kết nối được</b>. Trên
 * wifi nhà hay mạng trường thì phần lớn chạy.
 *
 * <p>Đặt ở đây để ngày muốn thêm TURN thì chỉ cần thêm một biến môi trường
 * trên Render rồi khởi động lại — không phải sửa mã giao diện, không phải
 * build lại và deploy lại Vercel. Nếu nhúng cứng vào mã FE thì mỗi lần đổi
 * thông tin TURN là một vòng deploy đầy đủ, và thông tin đăng nhập TURN sẽ
 * nằm vĩnh viễn trong bundle công khai.
 *
 * <p>Ví dụ bật TURN sau này (Cloudflare Realtime TURN có mức miễn phí):
 * <pre>
 * RTC_ICE_URLS=stun:stun.l.google.com:19302,turn:turn.example.com:3478
 * RTC_TURN_USERNAME=...
 * RTC_TURN_CREDENTIAL=...
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/rtc")
@RequiredArgsConstructor
public class RtcConfigController {

    @Value("${app.rtc.ice-urls:stun:stun.l.google.com:19302,stun:stun1.l.google.com:19302}")
    private String iceUrls;

    @Value("${app.rtc.turn-username:}")
    private String turnUsername;

    @Value("${app.rtc.turn-credential:}")
    private String turnCredential;

    /**
     * Trả về đúng dạng {@code RTCIceServer[]} mà {@code RTCPeerConnection}
     * nhận, để giao diện đưa thẳng vào không phải nắn lại.
     */
    @GetMapping("/ice")
    @PreAuthorize("hasAuthority('USER_BASIC')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ice() {
        List<String> stun = new ArrayList<>();
        List<String> turn = new ArrayList<>();

        for (String raw : iceUrls.split(",")) {
            String url = raw.trim();
            if (url.isEmpty()) {
                continue;
            }
            if (url.startsWith("turn:") || url.startsWith("turns:")) {
                turn.add(url);
            } else {
                stun.add(url);
            }
        }

        List<Map<String, Object>> servers = new ArrayList<>();
        if (!stun.isEmpty()) {
            servers.add(Map.of("urls", stun));
        }
        // Chỉ khai TURN khi có đủ thông tin đăng nhập. Gửi TURN thiếu
        // username/credential thì trình duyệt lặng lẽ bỏ qua nó, và ta mất
        // hàng giờ tưởng TURN đang chạy trong khi thật ra không.
        if (!turn.isEmpty() && !turnUsername.isBlank() && !turnCredential.isBlank()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("urls", turn);
            entry.put("username", turnUsername);
            entry.put("credential", turnCredential);
            servers.add(entry);
        }

        boolean hasTurn = servers.size() > 1;
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "iceServers", servers,
                // Giao diện dùng cờ này để cảnh báo trước thay vì để người
                // dùng ngồi nhìn màn hình "đang kết nối" cho tới khi hết giờ.
                "hasTurn", hasTurn)));
    }

    /** Dùng cho test: tách phần phân loại ra khỏi Spring. */
    static List<String> split(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
