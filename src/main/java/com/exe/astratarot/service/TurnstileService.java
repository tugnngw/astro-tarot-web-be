package com.exe.astratarot.service;

import com.exe.astratarot.exception.TurnstileVerificationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Kiểm tra token Cloudflare Turnstile ở các endpoint dễ bị dò tự động
 * (đăng nhập, đăng ký, quên mật khẩu).
 *
 * <h3>Hai quyết định về "hỏng thì mở hay đóng"</h3>
 *
 * <p><b>Chưa cấu hình thì BỎ QUA.</b> {@code TURNSTILE_SECRET} để trống nghĩa
 * là tính năng chưa bật. Nếu chỗ này chặn khi thiếu khoá thì chỉ cần deploy mã
 * mới trước khi kịp điền biến môi trường là toàn bộ đăng nhập chết — một thay
 * đổi bảo mật không được phép tự biến thành sự cố ngừng dịch vụ.
 *
 * <p><b>Gọi Cloudflare hỏng thì CHO QUA.</b> Mạng lỗi hay siteverify sập là
 * chuyện của hạ tầng, không phải bằng chứng người dùng là bot. Đóng ở đây biến
 * một sự cố của Cloudflare thành một sự cố của mình. Ngược lại, khi Cloudflare
 * trả lời rõ ràng "token này không hợp lệ" thì CHẶN — đó mới là câu trả lời có
 * nghĩa.
 *
 * <p>Nói cách khác: chỉ chặn khi có bằng chứng phủ định, không chặn khi thiếu
 * thông tin.
 */
@Service
@Slf4j
public class TurnstileService {

    private final String secret;
    private final String verifyUrl;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public TurnstileService(
            @Value("${app.turnstile.secret:}") String secret,
            @Value("${app.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
            String verifyUrl) {
        this.secret = secret == null ? "" : secret.trim();
        this.verifyUrl = verifyUrl;
        if (this.secret.isEmpty()) {
            log.info("Turnstile CHƯA bật (TURNSTILE_SECRET trống) — bỏ qua kiểm tra.");
        } else {
            log.info("Turnstile đã bật cho các endpoint đăng nhập/đăng ký.");
        }
    }

    /** Tính năng có đang bật không — giao diện không cần biết, chỉ dùng để test. */
    public boolean isEnabled() {
        return !secret.isEmpty();
    }

    /**
     * @param token  giá trị header {@code X-Turnstile-Token} do widget ở giao diện sinh ra
     * @param remoteIp IP người gọi, gửi kèm cho Cloudflare đối chiếu (có thể null)
     * @throws TurnstileVerificationException khi Cloudflare khẳng định token sai
     */
    public void verify(String token, String remoteIp) {
        if (secret.isEmpty()) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new TurnstileVerificationException(
                    "Thiếu xác minh chống bot. Tải lại trang rồi thử lại.");
        }

        try {
            StringBuilder form = new StringBuilder()
                    .append("secret=").append(enc(secret))
                    .append("&response=").append(enc(token));
            if (remoteIp != null && !remoteIp.isBlank()) {
                form.append("&remoteip=").append(enc(remoteIp));
            }

            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create(verifyUrl))
                            .timeout(Duration.ofSeconds(8))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() / 100 != 2) {
                log.warn("Turnstile siteverify trả {} — cho qua để không chặn nhầm người thật.",
                        res.statusCode());
                return;
            }

            JsonNode body = mapper.readTree(res.body());
            if (body.path("success").asBoolean(false)) {
                return;
            }

            log.info("Turnstile từ chối token: {}", body.path("error-codes"));
            throw new TurnstileVerificationException(
                    "Xác minh chống bot thất bại. Tải lại trang rồi thử lại.");

        } catch (TurnstileVerificationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Bị ngắt khi gọi Turnstile — cho qua.");
        } catch (Exception e) {
            log.warn("Không gọi được Turnstile ({}) — cho qua.", e.toString());
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
