package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.exception.EmailAlreadyExistsException;
import com.exe.astratarot.exception.InvalidTokenException;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.service.EmailService;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Xác thực email. Lớp này trước đây phủ 0,0%.
 *
 * <p>Điểm quan trọng nhất và dễ bị làm hỏng nhất: <b>chỉ BẢN BĂM của token nằm
 * trong cơ sở dữ liệu</b>. Token gửi qua email là bản thô, và nó không bao giờ
 * được lưu. Nếu lưu bản thô thì bất kỳ ai đọc được bảng users — một bản sao lưu
 * lọt ra, một lỗi SQL injection, một nhân viên có quyền đọc — đều đổi được
 * email của mọi tài khoản, và đổi email là bước đầu của chiếm tài khoản.
 *
 * <p>Điểm thứ hai: email mới chỉ được ghi vào {@code pendingEmail}, không ghi
 * đè {@code email} cho tới khi bấm xác nhận. Ghi đè sớm là người ta gõ nhầm một
 * chữ rồi mất luôn đường đăng nhập.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    private EmailVerificationServiceImpl service;
    private User nguoiDung;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationServiceImpl(userRepository, emailService);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://astrotarot.date");

        nguoiDung = new User();
        nguoiDung.setId(UUID.randomUUID());
        nguoiDung.setEmail("cu@example.com");
        nguoiDung.setEmailVerified(false);

        lenient().when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Gửi yêu cầu rồi moi lại token THÔ từ đường dẫn trong email. */
    private String guiVaLayTokenTho(String email) {
        service.sendVerification(nguoiDung, email);
        ArgumentCaptor<String> bat = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailVerification(anyString(), bat.capture());
        String link = bat.getValue();
        return link.substring(link.indexOf("token=") + 6);
    }

    // =====================================================================

    @Test
    @DisplayName("Chỉ BẢN BĂM của token nằm trong CSDL, không bao giờ là bản thô")
    void chiLuuBanBam() {
        String tho = guiVaLayTokenTho("moi@example.com");

        // Lưu bản thô thì ai đọc được bảng users — một bản sao lưu lọt ra, một
        // lỗi SQL injection — đều đổi được email của mọi tài khoản, mà đổi
        // email là bước đầu của chiếm tài khoản.
        assertAll(
                () -> assertNotEquals(tho, nguoiDung.getEmailVerificationToken()),
                () -> assertEquals(DigestUtils.sha256Hex(tho),
                        nguoiDung.getEmailVerificationToken()),
                () -> assertEquals(64, nguoiDung.getEmailVerificationToken().length()));
    }

    @Test
    @DisplayName("Email mới vào pendingEmail, email đang dùng KHÔNG đổi")
    void emailMoiVaoPending() {
        service.sendVerification(nguoiDung, "moi@example.com");

        // Ghi đè sớm là người ta gõ nhầm một chữ rồi mất luôn đường đăng nhập.
        assertAll(
                () -> assertEquals("moi@example.com", nguoiDung.getPendingEmail()),
                () -> assertEquals("cu@example.com", nguoiDung.getEmail()),
                () -> assertFalse(nguoiDung.getEmailVerified()));
    }

    @Test
    @DisplayName("Email được chuẩn hoá: cắt hai đầu và hạ chữ thường")
    void chuanHoaEmail() {
        service.sendVerification(nguoiDung, "  MOI@Example.COM  ");

        // Không chuẩn hoá thì "A@x.com" và "a@x.com" thành hai tài khoản khác
        // nhau, và phép kiểm trùng email ở dưới không bắt được gì.
        assertEquals("moi@example.com", nguoiDung.getPendingEmail());
    }

    @Test
    @DisplayName("Đường dẫn xác nhận trỏ đúng trang và mang token thô")
    void duongDanXacNhan() {
        String tho = guiVaLayTokenTho("moi@example.com");

        ArgumentCaptor<String> bat = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailVerification(anyString(), bat.capture());
        assertAll(
                () -> assertTrue(bat.getValue().startsWith("https://astrotarot.date/verify-email")),
                () -> assertTrue(bat.getValue().contains("token=" + tho)));
    }

    @Test
    @DisplayName("Thư gửi tới email MỚI, không gửi tới email cũ")
    void guiToiEmailMoi() {
        service.sendVerification(nguoiDung, "moi@example.com");

        // Gửi tới email cũ thì chẳng chứng minh được người ta có đọc được hòm
        // thư mới hay không — tức là mất hẳn ý nghĩa của bước xác thực.
        verify(emailService).sendEmailVerification(
                org.mockito.ArgumentMatchers.eq("moi@example.com"), anyString());
    }

    @Test
    @DisplayName("Token hết hạn sau 24 giờ")
    void tokenHetHanSau24Gio() {
        Instant truoc = Instant.now();
        service.sendVerification(nguoiDung, "moi@example.com");

        Instant han = nguoiDung.getEmailVerificationExpiresAt();
        assertAll(
                () -> assertTrue(han.isAfter(truoc.plus(23, ChronoUnit.HOURS))),
                () -> assertTrue(han.isBefore(truoc.plus(25, ChronoUnit.HOURS))));
    }

    @Test
    @DisplayName("Email đã có người khác dùng thì bị chặn ngay lúc gửi")
    void emailDaCoNguoiKhacDung() {
        User nguoiKhac = new User();
        nguoiKhac.setId(UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("moi@example.com"))
                .thenReturn(Optional.of(nguoiKhac));

        assertThrows(EmailAlreadyExistsException.class,
                () -> service.sendVerification(nguoiDung, "moi@example.com"));
        verify(emailService, never()).sendEmailVerification(anyString(), anyString());
    }

    @Test
    @DisplayName("Gửi lại đúng email của CHÍNH MÌNH thì không bị coi là trùng")
    void emailCuaChinhMinh() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("cu@example.com"))
                .thenReturn(Optional.of(nguoiDung));

        // Người ta bấm "gửi lại thư xác nhận" cho chính email đang dùng. Chặn ở
        // đây là khoá luôn đường xác thực lại.
        service.sendVerification(nguoiDung, "cu@example.com");
        verify(emailService).sendEmailVerification(anyString(), anyString());
    }

    @Test
    @DisplayName("Email rỗng hoặc null thì bị chặn")
    void emailRong() {
        assertAll(
                () -> assertThrows(InvalidTokenException.class,
                        () -> service.sendVerification(nguoiDung, null)),
                () -> assertThrows(InvalidTokenException.class,
                        () -> service.sendVerification(nguoiDung, "   ")));
    }

    @Test
    @DisplayName("Xác nhận thành công: đổi email, đánh dấu đã xác thực, DỌN token")
    void xacNhanThanhCong() {
        String tho = guiVaLayTokenTho("moi@example.com");
        when(userRepository.findByEmailVerificationToken(DigestUtils.sha256Hex(tho)))
                .thenReturn(Optional.of(nguoiDung));

        service.verify(tho);

        assertAll(
                () -> assertEquals("moi@example.com", nguoiDung.getEmail()),
                () -> assertTrue(nguoiDung.getEmailVerified()),
                () -> assertTrue(nguoiDung.getEmailVerifiedAt() != null),
                // Dọn token sau khi dùng: để lại là một đường dẫn dùng được mãi
                // nằm trong hòm thư, và hòm thư là thứ hay bị đọc lén nhất.
                () -> assertNull(nguoiDung.getEmailVerificationToken()),
                () -> assertNull(nguoiDung.getPendingEmail()),
                () -> assertNull(nguoiDung.getEmailVerificationExpiresAt()));
    }

    @Test
    @DisplayName("Token không có thật thì bị từ chối")
    void tokenKhongCoThat() {
        when(userRepository.findByEmailVerificationToken(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> service.verify("bia-ra-mot-token"));
    }

    @Test
    @DisplayName("Token HẾT HẠN thì bị từ chối, email không đổi")
    void tokenHetHan() {
        nguoiDung.setPendingEmail("moi@example.com");
        nguoiDung.setEmailVerificationToken(DigestUtils.sha256Hex("tho"));
        nguoiDung.setEmailVerificationExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(userRepository.findByEmailVerificationToken(DigestUtils.sha256Hex("tho")))
                .thenReturn(Optional.of(nguoiDung));

        assertThrows(InvalidTokenException.class, () -> service.verify("tho"));
        assertEquals("cu@example.com", nguoiDung.getEmail());
    }

    @Test
    @DisplayName("Token không có hạn thì cũng bị từ chối, không coi là vô hạn")
    void tokenKhongCoHan() {
        nguoiDung.setPendingEmail("moi@example.com");
        nguoiDung.setEmailVerificationToken(DigestUtils.sha256Hex("tho"));
        nguoiDung.setEmailVerificationExpiresAt(null);
        when(userRepository.findByEmailVerificationToken(DigestUtils.sha256Hex("tho")))
                .thenReturn(Optional.of(nguoiDung));

        // Null nghĩa là dữ liệu hỏng. Coi hỏng là "không hết hạn" thì một bản
        // ghi lỗi thành một token sống mãi.
        assertThrows(InvalidTokenException.class, () -> service.verify("tho"));
    }

    @Test
    @DisplayName("Không còn email chờ xác nhận thì từ chối")
    void khongConEmailCho() {
        nguoiDung.setPendingEmail(null);
        nguoiDung.setEmailVerificationToken(DigestUtils.sha256Hex("tho"));
        nguoiDung.setEmailVerificationExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(userRepository.findByEmailVerificationToken(DigestUtils.sha256Hex("tho")))
                .thenReturn(Optional.of(nguoiDung));

        assertThrows(InvalidTokenException.class, () -> service.verify("tho"));
    }

    @Test
    @DisplayName("Có người khác chiếm mất email trong lúc chờ thì từ chối")
    void biChiemMatEmailTrongLucCho() {
        nguoiDung.setPendingEmail("moi@example.com");
        nguoiDung.setEmailVerificationToken(DigestUtils.sha256Hex("tho"));
        nguoiDung.setEmailVerificationExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        when(userRepository.findByEmailVerificationToken(DigestUtils.sha256Hex("tho")))
                .thenReturn(Optional.of(nguoiDung));

        User nguoiKhac = new User();
        nguoiKhac.setId(UUID.randomUUID());
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("moi@example.com"))
                .thenReturn(Optional.of(nguoiKhac));

        // Token sống 24 giờ, nên có cả một ngày để người khác đăng ký mất email
        // đó. Kiểm lần nữa ở đây, không tin kết quả kiểm lúc gửi.
        assertThrows(EmailAlreadyExistsException.class, () -> service.verify("tho"));
        assertEquals("cu@example.com", nguoiDung.getEmail());
    }

    @Test
    @DisplayName("Hai lần gửi sinh hai token khác nhau")
    void haiLanGuiHaiToken() {
        String lan1 = guiVaLayTokenTho("moi@example.com");
        org.mockito.Mockito.reset(emailService);
        String lan2 = guiVaLayTokenTho("moi@example.com");

        // Token đoán được hay lặp lại là token vô dụng.
        assertNotEquals(lan1, lan2);
    }
}
