package com.exe.astratarot.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Xin thông tin đăng nhập TURN từ Cloudflare.
 *
 * <p>Hai thứ được giữ ở đây, và cả hai đều im lặng khi hỏng nên không có cách
 * nào khác để biết chúng còn đúng:
 *
 * <ul>
 *   <li><b>Hỏng thì trả rỗng, không ném lỗi.</b> Không xin được thì vẫn còn
 *       STUN và người cùng wifi vẫn gọi được. Ném lỗi lên thì endpoint cấu
 *       hình chết và KHÔNG AI gọi được — biến sự cố của Cloudflare thành sự
 *       cố của mình.</li>
 *   <li><b>Giữ lại dùng chung.</b> Mỗi lần ai đó mở cuộc gọi mà gọi lại API
 *       thì vừa chậm vừa đụng hạn mức, trong khi một bộ dùng được cả ngày.</li>
 * </ul>
 */
class CloudflareTurnServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Máy chủ giả; trả về địa chỉ gốc để truyền vào service. */
    private String mayChuGia(int ma, String than, AtomicInteger demLuotGoi,
                             AtomicReference<String> batHeader) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            demLuotGoi.incrementAndGet();
            batHeader.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] b = than.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(ma, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static final String PHAN_HOI_THAT = """
            {"iceServers":[
              {"urls":["stun:stun.cloudflare.com:3478"]},
              {"urls":["turn:turn.cloudflare.com:3478?transport=udp",
                       "turns:turn.cloudflare.com:443?transport=tcp"],
               "username":"nguoi-dung-tam","credential":"mat-khau-tam"}
            ]}""";

    @Test
    @DisplayName("Chưa cấu hình thì tắt hẳn, không gọi mạng")
    void chuaCauHinhThiTat() {
        CloudflareTurnService s = new CloudflareTurnService(
                "", "", "http://khong-ton-tai.invalid", 86400);

        assertAll(
                () -> assertFalse(s.daBat()),
                // Không được ném, và không được treo vì chờ một địa chỉ không
                // tồn tại: deploy mã mới trước khi kịp điền biến môi trường là
                // chuyện bình thường.
                () -> assertTrue(s.iceServers().isEmpty()));
    }

    @Test
    @DisplayName("Xin được thì trả đúng mục CÓ thông tin đăng nhập, bỏ mục STUN")
    void xinDuocThiTraMucCoDangNhap() throws Exception {
        AtomicInteger dem = new AtomicInteger();
        AtomicReference<String> auth = new AtomicReference<>();
        String goc = mayChuGia(200, PHAN_HOI_THAT, dem, auth);

        CloudflareTurnService s = new CloudflareTurnService("khoa1", "token-bi-mat", goc, 86400);
        List<Map<String, Object>> ra = s.iceServers();

        assertAll(
                () -> assertEquals(1, ra.size(),
                        "mục STUN của Cloudflare phải bị bỏ — endpoint cấu hình đã tự khai STUN,"
                                + " và để nó lọt vào đây sẽ làm cờ hasTurn nói dối"),
                () -> assertEquals("nguoi-dung-tam", ra.get(0).get("username")),
                () -> assertEquals("mat-khau-tam", ra.get(0).get("credential")),
                () -> assertEquals(2, ((List<?>) ra.get(0).get("urls")).size()),
                () -> assertEquals("Bearer token-bi-mat", auth.get()));
    }

    @Test
    @DisplayName("Giữ lại dùng chung — lần gọi thứ hai KHÔNG đụng API nữa")
    void giuLaiDungChung() throws Exception {
        AtomicInteger dem = new AtomicInteger();
        String goc = mayChuGia(200, PHAN_HOI_THAT, dem, new AtomicReference<>());

        CloudflareTurnService s = new CloudflareTurnService("khoa1", "token", goc, 86400);
        s.iceServers();
        s.iceServers();
        s.iceServers();

        // Một bộ dùng được 24 giờ. Xin lại mỗi lần ai đó bấm nút gọi là vừa
        // thêm một nhịp chờ vào lúc người dùng sốt ruột nhất, vừa đụng hạn mức
        // cấp thông tin đăng nhập của Cloudflare.
        assertEquals(1, dem.get());
    }

    @Test
    @DisplayName("Cloudflare trả lỗi thì trả rỗng, KHÔNG ném")
    void loiThiTraRong() throws Exception {
        AtomicInteger dem = new AtomicInteger();
        String goc = mayChuGia(401, "{\"errors\":[{\"message\":\"sai token\"}]}",
                dem, new AtomicReference<>());

        CloudflareTurnService s = new CloudflareTurnService("khoa1", "token-sai", goc, 86400);

        // Sai token là lỗi cấu hình của mình, nhưng nó KHÔNG được làm chết cả
        // endpoint ICE: còn STUN thì người cùng wifi vẫn gọi được.
        assertTrue(s.iceServers().isEmpty());
    }

    @Test
    @DisplayName("Phản hồi không đọc được thì trả rỗng")
    void phanHoiHongThiTraRong() throws Exception {
        String goc = mayChuGia(200, "khong-phai-json", new AtomicInteger(), new AtomicReference<>());
        CloudflareTurnService s = new CloudflareTurnService("khoa1", "token", goc, 86400);

        assertTrue(s.iceServers().isEmpty());
    }

    @Test
    @DisplayName("Mục thiếu username hoặc thiếu urls đều bị bỏ")
    void bomucThieu() {
        CloudflareTurnService s = new CloudflareTurnService("", "", "http://x.invalid", 86400);

        // Một mục TURN thiếu username thì trình duyệt lặng lẽ bỏ qua nó, và ta
        // mất hàng giờ tưởng TURN đang chạy trong khi thật ra không.
        assertAll(
                () -> assertTrue(s.doc("""
                        {"iceServers":[{"urls":["turn:a:3478"]}]}""").isEmpty()),
                () -> assertTrue(s.doc("""
                        {"iceServers":[{"urls":[],"username":"u","credential":"c"}]}""").isEmpty()),
                () -> assertTrue(s.doc("""
                        {"iceServers":[{"urls":["turn:a:3478"],"username":"u","credential":""}]}""")
                        .isEmpty()),
                () -> assertTrue(s.doc("{}").isEmpty()),
                // urls dạng chuỗi đơn thay vì mảng — chuẩn WebRTC cho phép cả
                // hai, nên đừng chỉ đọc được một dạng.
                () -> assertEquals(1, s.doc("""
                        {"iceServers":[{"urls":"turn:a:3478","username":"u","credential":"c"}]}""")
                        .size()));
    }
}
