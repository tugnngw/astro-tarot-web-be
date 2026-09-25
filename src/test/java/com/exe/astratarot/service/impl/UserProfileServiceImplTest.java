package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.user.ChangePasswordRequest;
import com.exe.astratarot.domain.dto.user.UpdateProfileRequest;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserAvatar;
import com.exe.astratarot.domain.entity.UserSession;
import com.exe.astratarot.domain.enums.Gender;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.exception.InvalidCredentialsException;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.UserAvatarRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hồ sơ người dùng. Lớp này trước đây phủ 2,8%.
 *
 * <p>Ba chỗ đáng kiểm, và cả ba đều là chuyện an toàn chứ không phải hiển thị:
 *
 * <ol>
 *   <li><b>Sửa một trường không được xoá các trường khác.</b> Form chỉ gửi số
 *       điện thoại mà service gán tuốt thì bio, địa chỉ, ngày sinh của người ta
 *       bị xoá trắng — và không có cách nào lấy lại.
 *   <li><b>Đổi mật khẩu phải thu hồi mọi phiên.</b> Người ta đổi mật khẩu
 *       thường là vì nghi có người khác vào được. Không đá thiết bị lạ ra thì
 *       việc đổi mật khẩu chẳng giải quyết gì.
 *   <li><b>Tên file avatar do server đặt.</b> Dùng tên gốc của client là mở
 *       đường cho "../../application.properties".
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private UserAvatarRepository userAvatarRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserProfileServiceImpl service;
    private User nguoiDung;

    @BeforeEach
    void setUp() {
        service = new UserProfileServiceImpl(userRepository, userSessionRepository,
                passwordEncoder, userAvatarRepository);
        ReflectionTestUtils.setField(service, "uploadDir", "uploads");

        nguoiDung = new User();
        nguoiDung.setId(UUID.randomUUID());
        nguoiDung.setUsername("khach");
        nguoiDung.setEmail("khach@example.com");
        nguoiDung.setEmailVerified(true);
        nguoiDung.setFullName("Trần Duy Đạt");
        nguoiDung.setPhone("0900000000");
        nguoiDung.setBio("Giới thiệu cũ");
        nguoiDung.setAddress("Số 1 Đại Cồ Việt");
        nguoiDung.setCity("Hà Nội");
        nguoiDung.setCountry("Việt Nam");
        nguoiDung.setDateOfBirth(LocalDate.of(2000, 1, 1));
        nguoiDung.setPasswordHash(passwordEncoder.encode("matkhaucu123"));
        nguoiDung.setRole(UserRole.USER);

        lenient().when(userRepository.findById(nguoiDung.getId()))
                .thenReturn(Optional.of(nguoiDung));
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Yêu cầu chỉ đổi đúng một trường, mọi trường khác để null. */
    private UpdateProfileRequest chiDoi(String truong, Object giaTri) {
        return new UpdateProfileRequest(
                "fullName".equals(truong) ? (String) giaTri : null,
                "phone".equals(truong) ? (String) giaTri : null,
                "gender".equals(truong) ? (String) giaTri : null,
                "dateOfBirth".equals(truong) ? (LocalDate) giaTri : null,
                "bio".equals(truong) ? (String) giaTri : null,
                "address".equals(truong) ? (String) giaTri : null,
                "city".equals(truong) ? (String) giaTri : null,
                "country".equals(truong) ? (String) giaTri : null);
    }

    // =====================================================================
    // Xem và sửa hồ sơ
    // =====================================================================

    @Nested
    @DisplayName("Hồ sơ")
    class HoSo {

        @Test
        @DisplayName("Xem hồ sơ trả đủ trường, kèm danh sách quyền theo vai trò")
        void xemHoSo() {
            var kq = service.getProfile(nguoiDung.getId());

            assertAll(
                    () -> assertEquals("khach@example.com", kq.getEmail()),
                    () -> assertEquals("Trần Duy Đạt", kq.getFullName()),
                    () -> assertEquals("USER", kq.getRole()),
                    () -> assertEquals("ACTIVE", kq.getStatus()),
                    () -> assertEquals("LOCAL", kq.getAuthProvider()),
                    // Giao diện dựng menu từ danh sách quyền này; thiếu nó thì
                    // người dùng thấy một ứng dụng trống rỗng.
                    () -> assertTrue(kq.getPermissions() != null && !kq.getPermissions().isEmpty()));
        }

        @Test
        @DisplayName("Chưa khai giới tính thì trả UNDISCLOSED chứ không null")
        void gioiTinhChuaKhai() {
            nguoiDung.setGender(null);

            assertEquals("UNDISCLOSED", service.getProfile(nguoiDung.getId()).getGender());
        }

        @Test
        @DisplayName("Sửa MỘT trường không xoá các trường khác")
        void suaMotTruong() {
            var kq = service.updateProfile(nguoiDung.getId(), chiDoi("phone", "0911111111"));

            // Gán tuốt thì một form chỉ sửa số điện thoại sẽ xoá trắng bio, địa
            // chỉ, ngày sinh — và không có cách nào lấy lại.
            assertAll(
                    () -> assertEquals("0911111111", kq.getPhone()),
                    () -> assertEquals("Trần Duy Đạt", kq.getFullName()),
                    () -> assertEquals("Giới thiệu cũ", kq.getBio()),
                    () -> assertEquals("Số 1 Đại Cồ Việt", kq.getAddress()),
                    () -> assertEquals("Hà Nội", kq.getCity()),
                    () -> assertEquals("Việt Nam", kq.getCountry()),
                    () -> assertEquals(LocalDate.of(2000, 1, 1), kq.getDateOfBirth()));
        }

        @Test
        @DisplayName("Gửi chuỗi RỖNG là cố ý xoá trường đó")
        void chuoiRongLaXoa() {
            var kq = service.updateProfile(nguoiDung.getId(), chiDoi("bio", "   "));

            // Phân biệt null (không gửi) với chuỗi rỗng (gửi để xoá): thiếu
            // phân biệt này thì người dùng không có cách nào bỏ phần giới thiệu.
            assertNull(kq.getBio());
        }

        @Test
        @DisplayName("Họ tên rỗng thì BỎ QUA, không cho tên trống")
        void hoTenRongThiBoQua() {
            var kq = service.updateProfile(nguoiDung.getId(), chiDoi("fullName", "   "));

            // Tên trống hiện ra khắp nơi trong app — ở thẻ Reader, trong chat,
            // trên lịch hẹn. Đây là trường duy nhất không cho xoá.
            assertEquals("Trần Duy Đạt", kq.getFullName());
        }

        @Test
        @DisplayName("Họ tên được cắt hai đầu")
        void hoTenCatHaiDau() {
            assertEquals("Nguyễn Văn A",
                    service.updateProfile(nguoiDung.getId(),
                            chiDoi("fullName", "  Nguyễn Văn A  ")).getFullName());
        }

        @Test
        @DisplayName("Đổi giới tính")
        void doiGioiTinh() {
            assertEquals(Gender.FEMALE.name(),
                    service.updateProfile(nguoiDung.getId(), chiDoi("gender", "FEMALE")).getGender());
        }

        @Test
        @DisplayName("Giới tính rỗng thì giữ nguyên, không nổ vì valueOf")
        void gioiTinhRong() {
            nguoiDung.setGender(Gender.MALE);

            // Gọi Gender.valueOf("") là IllegalArgumentException, và nó bay ra
            // tận controller thành 500.
            assertEquals(Gender.MALE.name(),
                    service.updateProfile(nguoiDung.getId(), chiDoi("gender", "")).getGender());
        }

        @Test
        @DisplayName("Tài khoản đã xoá mềm thì không còn hồ sơ")
        void taiKhoanDaXoa() {
            nguoiDung.setDeletedAt(Instant.now());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.getProfile(nguoiDung.getId()));
        }

        @Test
        @DisplayName("Tài khoản không tồn tại thì báo đúng loại lỗi")
        void taiKhoanKhongTonTai() {
            UUID la = UUID.randomUUID();
            when(userRepository.findById(la)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> service.getProfile(la));
        }
    }

    // =====================================================================
    // Ảnh đại diện
    // =====================================================================

    @Nested
    @DisplayName("Ảnh đại diện")
    class AnhDaiDien {

        private MockMultipartFile anh(String loai, byte[] noiDung) {
            return new MockMultipartFile("file", "anh-cua-toi.png", loai, noiDung);
        }

        @Test
        @DisplayName("Lưu ảnh vào CSDL, và đường dẫn mang mốc thời gian phá cache")
        void luuAnh() {
            var kq = service.updateAvatar(nguoiDung.getId(), anh("image/png", new byte[]{1, 2, 3}));

            ArgumentCaptor<UserAvatar> bat = ArgumentCaptor.forClass(UserAvatar.class);
            verify(userAvatarRepository).save(bat.capture());
            assertAll(
                    // Ghi ra thư mục trong container thì mỗi lần deploy là mất:
                    // đĩa của Render ở gói free là tạm. Đã xảy ra thật.
                    () -> assertEquals(nguoiDung.getId(), bat.getValue().getUserId()),
                    () -> assertEquals("image/png", bat.getValue().getContentType()),
                    () -> assertEquals(3, bat.getValue().getData().length),
                    () -> assertTrue(kq.getAvatar().startsWith(
                            "/api/v1/users/" + nguoiDung.getId() + "/avatar?v=")),
                    // Không có mốc thời gian thì đường dẫn không đổi, và trình
                    // duyệt hiện lại ảnh cũ sau khi người ta vừa đổi ảnh.
                    () -> assertTrue(kq.getAvatar().contains("?v=")));
        }

        @Test
        @DisplayName("Không chọn ảnh thì báo lỗi")
        void khongChonAnh() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.updateAvatar(nguoiDung.getId(), null)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.updateAvatar(nguoiDung.getId(),
                                    anh("image/png", new byte[0]))));
        }

        @Test
        @DisplayName("Ảnh lớn hơn 2MB thì bị từ chối")
        void anhQuaLon() {
            var loi = assertThrows(IllegalArgumentException.class,
                    () -> service.updateAvatar(nguoiDung.getId(),
                            anh("image/jpeg", new byte[2 * 1024 * 1024 + 1])));
            assertTrue(loi.getMessage().contains("2MB"));
            verify(userAvatarRepository, never()).save(any());
        }

        @Test
        @DisplayName("Định dạng không phải ảnh thì bị từ chối")
        void khongPhaiAnh() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.updateAvatar(nguoiDung.getId(),
                                    anh("application/pdf", new byte[]{1}))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.updateAvatar(nguoiDung.getId(),
                                    anh("text/html", new byte[]{1}))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> service.updateAvatar(nguoiDung.getId(),
                                    anh(null, new byte[]{1}))));
        }

        @Test
        @DisplayName("Bốn định dạng ảnh đều nhận")
        void bonDinhDangAnh() {
            for (String loai : List.of("image/jpeg", "image/png", "image/webp", "image/gif")) {
                assertTrue(service.updateAvatar(nguoiDung.getId(), anh(loai, new byte[]{1}))
                        .getAvatar().contains("/avatar?v="), loai + " phải được nhận");
            }
        }

        @Test
        @DisplayName("Chữ hoa trong content-type vẫn nhận")
        void chuHoaContentType() {
            assertTrue(service.updateAvatar(nguoiDung.getId(), anh("IMAGE/PNG", new byte[]{1}))
                    .getAvatar().contains("/avatar?v="));
        }

        @Test
        @DisplayName("Gỡ ảnh: xoá khỏi CSDL và để trống trường avatar")
        void goAnh() {
            nguoiDung.setAvatar("/api/v1/users/x/avatar?v=1");

            var kq = service.removeAvatar(nguoiDung.getId());

            assertNull(kq.getAvatar());
            verify(userAvatarRepository).deleteById(nguoiDung.getId());
        }

        @Test
        @DisplayName("Ảnh từ Google là URL ngoài, gỡ ảnh không đụng tới file nào")
        void anhTuGoogle() {
            nguoiDung.setAvatar("https://lh3.googleusercontent.com/abc");

            assertNull(service.removeAvatar(nguoiDung.getId()).getAvatar());
        }

        @Test
        @DisplayName("Đường dẫn ảnh công khai dựng đúng dạng")
        void duongDanCongKhai() {
            UUID id = UUID.randomUUID();

            assertEquals("/api/v1/users/" + id + "/avatar?v=99",
                    UserProfileServiceImpl.avatarUrlFor(id, 99L));
        }
    }

    // =====================================================================
    // Đổi mật khẩu
    // =====================================================================

    @Nested
    @DisplayName("Đổi mật khẩu")
    class DoiMatKhau {

        @Test
        @DisplayName("Đổi thành công thì THU HỒI mọi phiên đang mở")
        void doiVaThuHoiPhien() {
            UserSession phien1 = UserSession.builder().id(UUID.randomUUID()).revoked(false).build();
            UserSession phien2 = UserSession.builder().id(UUID.randomUUID()).revoked(false).build();
            when(userSessionRepository.findByUserIdAndRevokedFalse(nguoiDung.getId()))
                    .thenReturn(List.of(phien1, phien2));

            service.changePassword(nguoiDung.getId(),
                    new ChangePasswordRequest("matkhaucu123", "matkhaumoi456"));

            // Người ta đổi mật khẩu thường vì nghi có người khác vào được.
            // Không đá thiết bị lạ ra thì việc đổi mật khẩu chẳng giải quyết gì.
            assertAll(
                    () -> assertTrue(phien1.getRevoked()),
                    () -> assertTrue(phien2.getRevoked()),
                    () -> assertTrue(passwordEncoder.matches("matkhaumoi456",
                            nguoiDung.getPasswordHash())));
        }

        @Test
        @DisplayName("Sai mật khẩu hiện tại thì bị chặn, và phiên KHÔNG bị thu hồi")
        void saiMatKhauHienTai() {
            assertThrows(InvalidCredentialsException.class,
                    () -> service.changePassword(nguoiDung.getId(),
                            new ChangePasswordRequest("doan-bua", "matkhaumoi456")));

            // Thu hồi phiên khi đoán sai là biến ô đổi mật khẩu thành một cách
            // đá người khác ra khỏi máy họ.
            verify(userSessionRepository, never()).findByUserIdAndRevokedFalse(any());
        }

        @Test
        @DisplayName("Mật khẩu mới trùng mật khẩu cũ thì bị chặn")
        void matKhauMoiTrungCu() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.changePassword(nguoiDung.getId(),
                            new ChangePasswordRequest("matkhaucu123", "matkhaucu123")));
        }

        @Test
        @DisplayName("Tài khoản Google chưa đặt mật khẩu thì chỉ dẫn sang quên mật khẩu")
        void taiKhoanGoogle() {
            nguoiDung.setPasswordHash(null);

            var loi = assertThrows(InvalidCredentialsException.class,
                    () -> service.changePassword(nguoiDung.getId(),
                            new ChangePasswordRequest("bat-ky", "matkhaumoi456")));
            // Báo "mật khẩu hiện tại không đúng" ở đây là bắt người ta đoán mãi
            // một thứ chưa từng tồn tại.
            assertTrue(loi.getMessage().contains("Google"));
        }
    }
}
