package com.exe.astratarot.security;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.AuthProvider;
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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /** Nơi handler đưa người dùng về khi hỏng — cùng chỗ với failureHandler. */
    private static final String TRANG_LOI = "https://astrotarot.date/?dang-nhap=google-loi";

    @BeforeEach
    void setUp() {
        // Dùng bản THẬT của dịch vụ nối tài khoản, không mock: quy tắc chống
        // tài khoản trùng nằm trong đó, và mock nó đi là những bài kiểm dưới
        // đây chỉ còn kiểm phần chuyển hướng.
        handler = new OAuth2LoginSuccessHandler(
                new com.exe.astratarot.service.impl.TaiKhoanGoogleServiceImpl(userRepository),
                userRepository,
                exchangeCodeService);
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
    @DisplayName("Email chưa được Google xác minh thì TỪ CHỐI, và không ném ra ngoài")
    void emailChuaXacMinh() throws Exception {
        // Google có trả về tài khoản mà email chưa xác minh. Tin nó là cho
        // người khác đăng ký một địa chỉ họ không sở hữu rồi bước vào tài khoản
        // của chủ địa chỉ ấy.
        dangNhap(Map.of("email_verified", false));
        dangNhap(new HashMap<>(Map.of("email_verified", "khong-phai-boolean")));

        verify(userRepository, never()).save(any());
        verify(response, times(2)).sendRedirect(TRANG_LOI);
    }

    @Test
    @DisplayName("Thiếu sub hoặc thiếu email thì từ chối, và không ném ra ngoài")
    void thieuThuocTinhBatBuoc() throws Exception {
        Map<String, Object> khongSub = new HashMap<>();
        khongSub.put("sub", null);
        Map<String, Object> khongEmail = new HashMap<>();
        khongEmail.put("email", null);

        dangNhap(khongSub);
        dangNhap(khongEmail);

        verify(userRepository, never()).save(any());
        verify(response, times(2)).sendRedirect(TRANG_LOI);
    }

    /**
     * Chỗ này từng trả HTTP 500 thật.
     *
     * <p>Handler chạy BÊN TRONG chuỗi bộ lọc, sau khi xác thực đã xong, nên
     * {@code failureHandler} của {@code oauth2Login} không với tới. Lỗi ném ở
     * đây chui thẳng lên Tomcat: người dùng nhận trang "HTTP Status 500" trần
     * trên tên miền api, không có đường quay lại.
     */
    @Test
    @DisplayName("Lỗi bất ngờ cũng chuyển hướng, KHÔNG để 500 lọt ra")
    void loiBatNgoKhongThanh500() throws Exception {
        when(exchangeCodeService.createCode(any()))
                .thenThrow(new IllegalStateException("kho ma trao doi hong"));

        assertDoesNotThrow(() -> dangNhap(Map.of()));
        verify(response).sendRedirect(TRANG_LOI);
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

    /**
     * Bản trước TỪ CHỐI nhánh này, và vì nó ném từ bên trong handler nên mọi
     * lần bấm nút Google đều ra HTTP 500. Tới 27/10/2026 cả 12 tài khoản trên
     * production đều là {@code LOCAL}, tức là nhánh ấy bắt đúng mọi người.
     *
     * <p>Nối được là an toàn vì {@code timHoacTao} đã bắt buộc
     * {@code email_verified}: Google xác nhận người đang đăng nhập làm chủ
     * hòm thư. Và nó không mở cửa nào mới — hệ thống vốn đã có đặt lại mật
     * khẩu qua chính hòm thư ấy.
     */
    @Test
    @DisplayName("Email trùng tài khoản MẬT KHẨU thì NỐI vào, giữ nguyên tài khoản cũ")
    void emailTrungTaiKhoanMatKhau() throws Exception {
        UUID idCu = UUID.randomUUID();
        User taiKhoanMatKhau = new User();
        taiKhoanMatKhau.setId(idCu);
        taiKhoanMatKhau.setEmail("khach@gmail.com");
        taiKhoanMatKhau.setFullName("Tên tự đặt");
        taiKhoanMatKhau.setPasswordHash("bam-mat-khau-cu");
        taiKhoanMatKhau.setAuthProvider(AuthProvider.LOCAL);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("khach@gmail.com"))
                .thenReturn(Optional.of(taiKhoanMatKhau));

        dangNhap(Map.of());

        User u = daLuu();
        assertAll(
                () -> assertEquals(idCu, u.getId(), "phải là tài khoản CŨ, không phải tài khoản thứ hai"),
                () -> assertEquals(AuthProvider.GOOGLE, u.getAuthProvider()),
                () -> assertEquals("google-123", u.getProviderId()),
                // Giữ mật khẩu: ai quen đăng nhập bằng mật khẩu vẫn vào được.
                () -> assertEquals("bam-mat-khau-cu", u.getPasswordHash()),
                // Và không đè lên tên người ta tự đặt.
                () -> assertEquals("Tên tự đặt", u.getFullName()));
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
