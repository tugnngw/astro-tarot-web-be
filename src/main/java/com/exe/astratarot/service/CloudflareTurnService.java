package com.exe.astratarot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Xin thông tin đăng nhập TURN từ Cloudflare Realtime.
 *
 * <h3>Vì sao cần TURN</h3>
 *
 * <p>STUN chỉ giúp hai máy tự tìm ra địa chỉ công khai của nhau rồi nối
 * thẳng. Khi cả hai nằm sau NAT đối xứng thì không có địa chỉ nào để nối
 * thẳng tới, và cuộc gọi đứng im ở "đang kết nối" cho tới khi hết giờ. Ở Việt
 * Nam mọi nhà mạng di động đều dùng CGNAT, nên <b>4G gần như luôn rơi vào
 * trường hợp này</b> — đó là lý do gọi chỉ chạy khi hai bên cùng một wifi
 * thường.
 *
 * <p>TURN là máy chủ trung chuyển: hai bên cùng gửi vào đó và nhận ra từ đó,
 * nên không cần nối thẳng nữa. Nó tốn băng thông vì toàn bộ tiếng và hình đi
 * qua nó, và đó là lý do người ta tính tiền theo GB.
 *
 * <h3>Vì sao phải xin theo phiên chứ không đặt sẵn vào biến môi trường</h3>
 *
 * <p>Cloudflare không cấp thông tin đăng nhập cố định. Mỗi bộ chỉ sống tối đa
 * 48 giờ và phải xin qua API bằng khoá riêng. Đó là chủ ý của họ: một bộ lộ ra
 * ngoài thì kẻ khác dùng băng thông trên hoá đơn của mình, và tuổi đời ngắn
 * giới hạn thiệt hại.
 *
 * <p>Nên chỗ này xin rồi <b>giữ lại dùng chung cho mọi người</b>, xin lại
 * trước hạn một tiếng. Với TTL mặc định 24 giờ thì một ngày gọi API một lần,
 * chứ không phải mỗi lần ai đó mở cuộc gọi.
 *
 * <h3>Hỏng thì trả rỗng, không ném lỗi</h3>
 *
 * <p>Không xin được thì vẫn còn STUN, và cuộc gọi vẫn chạy với những người
 * cùng mạng wifi. Ném lỗi lên thì endpoint cấu hình ICE chết, và lúc ấy
 * <b>không ai</b> gọi được — biến một sự cố của Cloudflare thành sự cố của
 * mình, đúng kiểu {@link TurnstileService} đã tránh.
 */
@Service
@Slf4j
public class CloudflareTurnService {

    private final String keyId;
    private final String apiToken;
    private final String apiBase;
    private final int ttlSeconds;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Xin lại sớm hơn hạn chừng này.
     *
     * <p>Không chờ tới đúng lúc hết hạn: một cuộc gọi bắt đầu ngay trước đó sẽ
     * cầm bộ thông tin sắp chết, và người dùng thấy cuộc gọi rớt giữa chừng mà
     * không hiểu vì sao.
     */
    private static final Duration LE_AN_TOAN = Duration.ofHours(1);

    /** Bộ đang dùng. Một tiến trình, một bản — không cần khoá phức tạp. */
    private volatile BoTin boTin;

    private record BoTin(List<Map<String, Object>> servers, Instant hetHan) {}

    public CloudflareTurnService(
            @Value("${app.rtc.cloudflare.key-id:}") String keyId,
            @Value("${app.rtc.cloudflare.api-token:}") String apiToken,
            @Value("${app.rtc.cloudflare.api-base:https://rtc.live.cloudflare.com/v1/turn/keys}")
            String apiBase,
            @Value("${app.rtc.cloudflare.ttl-seconds:86400}") int ttlSeconds) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.apiBase = apiBase;
        this.ttlSeconds = ttlSeconds;

        if (!daBat()) {
            log.info("Cloudflare TURN CHƯA bật — cuộc gọi chỉ chạy khi hai bên "
                    + "nối thẳng được (thường là cùng một wifi).");
        } else {
            log.info("Cloudflare TURN đã bật, TTL {} giây.", ttlSeconds);
        }
    }

    public boolean daBat() {
        return !keyId.isEmpty() && !apiToken.isEmpty();
    }

    /**
     * Danh sách máy chủ TURN kèm thông tin đăng nhập, đúng dạng
     * {@code RTCIceServer[]} mà trình duyệt và Flutter đều nhận thẳng.
     *
     * <p>Rỗng nghĩa là chưa bật hoặc xin không được. Người gọi phải coi đó là
     * chuyện bình thường chứ không phải lỗi.
     */
    public List<Map<String, Object>> iceServers() {
        if (!daBat()) {
            return List.of();
        }
        BoTin hienCo = boTin;
        if (hienCo != null && Instant.now().isBefore(hienCo.hetHan().minus(LE_AN_TOAN))) {
            return hienCo.servers();
        }
        List<Map<String, Object>> moi = xin();
        if (!moi.isEmpty()) {
            boTin = new BoTin(moi, Instant.now().plusSeconds(ttlSeconds));
            return moi;
        }
        // Xin không được nhưng bộ cũ vẫn còn hạn: dùng tiếp còn hơn không có
        // gì. Đây chính là lý do phải xin sớm một tiếng — có chỗ để lùi.
        return hienCo != null && Instant.now().isBefore(hienCo.hetHan())
                ? hienCo.servers()
                : List.of();
    }

    private List<Map<String, Object>> xin() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/" + keyId + "/credentials/generate-ice-servers"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + apiToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"ttl\":" + ttlSeconds + "}"))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                // KHÔNG ghi thân phản hồi ra log: nó chứa đúng bộ thông tin
                // đăng nhập khi gọi thành công, và log của Render ai vào
                // bảng điều khiển cũng đọc được.
                log.warn("Cloudflare TURN trả mã {} — tạm thời chỉ còn STUN.", res.statusCode());
                return List.of();
            }
            return doc(res.body());
        } catch (java.io.IOException e) {
            log.warn("Không gọi được Cloudflare TURN: {}", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /**
     * Lấy các mục CÓ thông tin đăng nhập ra khỏi phản hồi.
     *
     * <p>Cloudflare trả kèm cả một mục STUN không có username. Bỏ nó đi vì
     * endpoint cấu hình đã tự khai STUN riêng, và vì mục STUN lọt vào đây sẽ
     * làm cờ "đã có TURN" nói dối.
     */
    List<Map<String, Object>> doc(String than) {
        try {
            JsonNode root = mapper.readTree(than).path("iceServers");
            List<Map<String, Object>> ra = new ArrayList<>();
            for (JsonNode muc : root.isArray() ? root : mapper.createArrayNode()) {
                String user = muc.path("username").asText("");
                String pass = muc.path("credential").asText("");
                if (user.isEmpty() || pass.isEmpty()) {
                    continue;
                }
                List<String> urls = new ArrayList<>();
                JsonNode u = muc.path("urls");
                if (u.isArray()) {
                    u.forEach(x -> urls.add(x.asText()));
                } else if (u.isTextual()) {
                    urls.add(u.asText());
                }
                if (urls.isEmpty()) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("urls", urls);
                entry.put("username", user);
                entry.put("credential", pass);
                ra.add(entry);
            }
            return ra;
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            log.warn("Phản hồi Cloudflare TURN không đọc được: {}", e.getMessage());
            return List.of();
        }
    }
}
