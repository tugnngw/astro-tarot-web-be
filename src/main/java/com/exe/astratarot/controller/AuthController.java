package com.exe.astratarot.controller;

import com.exe.astratarot.domain.dto.auth.AuthResponse;
import com.exe.astratarot.domain.dto.auth.ForgotPasswordRequest;
import com.exe.astratarot.domain.dto.auth.LoginRequest;
import com.exe.astratarot.domain.dto.auth.LogoutRequest;
import com.exe.astratarot.domain.dto.auth.RefreshTokenRequest;
import com.exe.astratarot.domain.dto.auth.RegisterRequest;
import com.exe.astratarot.domain.dto.auth.RegisterResponse;
import com.exe.astratarot.domain.dto.auth.ResendVerificationRequest;
import com.exe.astratarot.domain.dto.auth.ResetPasswordRequest;
import com.exe.astratarot.domain.dto.common.ApiResponse;
import com.exe.astratarot.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã gửi email xác minh. Vui lòng kiểm tra hộp thư để kích hoạt tài khoản.",
                authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công",
                authService.login(request)));
    }

    /**
     * Xác minh email từ link trong hộp thư. Public — người dùng bấm link khi
     * chưa đăng nhập được (tài khoản còn đang bị chặn vì chưa xác minh).
     */
    @PostMapping("/verify-email")
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyEmail(
            @RequestBody Map<String, String> body) {
        String email = authService.verifyEmail(body.get("token"));
        return ResponseEntity.ok(ApiResponse.success(
                "Xác minh email thành công. Bạn có thể đăng nhập ngay.",
                Map.of("email", email)));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request);
        // Thông điệp cố định, không tiết lộ email có tồn tại hay không.
        return ResponseEntity.ok(ApiResponse.success(
                "Nếu email tồn tại và chưa xác minh, chúng tôi đã gửi lại liên kết.", null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        // Luôn trả cùng một câu bất kể email có tồn tại hay không — nếu phân
        // biệt, trang này thành công cụ dò xem ai có tài khoản ở đây.
        return ResponseEntity.ok(ApiResponse.success(
                "Nếu email tồn tại, chúng tôi đã gửi liên kết đặt lại mật khẩu.", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Refreshed", authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success("Đã đăng xuất", null));
    }
}
