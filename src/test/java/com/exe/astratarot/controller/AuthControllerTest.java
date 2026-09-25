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
import com.exe.astratarot.service.AuthService;
import com.exe.astratarot.service.TurnstileService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cửa vào hệ thống. Lớp này trước đây phủ 0,0%.
 *
 * <p>Hai thứ ở đây quan trọng hơn phần còn lại của controller cộng lại:
 *
 * <ol>
 *   <li><b>Câu trả lời không được tiết lộ email có tồn tại hay không.</b> "Quên
 *       mật khẩu" và "gửi lại thư xác minh" đều trả cùng một câu dù email có
 *       thật hay không. Phân biệt hai trường hợp là biến trang này thành công
 *       cụ dò xem ai có tài khoản ở đây — và danh sách ấy bán được.
 *   <li><b>IP gửi sang Cloudflare phải là IP THẬT của người dùng.</b> Mọi
 *       request đều đi qua Worker rồi mới tới Render, nên
 *       {@code getRemoteAddr()} luôn là IP của Cloudflare. Gửi nhầm IP đó sang
 *       siteverify thì Cloudflare thấy không khớp với lúc sinh token và có thể
 *       từ chối người thật — người dùng bị chặn ở cửa mà không hiểu vì sao.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private TurnstileService turnstileService;
    @Mock private HttpServletRequest http;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, turnstileService);

        lenient().when(http.getHeader(anyString())).thenReturn(null);
        lenient().when(http.getRemoteAddr()).thenReturn("172.16.0.1");
        lenient().when(authService.register(any())).thenReturn(new RegisterResponse("khach@example.com", true));
        lenient().when(authService.login(any())).thenReturn(new AuthResponse(null, null, null, null, null, null, java.util.List.of(), "at", "rt", 3600L));
        lenient().when(authService.refresh(any())).thenReturn(new AuthResponse(null, null, null, null, null, null, java.util.List.of(), "at", "rt", 3600L));
    }

    /** IP mà controller đã gửi sang Cloudflare. */
    private String ipDaGui() {
        ArgumentCaptor<String> bat = ArgumentCaptor.forClass(String.class);
        verify(turnstileService).verify(any(), bat.capture());
        return bat.getValue();
    }

    // =====================================================================

    @Test
    @DisplayName("Đăng ký: kiểm Turnstile TRƯỚC khi chạm tới dịch vụ")
    void dangKyKiemTurnstileTruoc() {
        RegisterRequest r = new RegisterRequest("khach@example.com", "matkhaurieng", "Khách");
        when(http.getHeader("X-Turnstile-Token")).thenReturn("token-cua-trinh-duyet");

        var res = controller.register(r, http);

        // Kiểm sau khi đã tạo tài khoản là kiểm cho vui: bot đã có tài khoản
        // rồi. Thứ tự ở đây chính là toàn bộ tác dụng của Turnstile.
        assertAll(
                () -> assertEquals(200, res.getStatusCode().value()),
                () -> assertTrue(res.getBody().getMessage().contains("xác minh")));
        verify(turnstileService).verify(eq("token-cua-trinh-duyet"), anyString());
        verify(authService).register(r);
    }

    @Test
    @DisplayName("Đăng nhập cũng đi qua Turnstile")
    void dangNhapQuaTurnstile() {
        LoginRequest r = new LoginRequest("khach@example.com", "matkhaurieng");

        var res = controller.login(r, http);

        // Không chặn ở đây thì trang đăng nhập thành nơi dò mật khẩu hàng loạt.
        assertAll(
                () -> assertEquals("Đăng nhập thành công", res.getBody().getMessage()),
                () -> verify(turnstileService).verify(any(), anyString()),
                () -> verify(authService).login(r));
    }

    @Test
    @DisplayName("IP gửi sang Cloudflare lấy từ CF-Connecting-IP trước tiên")
    void ipTuCloudflare() {
        when(http.getHeader("CF-Connecting-IP")).thenReturn("203.0.113.7");
        when(http.getHeader("X-Forwarded-For")).thenReturn("198.51.100.9, 203.0.113.7");

        controller.login(new LoginRequest("khach@example.com", "matkhaurieng"), http);

        // Cloudflare tự đặt header này và người ngoài không giả được nó qua
        // Worker; X-Forwarded-For thì bất kỳ ai cũng gửi lên được.
        assertEquals("203.0.113.7", ipDaGui());
    }

    @Test
    @DisplayName("Không có CF-Connecting-IP thì lấy IP ĐẦU của X-Forwarded-For")
    void ipTuXForwardedFor() {
        when(http.getHeader("X-Forwarded-For")).thenReturn("  198.51.100.9 , 203.0.113.7 ");

        controller.login(new LoginRequest("khach@example.com", "matkhaurieng"), http);

        // Chuỗi này là đường đi qua từng proxy; IP của người dùng là cái ĐẦU
        // tiên. Lấy nhầm cái cuối là lấy IP của proxy gần nhất.
        assertEquals("198.51.100.9", ipDaGui());
    }

    @Test
    @DisplayName("Không có header nào thì rơi về địa chỉ kết nối trực tiếp")
    void ipRoiVeRemoteAddr() {
        controller.login(new LoginRequest("khach@example.com", "matkhaurieng"), http);

        assertEquals("172.16.0.1", ipDaGui());
    }

    @Test
    @DisplayName("Header rỗng cũng coi như không có")
    void headerRong() {
        when(http.getHeader("CF-Connecting-IP")).thenReturn("   ");
        when(http.getHeader("X-Forwarded-For")).thenReturn("  ");

        controller.login(new LoginRequest("khach@example.com", "matkhaurieng"), http);

        assertEquals("172.16.0.1", ipDaGui());
    }

    @Test
    @DisplayName("Xác minh email trả lại chính email vừa xác minh")
    void xacMinhEmail() {
        Map<String, String> than = new HashMap<>();
        than.put("token", "token-tu-hop-thu");
        when(authService.verifyEmail("token-tu-hop-thu")).thenReturn("khach@example.com");

        var res = controller.verifyEmail(than);

        // Giao diện hiện "đã xác minh khach@example.com" và điền sẵn ô đăng
        // nhập — nên email phải quay về, không chỉ một chữ OK.
        assertAll(
                () -> assertEquals("khach@example.com", res.getBody().getData().get("email")),
                () -> assertTrue(res.getBody().getMessage().contains("đăng nhập ngay")));
    }

    @Test
    @DisplayName("Quên mật khẩu trả câu CỐ ĐỊNH, không tiết lộ email có tồn tại")
    void quenMatKhauKhongTietLo() {
        ForgotPasswordRequest r = new ForgotPasswordRequest("khong-ton-tai@example.com");

        var res = controller.forgotPassword(r, http);

        // Phân biệt "đã gửi" với "email không tồn tại" là biến trang này thành
        // công cụ dò xem ai có tài khoản ở đây — và danh sách ấy bán được.
        assertAll(
                () -> assertTrue(res.getBody().getMessage().startsWith("Nếu email tồn tại")),
                () -> assertFalse(res.getBody().getMessage().contains("khong-ton-tai")),
                () -> assertTrue(res.getBody().isSuccess()));
        verify(authService).forgotPassword(r);
    }

    @Test
    @DisplayName("Gửi lại thư xác minh cũng trả câu cố định")
    void guiLaiThuXacMinh() {
        ResendVerificationRequest r = new ResendVerificationRequest("ai-do@example.com");

        var res = controller.resendVerification(r);

        assertAll(
                () -> assertTrue(res.getBody().getMessage().startsWith("Nếu email tồn tại")),
                () -> assertFalse(res.getBody().getMessage().contains("ai-do")));
        verify(authService).resendVerification(r);
    }

    @Test
    @DisplayName("Đặt lại mật khẩu xong thì bảo đăng nhập lại")
    void datLaiMatKhau() {
        ResetPasswordRequest r = new ResetPasswordRequest("token", "matkhaumoi");

        var res = controller.resetPassword(r);

        // Mọi phiên cũ đã bị thu hồi, nên phải nói rõ là cần đăng nhập lại —
        // không thì người dùng tưởng app hỏng.
        assertTrue(res.getBody().getMessage().contains("đăng nhập lại"));
        verify(authService).resetPassword(r);
    }

    @Test
    @DisplayName("Làm mới token và đăng xuất KHÔNG đi qua Turnstile")
    void lamMoiVaDangXuat() {
        RefreshTokenRequest lamMoi = new RefreshTokenRequest("refresh-token");
        LogoutRequest dangXuat = new LogoutRequest("refresh-token");

        controller.refresh(lamMoi);
        controller.logout(dangXuat);

        // Hai endpoint này được gọi tự động trong nền; bắt giải captcha ở đây
        // là đá người dùng ra giữa lúc họ đang dùng.
        assertAll(
                () -> verify(authService).refresh(lamMoi),
                () -> verify(authService).logout(dangXuat),
                () -> verify(turnstileService, org.mockito.Mockito.never())
                        .verify(any(), anyString()));
    }
}
