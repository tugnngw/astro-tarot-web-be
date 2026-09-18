package com.exe.astratarot.service;

import com.exe.astratarot.exception.TurnstileVerificationException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bộ test này giữ đúng MỘT thứ: quy tắc "hỏng thì mở hay đóng".
 *
 * <p>Đó là chỗ dễ sửa sai nhất. Ai đó thấy "bảo mật" rồi đổi thành chặn-hết
 * cho chắc, thế là một lần Cloudflare chập mạng đủ làm không ai đăng nhập
 * được. Ba test dưới đây sẽ đỏ nếu quy tắc bị đảo.
 */
class TurnstileServiceTest {

    private static final String VERIFY = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    @Test
    void chuaCauHinhThiBoQuaHoanToan() {
        TurnstileService s = new TurnstileService("", VERIFY);

        assertFalse(s.isEnabled());
        // Không token, không mạng, vẫn phải cho qua — nếu không thì chỉ cần
        // deploy trước khi điền biến môi trường là đăng nhập chết.
        assertDoesNotThrow(() -> s.verify(null, "1.2.3.4"));
        assertDoesNotThrow(() -> s.verify("", null));
    }

    @Test
    void daBatMaThieuTokenThiChan() {
        TurnstileService s = new TurnstileService("bi-mat-gia", VERIFY);

        assertTrue(s.isEnabled());
        assertThrows(TurnstileVerificationException.class, () -> s.verify(null, "1.2.3.4"));
        assertThrows(TurnstileVerificationException.class, () -> s.verify("   ", "1.2.3.4"));
    }

    @Test
    void cloudflareNoiKhongThiChan() throws Exception {
        try (StubSiteverify stub = new StubSiteverify("{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}", 200)) {
            TurnstileService s = new TurnstileService("bi-mat-gia", stub.url());
            assertThrows(TurnstileVerificationException.class, () -> s.verify("token-bia", "1.2.3.4"));
        }
    }

    @Test
    void cloudflareNoiOkThiChoQua() throws Exception {
        try (StubSiteverify stub = new StubSiteverify("{\"success\":true}", 200)) {
            TurnstileService s = new TurnstileService("bi-mat-gia", stub.url());
            assertDoesNotThrow(() -> s.verify("token-that", "1.2.3.4"));
        }
    }

    @Test
    void cloudflareHongThiCHOQUA() throws Exception {
        // 500 từ siteverify là sự cố của Cloudflare, không phải bằng chứng
        // người dùng là bot. Chặn ở đây là tự biến sự cố của họ thành của mình.
        try (StubSiteverify stub = new StubSiteverify("boom", 500)) {
            TurnstileService s = new TurnstileService("bi-mat-gia", stub.url());
            assertDoesNotThrow(() -> s.verify("token-bat-ky", "1.2.3.4"));
        }
    }

    @Test
    void khongGoiDuocThiCHOQUA() {
        // Cổng không có ai nghe → ConnectException. Vẫn phải cho qua.
        TurnstileService s = new TurnstileService("bi-mat-gia", "http://127.0.0.1:1/siteverify");
        assertDoesNotThrow(() -> s.verify("token-bat-ky", "1.2.3.4"));
    }

    /** Máy chủ giả tối giản, trả đúng một câu trả lời cố định. */
    private static final class StubSiteverify implements AutoCloseable {
        private final HttpServer server;

        StubSiteverify(String body, int status) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/siteverify", ex -> {
                byte[] out = body.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(status, out.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(out);
                }
            });
            server.start();
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
