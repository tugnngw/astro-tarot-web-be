package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserSession;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.enums.UserStatus;
import com.exe.astratarot.repository.OrderRepository;
import com.exe.astratarot.repository.ReaderApplicationRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserSessionRepository;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.AuthService;
import com.exe.astratarot.service.NotificationService;
import com.exe.astratarot.service.UsernameGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Hàng rào phân quyền của màn quản trị tài khoản. Lớp này trước đây có độ phủ
 * 1,5%.
 *
 * <p>Đây là chỗ mà một lỗi không gây ra sự cố ồn ào — nó lặng lẽ cho ai đó
 * nhiều quyền hơn mức đáng có, và không ai phát hiện cho tới khi đã muộn. Bốn
 * bất biến được khoá ở đây:
 *
 * <ol>
 *   <li><b>Không ai sửa được chính mình.</b> Tự hạ vai trò hoặc tự khoá là
 *       cách nhanh nhất để mất quyền vào trang quản trị mà không ai lấy lại
 *       được.</li>
 *   <li><b>Quản lý chỉ đụng được tới Người dùng và Nhân viên</b> — cả ở phía
 *       đối tượng lẫn phía vai trò được gán. Thiếu một trong hai thì Quản lý
 *       tự nâng đồng nghiệp lên Quản trị viên được.</li>
 *   <li><b>Đổi vai trò phải thu hồi mọi phiên.</b> Quyền nằm trong access
 *       token đã phát; không thu hồi thì người vừa bị hạ quyền vẫn dùng token
 *       cũ tới khi nó hết hạn.</li>
 *   <li><b>Luôn còn ít nhất một quản trị viên.</b> Mất người cuối cùng là hỏng
 *       không cứu được — không còn ai cấp lại quyền cho bất kỳ ai.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class UserAdminServiceImplRoleGuardTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ReaderProfileRepository readerProfileRepository;
    @Mock private ReaderApplicationRepository readerApplicationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UsernameGenerator usernameGenerator;
    @Mock private ActivityLogService activityLogService;
    @Mock private AuthService authService;
    @Mock private NotificationService notificationService;

    private UserAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAdminServiceImpl(
                userRepository, userSessionRepository, orderRepository,
                readerProfileRepository, readerApplicationRepository,
                passwordEncoder, usernameGenerator, activityLogService,
                authService, notificationService);

        lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userSessionRepository.findByUserIdAndRevokedFalse(any()))
                .thenReturn(List.of());
    }

    private User ai(UserRole vaiTro) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setRole(vaiTro);
        u.setStatus(UserStatus.ACTIVE);
        u.setFullName("Tên " + vaiTro);
        u.setEmail(vaiTro.name().toLowerCase() + "@example.com");
        return u;
    }

    private void co(User... ds) {
        for (User u : ds) {
            lenient().when(userRepository.findById(u.getId()))
                    .thenReturn(Optional.of(u));
        }
    }

    // =====================================================================

    @Test
    @DisplayName("Quản trị viên KHÔNG tự đổi vai trò của chính mình được")
    void khongTuDoiVaiTroChinhMinh() {
        User admin = ai(UserRole.ADMIN);
        co(admin);

        var loi = assertThrows(AccessDeniedException.class,
                () -> service.changeRole(admin.getId(), admin.getId(), UserRole.USER));

        assertAll(
                () -> assertTrue(loi.getMessage().contains("chính mình"),
                        "Câu lỗi phải nói rõ lý do, nếu không người dùng tưởng "
                                + "mình thiếu quyền: " + loi.getMessage()),
                () -> assertEquals(UserRole.ADMIN, admin.getRole()));
    }

    @Test
    @DisplayName("Quản trị viên KHÔNG tự khoá chính mình được")
    void khongTuKhoaChinhMinh() {
        User admin = ai(UserRole.ADMIN);
        co(admin);

        assertThrows(AccessDeniedException.class,
                () -> service.changeStatus(admin.getId(), admin.getId(),
                        UserStatus.INACTIVE));
        assertEquals(UserStatus.ACTIVE, admin.getStatus());
    }

    @Test
    @DisplayName("Quản lý KHÔNG đụng được vào tài khoản Quản trị viên")
    void quanLyKhongDungDuocVaoAdmin() {
        User quanLy = ai(UserRole.MANAGER);
        User admin = ai(UserRole.ADMIN);
        co(quanLy, admin);

        assertThrows(AccessDeniedException.class,
                () -> service.changeRole(quanLy.getId(), admin.getId(), UserRole.USER));
        assertEquals(UserRole.ADMIN, admin.getRole());
    }

    @Test
    @DisplayName("Quản lý KHÔNG nâng ai lên Quản trị viên được")
    void quanLyKhongNangLenAdmin() {
        User quanLy = ai(UserRole.MANAGER);
        User nhanVien = ai(UserRole.STAFF);
        co(quanLy, nhanVien);

        // Chặn ở phía ĐỐI TƯỢNG thôi là chưa đủ: Quản lý đụng được vào Nhân
        // viên, nên nếu không chặn cả phía VAI TRÒ ĐƯỢC GÁN thì họ tự nâng
        // đồng nghiệp lên Quản trị viên rồi nhờ người đó nâng lại mình.
        var loi = assertThrows(AccessDeniedException.class,
                () -> service.changeRole(quanLy.getId(), nhanVien.getId(),
                        UserRole.ADMIN));

        assertAll(
                () -> assertTrue(loi.getMessage().contains("Quản lý")),
                () -> assertEquals(UserRole.STAFF, nhanVien.getRole()));
    }

    @Test
    @DisplayName("Quản lý cất Người dùng lên Nhân viên thì được")
    void quanLyCatNguoiDungLenNhanVien() {
        User quanLy = ai(UserRole.MANAGER);
        User nguoiDung = ai(UserRole.USER);
        co(quanLy, nguoiDung);

        service.changeRole(quanLy.getId(), nguoiDung.getId(), UserRole.STAFF);

        assertEquals(UserRole.STAFF, nguoiDung.getRole());
    }

    @Test
    @DisplayName("Đổi vai trò THU HỒI mọi phiên và báo cho người bị đổi")
    void doiVaiTroThiThuHoiPhien() {
        User admin = ai(UserRole.ADMIN);
        User muc = ai(UserRole.USER);
        co(admin, muc);

        UserSession p1 = new UserSession();
        UserSession p2 = new UserSession();
        when(userSessionRepository.findByUserIdAndRevokedFalse(muc.getId()))
                .thenReturn(List.of(p1, p2));

        service.changeRole(admin.getId(), muc.getId(), UserRole.STAFF);

        assertAll(
                // Quyền nằm trong access token đã phát. Không thu hồi thì
                // người vừa bị hạ quyền vẫn dùng token cũ tới khi hết hạn.
                () -> assertEquals(Boolean.TRUE, p1.getRevoked()),
                () -> assertEquals(Boolean.TRUE, p2.getRevoked()),
                // Họ vừa bị đá khỏi mọi thiết bị; không báo thì họ không hiểu
                // vì sao.
                () -> verify(notificationService).push(org.mockito.ArgumentMatchers.eq(muc),
                        anyString(), anyString(), anyString(), any()),
                () -> verify(activityLogService).record(
                        org.mockito.ArgumentMatchers.eq(admin.getId()),
                        anyString(), anyString(),
                        org.mockito.ArgumentMatchers.eq(muc.getId()), any()));
    }

    @Test
    @DisplayName("Đặt lại đúng vai trò đang có thì không làm gì cả")
    void datLaiVaiTroCuThiKhongLamGi() {
        User admin = ai(UserRole.ADMIN);
        User muc = ai(UserRole.STAFF);
        co(admin, muc);

        service.changeRole(admin.getId(), muc.getId(), UserRole.STAFF);

        // Không chặn thì một cú bấm nhầm vào đúng vai trò hiện tại cũng đá
        // người ta ra khỏi mọi thiết bị, chẳng vì lý do gì.
        assertAll(
                () -> verify(userSessionRepository, never())
                        .findByUserIdAndRevokedFalse(muc.getId()),
                () -> verify(notificationService, never())
                        .push(any(), anyString(), anyString(), anyString(), any()));
    }

    @Test
    @DisplayName("KHÔNG hạ được quản trị viên cuối cùng")
    void khongHaAdminCuoiCung() {
        User admin = ai(UserRole.ADMIN);
        User adminKhac = ai(UserRole.ADMIN);
        co(admin, adminKhac);
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN))
                .thenReturn(1L);

        // Mất người cuối cùng là hỏng không cứu được: không còn ai cấp lại
        // quyền quản trị cho bất kỳ ai.
        var loi = assertThrows(AccessDeniedException.class,
                () -> service.changeRole(admin.getId(), adminKhac.getId(),
                        UserRole.USER));

        assertAll(
                () -> assertTrue(loi.getMessage().contains("cuối cùng")),
                () -> assertEquals(UserRole.ADMIN, adminKhac.getRole()));
    }

    @Test
    @DisplayName("KHÔNG khoá được quản trị viên cuối cùng")
    void khongKhoaAdminCuoiCung() {
        User admin = ai(UserRole.ADMIN);
        User adminKhac = ai(UserRole.ADMIN);
        co(admin, adminKhac);
        when(userRepository.countByRoleAndDeletedAtIsNull(UserRole.ADMIN))
                .thenReturn(1L);

        assertThrows(AccessDeniedException.class,
                () -> service.changeStatus(admin.getId(), adminKhac.getId(),
                        UserStatus.INACTIVE));
        assertEquals(UserStatus.ACTIVE, adminKhac.getStatus());
    }

    @Test
    @DisplayName("Khoá tài khoản thì thu hồi phiên; mở khoá lại thì không")
    void khoaThiThuHoiPhienMoKhoaThiKhong() {
        User admin = ai(UserRole.ADMIN);
        User muc = ai(UserRole.USER);
        co(admin, muc);
        // KHÔNG cần stub countByRoleAndDeletedAtIsNull: lưới "admin cuối cùng"
        // chỉ chạy khi ĐỐI TƯỢNG là admin. Mockito bắt được stub thừa này và
        // qua đó xác nhận luôn rằng lưới ấy không chạy nhầm cho tài khoản
        // thường.
        service.changeStatus(admin.getId(), muc.getId(), UserStatus.INACTIVE);
        verify(userSessionRepository).findByUserIdAndRevokedFalse(muc.getId());

        // Mở khoá là trả lại quyền truy cập, không phải lý do để đá họ ra.
        service.changeStatus(admin.getId(), muc.getId(), UserStatus.ACTIVE);
        verify(userSessionRepository, org.mockito.Mockito.times(1))
                .findByUserIdAndRevokedFalse(muc.getId());
    }

    @Test
    @DisplayName("Đổi vai trò hàng loạt: bỏ trùng lặp trong danh sách")
    void hangLoatBoTrungLap() {
        User admin = ai(UserRole.ADMIN);
        User muc = ai(UserRole.USER);
        co(admin, muc);

        var kq = service.changeRoleBulk(admin.getId(),
                List.of(muc.getId(), muc.getId(), muc.getId()), UserRole.STAFF);

        // Không lọc trùng thì cùng một tài khoản bị ghi ba dòng nhật ký và
        // nhận ba thông báo cho một thao tác.
        assertAll(
                () -> assertEquals(1, kq.size()),
                () -> assertEquals(UserRole.STAFF, muc.getRole()),
                () -> verify(notificationService, org.mockito.Mockito.times(1))
                        .push(any(), anyString(), anyString(), anyString(), any()));
    }
}
