package com.exe.astratarot.security;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.AuthProvider;
import com.exe.astratarot.exception.InvalidCredentialsException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.OAuthExchangeCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Đăng nhập bằng Google. Lớp này trước đây phủ 0,0% — chưa một dòng nào được
 * kiểm, mà đây là một trong hai cửa vào hệ thống.
 *
 * <p>Ba hàng rào, cả ba đều là chuyện chiếm tài khoản chứ không phải hiển thị:
 *
 * <ol>
 *   <li><b>{@code email_verified} phải đúng.</b> Google có trả về tài khoản mà
 *       email chưa xác minh. Tin nó là cho người khác đăng ký một địa chỉ họ
 *       không sở hữu rồi bước vào tài khoản của chủ địa chỉ ấy.
 *   <li><b>Không tự gộp sang tài khoản mật khẩu.</b> Email trùng với một tài
 *       khoản đăng nhập bằng mật khẩu thì dừng lại. Gộp tự động nghĩa là ai
 *       đăng ký Google bằng email của người khác cũng vào được tài khoản của
 *       họ.
 *   <li><b>Mã trao đổi một lần, không phải token trên URL.</b> Token nằm trong
 *       URL thì nó vào lịch sử trình duyệt, vào log máy chủ, và vào header
 *       Referer gửi sang trang khác.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2LoginSuccessHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private OAuthExchangeCodeService exchangeCodeService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;

    private OAuth2LoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(userRepository, exchangeCodeService);
        ReflectionTestUtils.setField(handler, "frontendUrl", "https://astrotarot.date");

        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        lenient().when(userRepository.findByAuthProviderAndProviderIdAndDeletedAtIsNull(any(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(anyString()))
                .thenReturn(false);
        lenient().when(exchangeCodeService.createCode(any())).thenReturn("ma-trao-doi");
    }

    /**
     * Hồ sơ Google chuẩn; từng phép kiểm sửa lại thuộc tính nó cần.
     *
     * <p>Không dùng {@code DefaultOAuth2User}: hàm dựng của nó tự từ chối khi
     * thiếu thuộc tính khoá, nên phép kiểm "thiếu sub" sẽ chết ở khâu dựng dữ
     * liệu thay vì chạm tới hàng rào của chính handler — tức là kiểm nhầm thứ.
     */
    private OAuth2User hoSoGoogle(Map<String, Object> ghiDe) {
        Map<String, Object> thuocTinh = new HashMap<>(Map.of(
                "sub", "google-123",
                "email", "khach@gmail.com",
                "email_verified", true,
                "name", "Trần Duy Đạt",
                "picture", "https://lh3.googleusercontent.com/abc"));
        ghiDe.forEach((k, v) -> {
            if (v == null) {
                thuocTinh.remove(k);
            } else {
                thuocTinh.put(k, v);
            }
        });
        OAuth2User hoSo = org.mockito.Mockito.mock(OAuth2User.class);
        lenient().when(hoSo.getAttributes()).thenReturn(thuocTinh);
        lenient().when(hoSo.getAttribute(anyString()))
                .thenAnswer(inv -> thuocTinh.get(inv.<String>getArgument(0)));
        lenient().when(hoSo.getName()).thenReturn(String.valueOf(thuocTinh.get("sub")));
        return hoSo;
    }

    private void dangNhap(Map<String, Object> ghiDe) throws Exception {
        // Dựng hồ sơ TRƯỚC khi stub: hoSoGoogle() tự stub bên trong nó, và
        // Mockito không cho bắt đầu một stubbing khi một stubbing khác chưa
        // kết thúc.
        OAuth2User hoSo = hoSoGoogle(ghiDe);
        when(authentication.getPrincipal()).thenReturn(hoSo);
        handler.onAuthenticationSuccess(request, response, authentication);
    }

    private User daLuu() {
        ArgumentCaptor<User> bat = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.atLeastOnce()).save(bat.capture());
        return bat.getValue();
    }

    // =====================================================================

    @Test
    @DisplayName("Người mới: tạo tài khoản Google, email coi như đã xác minh")
    void nguoiMoi() throws Exception {
        dangNhap(Map.of());

        User u = daLuu();
        assertAll(
                () -> assertEquals("khach@gmail.com", u.getEmail()),
                () -> assertEquals("Trần Duy Đạt", u.getFullName()),
                () -> assertEquals(AuthProvider.GOOGLE, u.getAuthProvider()),
                () -> assertEquals("google-123", u.getProviderId()),
                () -> assertTrue(u.getEmailVerified()),
                () -> assertNotNull(u.getEmailVerifiedAt()),
                () -> assertNotNull(u.getLastLoginAt()));
    }

    @Test
    @DisplayName("Tên đăng nhập sinh từ phần trước @, ký tự lạ thành gạch dưới")
    void tenDangNhapTuEmail() throws Exception {
        dangNhap(Map.of("email", "Duy.Dat+tarot@Gmail.com"));

        assertEquals("duy_dat_tarot", daLuu().getUsername());
    }

    @Test
    @DisplayName("Tên đăng nhập TRÙNG thì thêm đuôi ngẫu nhiên")
    void tenDangNhapTrung() throws Exception {
        when(userRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull("khach"))
                .thenReturn(true);

        dangNhap(Map.of());

        // Cột username là duy nhất; để trùng thì lỗi hiện ra là một
        // DataIntegrityViolation ngay giữa luồng đăng nhập.
        assertAll(
                () -> assertTrue(daLuu().getUsername().startsWith("khach_")),
                () -> assertTrue(daLuu().getUsername().length() > "khach".length()));
    }

    @Test
    @DisplayName("Email chưa được Google xác minh thì TỪ CHỐI")
    void emailChuaXacMinh() {
        // Google có trả về tài khoản mà email chưa xác minh. Tin nó là cho
        // người khác đăng ký một địa chỉ họ không sở hữu rồi bước vào tài khoản
        // của chủ địa chỉ ấy.
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> dangNhap(Map.of("email_verified", false))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> dangNhap(new HashMap<>(Map.of("email_verified", "khong-phai-boolean")))));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Thiếu sub hoặc thiếu email thì từ chối")
    void thieuThuocTinhBatBuoc() {
        Map<String, Object> khongSub = new HashMap<>();
        khongSub.put("sub", null);
        Map<String, Object> khongEmail = new HashMap<>();
        khongEmail.put("email", null);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> dangNhap(khongSub)),
                () -> assertThrows(IllegalArgumentException.class, () -> dangNhap(khongEmail)));
    }

    @Test
    @DisplayName("Không có tên thì lấy email làm tên hiển thị")
    void khongCoTen() throws Exception {
        Map<String, Object> khongTen = new HashMap<>();
        khongTen.put("name", null);

        dangNhap(khongTen);

        // Tên trống hiện ra khắp nơi: thẻ Reader, phòng chat, lịch hẹn.
        assertEquals("khach@gmail.com", daLuu().getFullName());
    }

    @Test
    @DisplayName("Đăng nhập LẠI: dùng lại tài khoản cũ, không tạo tài khoản thứ hai")
    void dangNhapLai() throws Exception {
        User cu = new User();
        cu.setId(UUID.randomUUID());
        cu.setEmail("khach@gmail.com");
        cu.setFullName("Tên tôi tự đặt");
        cu.setAvatar("https://cdn/anh-toi-tu-tai.jpg");
        cu.setAuthProvider(AuthProvider.GOOGLE);
        cu.setProviderId("google-123");
        when(userRepository.findByAuthProviderAndProviderIdAndDeletedAtIsNull(
                AuthProvider.GOOGLE, "google-123")).thenReturn(Optional.of(cu));

        dangNhap(Map.of());

        // Tên và ảnh người dùng TỰ ĐẶT phải thắng dữ liệu từ Google. Ghi đè mỗi
        // lần đăng nhập là xoá lựa chọn của họ, lặp đi lặp lại.
        assertAll(
                () -> assertEquals(cu.getId(), daLuu().getId()),
                () -> assertEquals("Tên tôi tự đặt", cu.getFullName()),
                () -> assertEquals("https://cdn/anh-toi-tu-tai.jpg", cu.getAvatar()));
    }

    @Test
    @DisplayName("Tài khoản cũ chưa có tên hay ảnh thì lấy từ Google")
    void taiKhoanCuChuaCoTen() throws Exception {
        User cu = new User();
        cu.setId(UUID.randomUUID());
        cu.setEmail("khach@gmail.com");
        cu.setFullName("  ");
        cu.setAvatar(null);
        cu.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findByAuthProviderAndProviderIdAndDeletedAtIsNull(
                AuthProvider.GOOGLE, "google-123")).thenReturn(Optional.of(cu));

        dangNhap(Map.of());

        assertAll(
                () -> assertEquals("Trần Duy Đạt", cu.getFullName()),
                () -> assertEquals("https://lh3.googleusercontent.com/abc", cu.getAvatar()));
    }

    @Test
    @DisplayName("Email trùng tài khoản đăng nhập bằng MẬT KHẨU thì từ chối gộp")
    void emailTrungTaiKhoanMatKhau() {
        User taiKhoanMatKhau = new User();
        taiKhoanMatKhau.setId(UUID.randomUUID());
        taiKhoanMatKhau.setEmail("khach@gmail.com");
        taiKhoanMatKhau.setAuthProvider(AuthProvider.LOCAL);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("khach@gmail.com"))
                .thenReturn(Optional.of(taiKhoanMatKhau));

        // Gộp tự động nghĩa là ai đăng ký Google bằng email của người khác cũng
        // vào được tài khoản của họ — không cần biết mật khẩu.
        assertThrows(InvalidCredentialsException.class, () -> dangNhap(Map.of()));
    }

    @Test
    @DisplayName("Tài khoản Google cũ đổi sub thì nối lại theo email")
    void taiKhoanGoogleCuDoiSub() throws Exception {
        User cu = new User();
        cu.setId(UUID.randomUUID());
        cu.setEmail("khach@gmail.com");
        cu.setAuthProvider(AuthProvider.GOOGLE);
        cu.setProviderId("google-cu");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("khach@gmail.com"))
                .thenReturn(Optional.of(cu));

        dangNhap(Map.of());

        assertEquals("google-123", cu.getProviderId());
    }

    @Test
    @DisplayName("Đăng nhập Google DỌN mọi token xác thực email còn treo")
    void donTokenXacThucConTreo() throws Exception {
        User cu = new User();
        cu.setId(UUID.randomUUID());
        cu.setEmail("khach@gmail.com");
        cu.setAuthProvider(AuthProvider.GOOGLE);
        cu.setPendingEmail("dinh-doi@example.com");
        cu.setEmailVerificationToken("mot-ban-bam");
        cu.setEmailVerificationExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(userRepository.findByAuthProviderAndProviderIdAndDeletedAtIsNull(
                AuthProvider.GOOGLE, "google-123")).thenReturn(Optional.of(cu));

        dangNhap(Map.of());

        // Email vừa được Google chứng thực, nên một yêu cầu đổi email đang treo
        // trở thành vô nghĩa — để lại là một đường dẫn còn dùng được trong hòm
        // thư, đổi email của tài khoản này sang địa chỉ khác.
        assertAll(
                () -> assertNull(cu.getPendingEmail()),
                () -> assertNull(cu.getEmailVerificationToken()),
                () -> assertNull(cu.getEmailVerificationExpiresAt()));
    }

    @Test
    @DisplayName("Chuyển hướng mang MÃ TRAO ĐỔI, không mang token")
    void chuyenHuongMangMaTraoDoi() throws Exception {
        dangNhap(Map.of());

        ArgumentCaptor<String> bat = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(bat.capture());
        String url = bat.getValue();
        // Token nằm trong URL thì nó vào lịch sử trình duyệt, vào log máy chủ,
        // và vào header Referer gửi sang trang khác.
        assertAll(
                () -> assertTrue(url.startsWith("https://astrotarot.date/oauth-success")),
                () -> assertTrue(url.contains("code=ma-trao-doi")),
                () -> assertFalse(url.contains("token")),
                () -> assertFalse(url.contains("eyJ")));
    }
}
