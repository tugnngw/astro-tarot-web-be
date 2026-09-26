package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.auth.AuthResponse;
import com.exe.astratarot.domain.dto.auth.ForgotPasswordRequest;
import com.exe.astratarot.domain.dto.auth.LoginRequest;
import com.exe.astratarot.domain.dto.auth.LogoutRequest;
import com.exe.astratarot.domain.dto.auth.RefreshTokenRequest;
import com.exe.astratarot.domain.dto.auth.RegisterRequest;
import com.exe.astratarot.domain.dto.auth.ResendVerificationRequest;
import com.exe.astratarot.domain.dto.auth.ResetPasswordRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserSession;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.exception.AccountDeactivatedException;
import com.exe.astratarot.exception.EmailAlreadyExistsException;
import com.exe.astratarot.exception.EmailNotVerifiedException;
import com.exe.astratarot.exception.InvalidCredentialsException;
import com.exe.astratarot.exception.InvalidTokenException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.service.EmailService;
import com.exe.astratarot.service.TokenIssuerService;
import com.exe.astratarot.service.UsernameGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Lớp này trước đây có độ phủ 0,8% — tức là gần như toàn bộ đường đăng nhập,
 * đăng ký và đặt lại mật khẩu chưa từng được kiểm tự động lần nào.
 *
 * <p>Bộ test dưới đây không nhằm làm đẹp con số. Nó khoá lại những bất biến mà
 * nếu vỡ thì hậu quả là mất tài khoản hoặc rò thông tin:
 *
 * <ul>
 *   <li><b>Token chỉ lưu bản băm.</b> Rò cơ sở dữ liệu mà token nằm dạng thô
 *       thì kẻ đọc được bảng có thể đặt lại mật khẩu của bất kỳ ai.</li>
 *   <li><b>Quên mật khẩu im lặng với email lạ.</b> Phân biệt "không tồn tại"
 *       với "đã gửi" là biến ô nhập thành công cụ dò xem ai có tài khoản.</li>
 *   <li><b>Đặt lại mật khẩu thu hồi mọi phiên.</b> Người ta đổi mật khẩu vì
 *       nghi bị chiếm tài khoản; không đá thiết bị lạ ra thì việc đổi vô
 *       nghĩa.</li>
 *   <li><b>Refresh token dùng một lần.</b> Không thu hồi phiên cũ thì một
 *       token bị chép lại dùng được mãi.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private org.springframework.security.authentication.AuthenticationManager authenticationManager;
    @Mock private TokenIssuerService tokenIssuerService;
    @Mock private EmailService emailService;
    @Mock private UsernameGenerator usernameGenerator;

    private AuthServiceImpl service;

    private static final String EMAIL = "nguoidung@example.com";

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userRepository, userSessionRepository, passwordEncoder,
                authenticationManager, tokenIssuerService, emailService,
                usernameGenerator);
        // buildLink đọc URL giao diện từ cấu hình; không đặt thì link ra "null/...".
        ReflectionTestUtils.setField(service, "frontendUrl", "https://astrotarot.date");

        lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private User nguoiDung() {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(EMAIL);
        u.setFullName("Người Dùng");
        u.setEmailVerified(true);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    // =====================================================================
    @Nested
    @DisplayName("Đăng ký")
    class DangKy {

        @Test
        @DisplayName("Email đã có thì từ chối, không tạo thêm tài khoản")
        void emailTrungThiTuChoi() {
            when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(true);

            assertThrows(EmailAlreadyExistsException.class,
                    () -> service.register(new RegisterRequest(EMAIL, "matkhau123", "Tên")));

            verify(userRepository, never()).save(any());
            verify(emailService, never()).sendEmailVerification(anyString(), anyString());
        }

        @Test
        @DisplayName("Email được chuẩn hoá về chữ thường và cắt khoảng trắng")
        void chuanHoaEmail() {
            when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(false);
            when(usernameGenerator.fromEmail(anyString())).thenReturn("nguoidung_1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");

            var kq = service.register(
                    new RegisterRequest("  NGUOIDUNG@Example.COM  ", "matkhau123", " Tên "));

            // Không chuẩn hoá thì cùng một người đăng ký được hai lần chỉ bằng
            // cách đổi hoa thường, và lần đăng nhập sau không tìm thấy tài khoản.
            assertEquals(EMAIL, kq.email());
            ArgumentCaptor<User> bat = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(bat.capture());
            assertEquals(EMAIL, bat.getValue().getEmail());
            assertEquals("Tên", bat.getValue().getFullName());
        }

        @Test
        @DisplayName("Chỉ lưu BẢN BĂM của token, còn link gửi đi mang token gốc")
        void chiLuuBanBamCuaToken() {
            when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(false);
            when(usernameGenerator.fromEmail(anyString())).thenReturn("nguoidung_1");
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");

            service.register(new RegisterRequest(EMAIL, "matkhau123", "Tên"));

            ArgumentCaptor<User> bat = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(bat.capture());
            ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendEmailVerification(anyString(), link.capture());

            String daLuu = bat.getValue().getEmailVerificationToken();
            assertNotNull(daLuu);
            assertAll(
                    () -> assertFalse(link.getValue().contains(daLuu),
                            "Token trong cơ sở dữ liệu KHÔNG được trùng token trong "
                                    + "link. Lưu dạng thô thì ai đọc được bảng users "
                                    + "cũng đặt lại được mật khẩu của người khác."),
                    () -> assertTrue(bat.getValue().getEmailVerificationExpiresAt()
                                    .isAfter(Instant.now()),
                            "Token phải có hạn dùng"));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Đăng nhập")
    class DangNhap {

        @Test
        @DisplayName("Chưa xác minh email thì báo đúng lý do, không báo sai mật khẩu")
        void chuaXacMinhThiBaoDungLyDo() {
            User u = nguoiDung();
            u.setEmailVerified(false);
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(Optional.of(u));

            assertThrows(EmailNotVerifiedException.class,
                    () -> service.login(new LoginRequest(EMAIL, "matkhau123")));

            // Kiểm xác minh phải chạy TRƯỚC authenticationManager. Để sau thì
            // người chưa xác minh gõ sai mật khẩu sẽ nhận thông báo sai lệch.
            verify(authenticationManager, never()).authenticate(any());
        }

        @Test
        @DisplayName("Email không tồn tại và mật khẩu sai trả CÙNG một loại lỗi")
        void khongLoNguoiDungCoTonTaiHayKhong() {
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("khongco@example.com"))
                    .thenReturn(Optional.empty());

            var khongTonTai = assertThrows(InvalidCredentialsException.class,
                    () -> service.login(new LoginRequest("khongco@example.com", "x")));

            User u = nguoiDung();
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(Optional.of(u));
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("sai"));

            var saiMatKhau = assertThrows(InvalidCredentialsException.class,
                    () -> service.login(new LoginRequest(EMAIL, "sai")));

            // Hai câu phải giống nhau. Khác nhau là nói cho người dò biết email
            // nào đã đăng ký ở đây.
            assertEquals(khongTonTai.getMessage(), saiMatKhau.getMessage());
        }

        @Test
        @DisplayName("Tài khoản bị vô hiệu hoá báo đúng loại lỗi riêng")
        void taiKhoanBiKhoa() {
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(Optional.of(nguoiDung()));
            when(authenticationManager.authenticate(any()))
                    .thenThrow(new DisabledException("khoá"));

            assertThrows(AccountDeactivatedException.class,
                    () -> service.login(new LoginRequest(EMAIL, "matkhau123")));
        }

        @Test
        @DisplayName("Đăng nhập thành công thì ghi lastLoginAt và phát token")
        void dangNhapThanhCong() {
            User u = nguoiDung();
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(Optional.of(u));
            when(authenticationManager.authenticate(any()))
                    .thenReturn(org.mockito.Mockito.mock(Authentication.class));
            AuthResponse mong = org.mockito.Mockito.mock(AuthResponse.class);
            when(tokenIssuerService.issueTokens(u)).thenReturn(mong);

            var kq = service.login(new LoginRequest(EMAIL, "matkhau123"));

            assertEquals(mong, kq);
            assertNotNull(u.getLastLoginAt());
        }

        @Test
        @DisplayName("Email rỗng bị chặn ngay, không đi tra cơ sở dữ liệu")
        void emailRongBiChan() {
            assertThrows(InvalidCredentialsException.class,
                    () -> service.login(new LoginRequest("   ", "x")));
            verify(userRepository, never())
                    .findByEmailIgnoreCaseAndDeletedAtIsNull(anyString());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Xác minh email")
    class XacMinh {

        @Test
        @DisplayName("Token không khớp thì từ chối")
        void tokenSai() {
            when(userRepository.findByEmailVerificationToken(anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(InvalidTokenException.class, () -> service.verifyEmail("bua"));
        }

        @Test
        @DisplayName("Token hết hạn thì từ chối, không đánh dấu đã xác minh")
        void tokenHetHan() {
            User u = nguoiDung();
            u.setEmailVerified(false);
            u.setEmailVerificationExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
            when(userRepository.findByEmailVerificationToken(anyString()))
                    .thenReturn(Optional.of(u));

            assertThrows(InvalidTokenException.class, () -> service.verifyEmail("token"));
            assertFalse(Boolean.TRUE.equals(u.getEmailVerified()));
        }

        @Test
        @DisplayName("Xác minh xong thì xoá token — link chỉ dùng được một lần")
        void linkChiDungMotLan() {
            User u = nguoiDung();
            u.setEmailVerified(false);
            u.setEmailVerificationExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
            when(userRepository.findByEmailVerificationToken(anyString()))
                    .thenReturn(Optional.of(u));

            service.verifyEmail("token");

            assertAll(
                    () -> assertTrue(u.getEmailVerified()),
                    () -> assertNotNull(u.getEmailVerifiedAt()),
                    () -> assertNull(u.getEmailVerificationToken(),
                            "Không xoá token thì cùng một link dùng lại được mãi"),
                    () -> assertNull(u.getEmailVerificationExpiresAt()));
        }

        @Test
        @DisplayName("Luồng đổi email: pendingEmail được chuyển thành email chính")
        void doiEmailThiChuyenPendingSangChinh() {
            User u = nguoiDung();
            u.setEmailVerified(false);
            u.setEmailVerificationExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
            u.setPendingEmail("moi@example.com");
            when(userRepository.findByEmailVerificationToken(anyString()))
                    .thenReturn(Optional.of(u));
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("moi@example.com"))
                    .thenReturn(Optional.empty());

            String kq = service.verifyEmail("token");

            // Chỉ đánh dấu đã xác minh mà quên chuyển thì người dùng bấm link
            // xong vẫn giữ email cũ, và không hiểu vì sao.
            assertAll(
                    () -> assertEquals("moi@example.com", kq),
                    () -> assertEquals("moi@example.com", u.getEmail()),
                    () -> assertNull(u.getPendingEmail()));
        }

        @Test
        @DisplayName("Email mới đã thuộc người khác thì từ chối, giữ nguyên email cũ")
        void emailMoiDaCoNguoiKhacDung() {
            User u = nguoiDung();
            u.setEmailVerified(false);
            u.setEmailVerificationExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
            u.setPendingEmail("moi@example.com");

            User nguoiKhac = nguoiDung();
            when(userRepository.findByEmailVerificationToken(anyString()))
                    .thenReturn(Optional.of(u));
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("moi@example.com"))
                    .thenReturn(Optional.of(nguoiKhac));

            assertThrows(EmailAlreadyExistsException.class,
                    () -> service.verifyEmail("token"));
            assertEquals(EMAIL, u.getEmail());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Gửi lại thư xác minh")
    class GuiLai {

        @Test
        @DisplayName("Email lạ thì im lặng — không lộ ai có tài khoản")
        void emailLaThiImLang() {
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(anyString()))
                    .thenReturn(Optional.empty());

            service.resendVerification(new ResendVerificationRequest("la@example.com"));

            verify(emailService, never()).sendEmailVerification(anyString(), anyString());
        }

        @Test
        @DisplayName("Đã xác minh rồi thì không gửi lại")
        void daXacMinhThiThoi() {
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(Optional.of(nguoiDung()));

            service.resendVerification(new ResendVerificationRequest(EMAIL));

            verify(emailService, never()).sendEmailVerification(anyString(), anyString());
        }

        @Test
        @DisplayName("Chưa xác minh thì cấp token MỚI, không dùng lại token cũ")
        void capTokenMoi() {
            User u = nguoiDung();
            u.setEmailVerified(false);
            u.setEmailVerificationToken("bam-cu");
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(Optional.of(u));

            service.resendVerification(new ResendVerificationRequest(EMAIL));

            assertNotEquals("bam-cu", u.getEmailVerificationToken());
            verify(emailService).sendEmailVerification(anyString(), anyString());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Quên và đặt lại mật khẩu")
    class MatKhau {

        @Test
        @DisplayName("Email lạ: KHÔNG ném lỗi và KHÔNG gửi thư")
        void emailLaKhongNemLoi() {
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(anyString()))
                    .thenReturn(Optional.empty());

            // Ném lỗi ở đây là biến ô quên mật khẩu thành công cụ dò tài khoản:
            // người dò chỉ cần xem có lỗi hay không.
            service.forgotPassword(new ForgotPasswordRequest("la@example.com"));

            verify(emailService, never()).sendPasswordReset(anyString(), anyString());
        }

        @Test
        @DisplayName("Token đặt lại cũng chỉ lưu bản băm")
        void tokenDatLaiCungBam() {
            User u = nguoiDung();
            when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL))
                    .thenReturn(Optional.of(u));

            service.forgotPassword(new ForgotPasswordRequest(EMAIL));

            ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
            verify(emailService).sendPasswordReset(anyString(), link.capture());
            assertNotNull(u.getPasswordResetToken());
            assertFalse(link.getValue().contains(u.getPasswordResetToken()));
        }

        @Test
        @DisplayName("Token đặt lại hết hạn thì từ chối")
        void tokenDatLaiHetHan() {
            User u = nguoiDung();
            u.setPasswordResetExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
            when(userRepository.findByPasswordResetToken(anyString()))
                    .thenReturn(Optional.of(u));

            assertThrows(InvalidTokenException.class,
                    () -> service.resetPassword(new ResetPasswordRequest("t", "matkhaumoi1")));
        }

        @Test
        @DisplayName("Đặt lại mật khẩu THU HỒI mọi phiên đang mở")
        void datLaiThiThuHoiMoiPhien() {
            User u = nguoiDung();
            u.setPasswordResetExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
            when(userRepository.findByPasswordResetToken(anyString()))
                    .thenReturn(Optional.of(u));
            when(passwordEncoder.encode("matkhaumoi1")).thenReturn("$2a$moi");

            UserSession p1 = new UserSession();
            UserSession p2 = new UserSession();
            when(userSessionRepository.findByUserIdAndRevokedFalse(u.getId()))
                    .thenReturn(List.of(p1, p2));

            service.resetPassword(new ResetPasswordRequest("t", "matkhaumoi1"));

            // Người ta đổi mật khẩu vì nghi bị chiếm tài khoản. Không đá thiết
            // bị lạ ra thì việc đổi chẳng giải quyết được gì.
            assertAll(
                    () -> assertEquals(Boolean.TRUE, p1.getRevoked()),
                    () -> assertEquals(Boolean.TRUE, p2.getRevoked()),
                    () -> assertEquals("$2a$moi", u.getPasswordHash()),
                    () -> assertNull(u.getPasswordResetToken()));
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("Phiên đăng nhập")
    class Phien {

        private UserSession phien(User u, Instant hetHan) {
            UserSession s = new UserSession();
            s.setUser(u);
            s.setExpiredAt(hetHan);
            return s;
        }

        @Test
        @DisplayName("Refresh token lạ thì từ chối")
        void tokenLa() {
            when(userSessionRepository.findByRefreshTokenHashAndRevokedFalse(anyString()))
                    .thenReturn(Optional.empty());
            assertThrows(InvalidTokenException.class,
                    () -> service.refresh(new RefreshTokenRequest("bua")));
        }

        @Test
        @DisplayName("Refresh token hết hạn thì thu hồi luôn phiên đó")
        void tokenHetHanThiThuHoi() {
            UserSession s = phien(nguoiDung(), Instant.now().minusSeconds(60));
            when(userSessionRepository.findByRefreshTokenHashAndRevokedFalse(anyString()))
                    .thenReturn(Optional.of(s));

            assertThrows(InvalidTokenException.class,
                    () -> service.refresh(new RefreshTokenRequest("t")));
            assertEquals(Boolean.TRUE, s.getRevoked());
        }

        @Test
        @DisplayName("Tài khoản đã vô hiệu hoá thì không làm mới được")
        void taiKhoanVoHieuHoa() {
            User u = nguoiDung();
            u.setStatus(UserStatus.INACTIVE);
            UserSession s = phien(u, Instant.now().plusSeconds(600));
            when(userSessionRepository.findByRefreshTokenHashAndRevokedFalse(anyString()))
                    .thenReturn(Optional.of(s));

            assertThrows(AccountDeactivatedException.class,
                    () -> service.refresh(new RefreshTokenRequest("t")));
            assertEquals(Boolean.TRUE, s.getRevoked());
        }

        @Test
        @DisplayName("Làm mới thành công thì phiên CŨ bị thu hồi — token dùng một lần")
        void lamMoiThiThuHoiPhienCu() {
            User u = nguoiDung();
            UserSession s = phien(u, Instant.now().plusSeconds(600));
            when(userSessionRepository.findByRefreshTokenHashAndRevokedFalse(anyString()))
                    .thenReturn(Optional.of(s));
            AuthResponse moi = org.mockito.Mockito.mock(AuthResponse.class);
            when(tokenIssuerService.issueTokens(u)).thenReturn(moi);

            var kq = service.refresh(new RefreshTokenRequest("t"));

            // Không thu hồi thì một refresh token bị chép lại dùng được mãi mãi.
            assertAll(
                    () -> assertEquals(moi, kq),
                    () -> assertEquals(Boolean.TRUE, s.getRevoked()));
        }

        @Test
        @DisplayName("Đăng xuất thu hồi phiên; token lạ thì im lặng bỏ qua")
        void dangXuat() {
            UserSession s = phien(nguoiDung(), Instant.now().plusSeconds(600));
            when(userSessionRepository.findByRefreshTokenHashAndRevokedFalse(anyString()))
                    .thenReturn(Optional.of(s));

            service.logout(new LogoutRequest("t"));
            assertEquals(Boolean.TRUE, s.getRevoked());

            when(userSessionRepository.findByRefreshTokenHashAndRevokedFalse(anyString()))
                    .thenReturn(Optional.empty());
            // Đăng xuất bằng token đã hết hiệu lực không được ném lỗi: người
            // dùng bấm đăng xuất là họ muốn rời máy này, đừng cản.
            service.logout(new LogoutRequest("da-het"));
        }
    }
}
