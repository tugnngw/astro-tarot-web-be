package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.auth.AuthResponse;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.exception.InvalidCredentialsException;
import com.exe.astratarot.service.TaiKhoanGoogleService;
import com.exe.astratarot.service.TokenIssuerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Set;

/**
 * Đăng nhập Google cho app di động.
 *
 * <h2>Vì sao app không đi chung lối với web</h2>
 *
 * Lối của web là chuyển hướng trình duyệt sang Google rồi nhận lại mã. App
 * không có thanh địa chỉ để chuyển hướng về; thay vào đó thư viện
 * {@code google_sign_in} lấy sẵn một <b>ID token</b> ngay trên máy, và máy
 * chủ chỉ việc xác minh token ấy.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/oauth/google")
@RequiredArgsConstructor
public class GoogleMobileAuthController {

    /** Google xác minh chữ ký và hạn của token, trả về các trường bên trong. */
    private static final String TOKENINFO = "https://oauth2.googleapis.com/tokeninfo";

    private final TaiKhoanGoogleService taiKhoanGoogleService;
    private final TokenIssuerService tokenIssuerService;

    /**
     * Client ID của app Android, và của web dùng chung.
     *
     * <p>Android có client ID RIÊNG, khác client web — Google cấp mỗi nền
     * tảng một cái. Nhưng {@code google_sign_in} trên Android lấy ID token
     * mang {@code aud} là client ID <b>WEB</b> (tham số `serverClientId`),
     * nên cả hai đều phải nằm trong danh sách chấp nhận.
     */
    @Value("${app.google.accepted-audiences:${spring.security.oauth2.client.registration.google.client-id:}}")
    private String audienceChapNhan;

    private final RestClient restClient = RestClient.create();

    public record DangNhapGoogleRequest(
            @NotBlank(message = "Thiếu ID token của Google") String idToken) {
    }

    @PostMapping("/mobile")
    public ResponseEntity<AuthResponse> dangNhap(@Valid @RequestBody DangNhapGoogleRequest request) {
        Map<?, ?> thongTin = hoiGoogle(request.idToken());

        // KIỂM `aud` LÀ BẮT BUỘC. Thiếu nó thì bất kỳ ID token nào của bất kỳ
        // ứng dụng Google nào cũng đăng nhập được vào đây — một người tạo app
        // riêng, dụ nạn nhân đăng nhập, lấy token ấy gửi sang máy chủ này là
        // chiếm được tài khoản mang email của nạn nhân.
        String aud = chuoi(thongTin.get("aud"));
        if (aud == null || !audienceHopLe().contains(aud)) {
            log.warn("ID token Google có aud không khớp: {}", aud);
            throw new InvalidCredentialsException("Token Google không dành cho ứng dụng này.");
        }

        boolean emailDaXacMinh = "true".equalsIgnoreCase(chuoi(thongTin.get("email_verified")));

        User user = taiKhoanGoogleService.timHoacTao(
                chuoi(thongTin.get("sub")),
                chuoi(thongTin.get("email")),
                emailDaXacMinh,
                chuoi(thongTin.get("name")),
                chuoi(thongTin.get("picture")));

        if (user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException("Tài khoản này đang bị khoá.");
        }

        return ResponseEntity.ok(tokenIssuerService.issueTokens(user));
    }

    /**
     * Nhờ Google xác minh token.
     *
     * <p>Gọi sang Google thay vì tự kiểm chữ ký tại chỗ: tự kiểm phải tải và
     * làm mới bộ khoá công khai của Google, xử lý xoay khoá, và một lỗi nhỏ ở
     * đó là chấp nhận token giả mà không ai biết. Đây là một lượt gọi mạng
     * cho mỗi lần đăng nhập — đổi lại phần dễ sai nhất do Google lo.
     */
    private Map<?, ?> hoiGoogle(String idToken) {
        try {
            Map<?, ?> res = restClient.get()
                    .uri(TOKENINFO + "?id_token={t}", idToken)
                    .retrieve()
                    .body(Map.class);
            if (res == null || res.get("sub") == null) {
                throw new InvalidCredentialsException("Token Google không hợp lệ.");
            }
            return res;
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            // Token hết hạn, sai chữ ký, hoặc Google từ chối — đều ra 4xx.
            log.warn("Không xác minh được ID token Google: {}", e.getMessage());
            throw new InvalidCredentialsException("Token Google không hợp lệ hoặc đã hết hạn.");
        }
    }

    private Set<String> audienceHopLe() {
        if (audienceChapNhan == null || audienceChapNhan.isBlank()) {
            // Chưa cấu hình thì KHÔNG chấp nhận gì cả. Mở toang khi thiếu cấu
            // hình là đúng kiểu lỗ hổng chỉ lộ ra sau khi đã bị lợi dụng.
            return Set.of();
        }
        return Set.of(audienceChapNhan.split("\\s*,\\s*"));
    }

    private static String chuoi(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }
}
