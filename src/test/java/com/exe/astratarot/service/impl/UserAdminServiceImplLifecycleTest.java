package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.admin.CreateUserRequest;
import com.exe.astratarot.domain.dto.admin.UpdateUserInfoRequest;
import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserSession;
import com.exe.astratarot.domain.enums.AuthProvider;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.exception.EmailAlreadyExistsException;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.OrderRepository;
import com.exe.astratarot.repository.ReaderApplicationRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.service.UsernameGenerator;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.AuthService;
import com.exe.astratarot.service.NotificationService;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
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
 * Vòng đời tài khoản trong khu quản trị — phần bộ kiểm cũ chưa chạm tới. Bộ
 * {@code UserAdminServiceImplRoleGuardTest} chốt luật đổi vai trò; bộ này lo
 * tạo, sửa, khoá, xoá và các thao tác gửi thư.
 *
 * <p>Hai nguyên tắc xuyên suốt:
 *
 * <ol>
 *   <li><b>Quản trị viên không bao giờ biết mật khẩu của người khác.</b> Tạo
 *       tài khoản hay đặt lại mật khẩu đều chỉ gửi một đường dẫn. Biết mật khẩu
 *       của người khác là mất hẳn khả năng quy trách nhiệm cho một thao tác
 *       đăng nhập — về sau không phân biệt được người dùng làm hay admin làm.
 *   <li><b>Mọi lần hạ quyền hay khoá đều phải thu hồi phiên.</b> Quyền nằm
 *       trong access token đã phát, nên không thu hồi thì người vừa bị khoá vẫn
 *       dùng được token cũ cho tới khi nó hết hạn.
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserAdminServiceImplLifecycleTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;
    @Mock private ReaderApplicationRepository readerApplicationRepository;
    @Mock private UsernameGenerator usernameGenerator;
    @Mock private ActivityLogService activityLogService;
    @Mock private AuthService authService;
    @Mock private NotificationService notificationService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UserAdminServiceImpl service;

    private User admin;
    private User quanLy;
    private User thanhVien;

    @BeforeEach
    void setUp() {
        service = new UserAdminServiceImpl(userRepository, userSessionRepository, orderRepository,
                readerProfileRepository, readerApplicationRepository, passwordEncoder,
                usernameGenerator, activityLogService, authService, notificationService);

        admin = taiKhoan("admin@example.com", UserRole.ADMIN);
        quanLy = taiKhoan("quanly@example.com", UserRole.MANAGER);
        thanhVien = taiKhoan("thanhvien@example.com", UserRole.USER);

        lenient().when(usernameGenerator.fromEmail(anyString())).thenReturn("tendangnhap");
        lenient().when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId(UUID.randomUUID());
            }
            return u;
        });
        lenient().when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(anyString()))
                .thenReturn(false);
        lenient().when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(3L);
        lenient().when(userSessionRepository.findByUserIdAndRevokedFalse(any())).thenReturn(List.of());
        lenient().when(userSessionRepository.countByUserIdAndRevokedFalse(any())).thenReturn(0L);
        lenient().when(orderRepository.countByUserId(any())).thenReturn(0L);
        lenient().when(orderRepository.sumTotalAmountByUserId(any())).thenReturn(0L);
        lenient().when(readerProfileRepository.existsByUserId(any())).thenReturn(false);
        lenient().when(readerApplicationRepository.existsByUserIdAndStatus(any(), any()))
                .thenReturn(false);
    }

    private User taiKhoan(String email, UserRole vaiTro) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername(email.split("@")[0]);
        u.setEmail(email);
        u.setFullName("Tên " + vaiTro);
        u.setRole(vaiTro);
        u.setStatus(UserStatus.ACTIVE);
        u.setEmailVerified(true);
        u.setAuthProvider(AuthProvider.LOCAL);
        lenient().when(userRepository.findById(u.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private CreateUserRequest yeuCauTao(String email, UserRole vaiTro, boolean daXacMinh) {
        CreateUserRequest r = new CreateUserRequest();
        r.setEmail(email);
        r.setFullName("  Người Mới  ");
        r.setRole(vaiTro);
        r.setMarkEmailVerified(daXacMinh);
        return r;
    }

    // =====================================================================
    // Tạo tài khoản
    // =====================================================================

    @Nested
    @DisplayName("Tạo tài khoản")
    class TaoTaiKhoan {

        @Test
        @DisplayName("Tạo tài khoản: email hạ chữ thường, tên cắt hai đầu, trạng thái ACTIVE")
        void taoTaiKhoan() {
            var kq = service.create(admin.getId(),
                    yeuCauTao("  NguoiMoi@Example.COM  ", UserRole.STAFF, false));

            assertAll(
                    () -> assertEquals("nguoimoi@example.com", kq.getEmail()),
                    () -> assertEquals("Người Mới", kq.getFullName()),
                    () -> assertEquals(UserRole.STAFF.name(), kq.getRole()),
                    () -> assertEquals(UserStatus.ACTIVE.name(), kq.getStatus()));
        }

        @Test
        @DisplayName("Mật khẩu KHÔNG bao giờ trả về, chỉ gửi thư cho người mới")
        void khongTraVeMatKhau() {
            service.create(admin.getId(), yeuCauTao("moi@example.com", UserRole.USER, false));

            ArgumentCaptor<User> bat = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(bat.capture());
            // Biết mật khẩu của người khác là mất hẳn khả năng quy trách nhiệm
            // cho một thao tác đăng nhập — về sau không phân biệt được người
            // dùng làm hay admin làm.
            assertAll(
                    () -> assertNotNull(bat.getValue().getPasswordHash()),
                    () -> assertTrue(bat.getValue().getPasswordHash().startsWith("$2")),
                    () -> assertFalse(bat.getValue().getPasswordHash().contains("moi@example.com")));
            verify(authService).resendVerification(any());
        }

        @Test
        @DisplayName("Đánh dấu đã xác minh thì gửi luồng ĐẶT LẠI mật khẩu, không gửi thư xác minh")
        void daXacMinhThiGuiDatLaiMatKhau() {
            service.create(admin.getId(), yeuCauTao("moi@example.com", UserRole.USER, true));

            // Gửi thư xác minh cho một email đã đánh dấu xác minh là bảo người
            // ta xác minh lại thứ vừa được công nhận.
            verify(authService).forgotPassword(any());
            verify(authService, never()).resendVerification(any());
        }

        @Test
        @DisplayName("Email đã tồn tại thì bị chặn")
        void emailDaTonTai() {
            when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("trung@example.com"))
                    .thenReturn(true);

            assertThrows(EmailAlreadyExistsException.class, () -> service.create(admin.getId(),
                    yeuCauTao("trung@example.com", UserRole.USER, false)));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Quản lý KHÔNG tạo được tài khoản Quản trị viên")
        void quanLyKhongTaoDuocAdmin() {
            // Tạo thẳng một ADMIN mới là đường vòng để tự nâng quyền: quản lý
            // không sửa được vai trò của ai lên ADMIN, nhưng nếu tạo được thì
            // họ chỉ cần tạo một tài khoản mới rồi đăng nhập bằng nó.
            assertAll(
                    () -> assertThrows(AccessDeniedException.class, () -> service.create(quanLy.getId(),
                            yeuCauTao("moi@example.com", UserRole.ADMIN, false))),
                    () -> assertThrows(AccessDeniedException.class, () -> service.create(quanLy.getId(),
                            yeuCauTao("moi@example.com", UserRole.MANAGER, false))));
        }

        @Test
        @DisplayName("Quản lý tạo được Người dùng và Nhân viên")
        void quanLyTaoDuocUserVaStaff() {
            assertAll(
                    () -> assertEquals(UserRole.USER.name(), service.create(quanLy.getId(),
                            yeuCauTao("a@example.com", UserRole.USER, false)).getRole()),
                    () -> assertEquals(UserRole.STAFF.name(), service.create(quanLy.getId(),
                            yeuCauTao("b@example.com", UserRole.STAFF, false)).getRole()));
        }
    }

    // =====================================================================
    // Sửa thông tin
    // =====================================================================

    @Nested
    @DisplayName("Sửa thông tin")
    class SuaThongTin {

        private UpdateUserInfoRequest sua(String ten, String dienThoai, String thanhPho, String diaChi) {
            UpdateUserInfoRequest r = new UpdateUserInfoRequest();
            r.setFullName(ten);
            r.setPhone(dienThoai);
            r.setCity(thanhPho);
            r.setAddress(diaChi);
            return r;
        }

        @Test
        @DisplayName("Trường KHÔNG gửi lên thì giữ nguyên, không bị xoá trắng")
        void truongKhongGuiThiGiuNguyen() {
            thanhVien.setPhone("0900000000");
            thanhVien.setCity("Hà Nội");
            thanhVien.setAddress("Số 1");

            service.updateInfo(admin.getId(), thanhVien.getId(),
                    sua("Tên Mới", null, null, null));

            // PATCH mà ghi đè cả trường null sẽ xoá sạch dữ liệu người dùng chỉ
            // vì giao diện không gửi đủ.
            assertAll(
                    () -> assertEquals("Tên Mới", thanhVien.getFullName()),
                    () -> assertEquals("0900000000", thanhVien.getPhone()),
                    () -> assertEquals("Hà Nội", thanhVien.getCity()),
                    () -> assertEquals("Số 1", thanhVien.getAddress()));
        }

        @Test
        @DisplayName("Gửi chuỗi RỖNG là cố ý xoá trường đó")
        void chuoiRongLaXoa() {
            thanhVien.setPhone("0900000000");
            thanhVien.setCity("Hà Nội");

            service.updateInfo(admin.getId(), thanhVien.getId(), sua(null, "", "  ", null));

            assertAll(
                    () -> assertNull(thanhVien.getPhone()),
                    () -> assertNull(thanhVien.getCity()));
        }

        @Test
        @DisplayName("Tên rỗng thì BỎ QUA — tên trống hiện ra khắp nơi trong app")
        void tenRongThiBoQua() {
            service.updateInfo(admin.getId(), thanhVien.getId(), sua("   ", null, null, null));

            assertEquals("Tên " + UserRole.USER, thanhVien.getFullName());
        }

        @Test
        @DisplayName("Không đổi gì thì KHÔNG ghi nhật ký")
        void khongDoiGiThiKhongGhiNhatKy() {
            service.updateInfo(admin.getId(), thanhVien.getId(), sua(null, null, null, null));

            // Một dòng nhật ký "đã sửa" mà không sửa gì chỉ làm nhật ký khó đọc
            // đúng lúc cần đọc nhất.
            verify(activityLogService, never()).record(any(), anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("Quản lý KHÔNG sửa được thông tin của Quản trị viên")
        void quanLyKhongSuaDuocAdmin() {
            assertThrows(AccessDeniedException.class,
                    () -> service.updateInfo(quanLy.getId(), admin.getId(),
                            sua("Tên Bịa", null, null, null)));
        }
    }

    // =====================================================================
    // Khoá, mở khoá, xoá
    // =====================================================================

    @Nested
    @DisplayName("Khoá và xoá")
    class KhoaVaXoa {

        @Test
        @DisplayName("Khoá tài khoản thì THU HỒI mọi phiên đang mở")
        void khoaThiThuHoiPhien() {
            UserSession phien = UserSession.builder().id(UUID.randomUUID()).revoked(false).build();
            when(userSessionRepository.findByUserIdAndRevokedFalse(thanhVien.getId()))
                    .thenReturn(List.of(phien));

            service.changeStatus(admin.getId(), thanhVien.getId(), UserStatus.BANNED);

            // Quyền nằm trong access token đã phát; không thu hồi thì người vừa
            // bị khoá vẫn dùng được token cũ cho tới khi nó hết hạn.
            assertAll(
                    () -> assertEquals(UserStatus.BANNED, thanhVien.getStatus()),
                    () -> assertTrue(phien.getRevoked()));
        }

        @Test
        @DisplayName("MỞ khoá thì không đụng tới phiên")
        void moKhoaThiKhongDungPhien() {
            thanhVien.setStatus(UserStatus.BANNED);

            service.changeStatus(admin.getId(), thanhVien.getId(), UserStatus.ACTIVE);

            assertEquals(UserStatus.ACTIVE, thanhVien.getStatus());
            verify(userSessionRepository, never()).findByUserIdAndRevokedFalse(any());
        }

        @Test
        @DisplayName("Không khoá được quản trị viên CUỐI CÙNG")
        void khongKhoaDuocAdminCuoiCung() {
            User adminKhac = taiKhoan("admin2@example.com", UserRole.ADMIN);
            when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(1L);

            // Khoá người quản trị cuối cùng là khoá luôn cửa vào khu quản trị,
            // và không còn ai mở lại được.
            assertThrows(AccessDeniedException.class,
                    () -> service.changeStatus(admin.getId(), adminKhac.getId(), UserStatus.BANNED));
            assertEquals(UserStatus.ACTIVE, adminKhac.getStatus());
        }

        @Test
        @DisplayName("Xoá là xoá MỀM: giữ bản ghi, đánh dấu thời điểm, thu hồi phiên")
        void xoaLaXoaMem() {
            UserSession phien = UserSession.builder().id(UUID.randomUUID()).revoked(false).build();
            when(userSessionRepository.findByUserIdAndRevokedFalse(thanhVien.getId()))
                    .thenReturn(List.of(phien));

            service.softDelete(admin.getId(), thanhVien.getId());

            // Xoá cứng làm gãy khoá ngoại tới đơn hàng, đánh giá, lịch hẹn —
            // hoặc mất luôn lịch sử giao dịch mà kế toán cần giữ.
            assertAll(
                    () -> assertNotNull(thanhVien.getDeletedAt()),
                    () -> assertEquals(UserStatus.INACTIVE, thanhVien.getStatus()),
                    () -> assertTrue(phien.getRevoked()));
            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Quản lý KHÔNG xoá được tài khoản nào, kể cả người dùng thường")
        void quanLyKhongXoaDuoc() {
            // Quản lý sửa được nhân sự, nhưng xoá tài khoản là chuyện khác hẳn:
            // nó kéo theo đơn hàng, lịch hẹn, đánh giá.
            assertThrows(AccessDeniedException.class,
                    () -> service.softDelete(quanLy.getId(), thanhVien.getId()));
            assertNull(thanhVien.getDeletedAt());
        }

        @Test
        @DisplayName("Không xoá được quản trị viên cuối cùng")
        void khongXoaDuocAdminCuoiCung() {
            User adminKhac = taiKhoan("admin2@example.com", UserRole.ADMIN);
            when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN)).thenReturn(1L);

            assertThrows(AccessDeniedException.class,
                    () -> service.softDelete(admin.getId(), adminKhac.getId()));
        }

        @Test
        @DisplayName("Tài khoản đã xoá mềm thì coi như không tồn tại")
        void taiKhoanDaXoaCoiNhuKhongTonTai() {
            thanhVien.setDeletedAt(Instant.now());

            assertThrows(ResourceNotFoundException.class,
                    () -> service.detail(admin.getId(), thanhVien.getId()));
        }
    }

    // =====================================================================
    // Thư và phiên
    // =====================================================================

    @Nested
    @DisplayName("Gửi thư và thu hồi phiên")
    class ThuVaPhien {

        @Test
        @DisplayName("Đặt lại mật khẩu: gửi LINK, không đặt hộ mật khẩu")
        void datLaiMatKhauGuiLink() {
            String bamCu = thanhVien.getPasswordHash();

            service.sendPasswordReset(admin.getId(), thanhVien.getId());

            verify(authService).forgotPassword(any());
            assertEquals(bamCu, thanhVien.getPasswordHash());
        }

        @Test
        @DisplayName("Tài khoản không có email thì không gửi được link")
        void khongCoEmail() {
            thanhVien.setEmail("   ");

            assertThrows(IllegalArgumentException.class,
                    () -> service.sendPasswordReset(admin.getId(), thanhVien.getId()));
            verify(authService, never()).forgotPassword(any());
        }

        @Test
        @DisplayName("Đã xác minh email rồi thì không gửi lại thư xác minh")
        void daXacMinhThiKhongGuiLai() {
            thanhVien.setEmailVerified(true);

            assertThrows(IllegalArgumentException.class,
                    () -> service.resendVerification(admin.getId(), thanhVien.getId()));
        }

        @Test
        @DisplayName("Chưa xác minh thì gửi lại được")
        void chuaXacMinhThiGuiLai() {
            thanhVien.setEmailVerified(false);

            service.resendVerification(admin.getId(), thanhVien.getId());

            verify(authService).resendVerification(any());
        }

        @Test
        @DisplayName("Thu hồi phiên theo lệnh của quản trị viên")
        void thuHoiPhienTheoLenh() {
            UserSession phien = UserSession.builder().id(UUID.randomUUID()).revoked(false).build();
            when(userSessionRepository.findByUserIdAndRevokedFalse(thanhVien.getId()))
                    .thenReturn(List.of(phien));

            service.revokeSessions(admin.getId(), thanhVien.getId());

            assertTrue(phien.getRevoked());
        }

        @Test
        @DisplayName("Không thu hồi được phiên của người mình không có quyền")
        void khongThuHoiDuocPhienNguoiKhongCoQuyen() {
            assertThrows(AccessDeniedException.class,
                    () -> service.revokeSessions(quanLy.getId(), admin.getId()));
        }
    }

    // =====================================================================
    // Đọc danh sách và chi tiết
    // =====================================================================

    @Nested
    @DisplayName("Đọc")
    class Doc {

        @Test
        @DisplayName("Không lọc thì truyền CHUỖI RỖNG xuống, không truyền null")
        void khongLocThiChuoiRong() {
            when(userRepository.searchForAdmin(anyString(), anyString(), anyString(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            service.list(admin.getId(), null, null, null, PageRequest.of(0, 20));

            // Truy vấn dùng chuỗi rỗng làm sentinel "không lọc"; null xuống SQL
            // thì mọi điều kiện đều sai và danh sách về rỗng.
            verify(userRepository).searchForAdmin(
                    org.mockito.ArgumentMatchers.eq(""),
                    org.mockito.ArgumentMatchers.eq(""),
                    org.mockito.ArgumentMatchers.eq(""), any());
        }

        @Test
        @DisplayName("Danh sách mang cờ sửa được theo quyền của NGƯỜI ĐANG XEM")
        void coSuaDuocTheoNguoiXem() {
            when(userRepository.searchForAdmin(anyString(), anyString(), anyString(), any()))
                    .thenReturn(new PageImpl<>(List.of(thanhVien, admin)));

            var trang = service.list(quanLy.getId(), null, null, null, PageRequest.of(0, 20));

            // Giao diện dùng cờ này để mờ nút Sửa. Tính sai thì người ta bấm
            // vào rồi mới nhận lỗi quyền, mỗi lần một lần.
            assertAll(
                    () -> assertTrue(trang.getContent().get(0).getEditable()),
                    () -> assertFalse(trang.getContent().get(1).getEditable()));
        }

        @Test
        @DisplayName("Chi tiết mang đủ số liệu phụ trợ và cờ hồ sơ Reader")
        void chiTietDuSoLieu() {
            when(userSessionRepository.countByUserIdAndRevokedFalse(thanhVien.getId())).thenReturn(2L);
            when(orderRepository.countByUserId(thanhVien.getId())).thenReturn(3L);
            when(orderRepository.sumTotalAmountByUserId(thanhVien.getId())).thenReturn(1_500_000L);
            when(readerProfileRepository.existsByUserId(thanhVien.getId())).thenReturn(true);
            when(readerApplicationRepository.existsByUserIdAndStatus(
                    thanhVien.getId(), ReaderApplication.ApplicationStatus.PENDING)).thenReturn(true);

            var kq = service.detail(admin.getId(), thanhVien.getId());

            assertAll(
                    () -> assertEquals(2L, kq.getActiveSessions()),
                    () -> assertEquals(3L, kq.getOrderCount()),
                    () -> assertEquals(1_500_000L, kq.getTotalSpent()),
                    () -> assertTrue(kq.getHasReaderProfile()),
                    () -> assertTrue(kq.getHasPendingReaderApplication()),
                    () -> assertTrue(kq.getEditable()),
                    () -> assertTrue(kq.getPermissions() != null && !kq.getPermissions().isEmpty()));
        }

        @Test
        @DisplayName("Tự xem hồ sơ của mình thì cờ sửa được là FALSE")
        void tuXemHoSoCuaMinh() {
            // Tự sửa vai trò hoặc tự khoá mình là cách nhanh nhất để mất quyền
            // vào trang quản trị mà không ai lấy lại được.
            assertFalse(service.detail(admin.getId(), admin.getId()).getEditable());
        }
    }
}
