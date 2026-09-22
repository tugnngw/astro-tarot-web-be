package com.exe.astratarot.service;

import com.exe.astratarot.domain.dto.reader.ApplyReaderRequest;
import com.exe.astratarot.domain.dto.reader.ReviewReaderRequest;
import com.exe.astratarot.domain.entity.ReaderApplication;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.domain.mapper.ReaderApplicationMapper;
import com.exe.astratarot.exception.AlreadyReaderException;
import com.exe.astratarot.repository.ReaderApplicationRepository;
import com.exe.astratarot.repository.ReaderProfileRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.security.CustomUserDetails;
import com.exe.astratarot.security.SecurityPermissions;
import com.exe.astratarot.service.impl.ReaderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Khoá lại việc gộp vai trò READER vào STAFF (migration V2_15).
 *
 * <p>Điều bộ test này canh: <b>được duyệt làm Reader thì không được MẤT quyền
 * nào.</b> Trước đây {@code review()} gán {@code UserRole.READER}, mà READER
 * là tập con thật sự của STAFF — thiếu {@code SUPPORT_RESPOND}. Hậu quả là
 * một nhân viên vừa được duyệt vẫn nhìn thấy hàng chờ hỗ trợ nhưng không trả
 * lời khách được nữa. Thăng chức mà bị giáng quyền, và không ai để ý vì giao
 * diện vẫn hiện tab.
 *
 * <p>Kiểu lỗi này quay lại rất dễ: chỉ cần ai đó thấy enum thiếu "READER" rồi
 * thêm lại cho "đầy đủ". Nên có một test đứng canh đúng chỗ đó.
 */
@ExtendWith(MockitoExtension.class)
class ReaderRoleMergeTest {

    @Mock private ReaderApplicationRepository applicationRepository;
    @Mock private ReaderProfileRepository profileRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private ReaderApplicationMapper mapper;

    private ReaderServiceImpl service;

    private User nguoiDung;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ReaderServiceImpl(
                applicationRepository, profileRepository, userRepository,
                notificationService, mapper);

        nguoiDung = new User();
        nguoiDung.setId(userId);
        nguoiDung.setFullName("Người thử");
        nguoiDung.setRole(UserRole.USER);

        lenient().when(userRepository.findById(userId)).thenReturn(Optional.of(nguoiDung));
        lenient().when(applicationRepository.save(any(ReaderApplication.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(profileRepository.save(any(ReaderProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- Bất biến chính ----------

    @Test
    @DisplayName("Enum vai trò KHÔNG được có READER trở lại")
    void khongDuocCoLaiVaiTroReader() {
        assertTrue(
                Arrays.stream(UserRole.values()).noneMatch(r -> r.name().equals("READER")),
                "Thêm lại READER là tái lập chính lỗi V2_15 vừa sửa: nó là tập con "
                        + "của STAFF nên gán cho ai là bớt quyền người đó. "
                        + "'Là Reader' thể hiện bằng sự tồn tại của ReaderProfile.");
    }

    @Test
    @DisplayName("Nhân viên được duyệt không mất quyền nào so với trước")
    void duyetXongKhongMatQuyenNao() {
        List<String> truoc = CustomUserDetails.permissionsOf(UserRole.STAFF);

        nguoiDung.setRole(UserRole.STAFF);
        service.apply(userId, yeuCau());

        List<String> sau = CustomUserDetails.permissionsOf(nguoiDung.getRole());

        assertAll(
                () -> assertEquals(UserRole.STAFF, nguoiDung.getRole()),
                () -> assertTrue(sau.containsAll(truoc),
                        "Mất quyền sau khi được duyệt: " + thieu(truoc, sau)),
                () -> assertTrue(sau.contains(SecurityPermissions.SUPPORT_RESPOND),
                        "Reader vẫn phải trả lời được phiếu hỗ trợ — đây đúng là "
                                + "quyền đã bị mất trước khi gộp vai trò."));
    }

    // ---------- Nhân viên không phải xếp hàng ----------

    @Test
    @DisplayName("Nhân viên có hồ sơ ngay, không qua hàng chờ")
    void nhanVienCoHoSoNgay() {
        nguoiDung.setRole(UserRole.STAFF);

        var ketQua = service.apply(userId, yeuCau());

        assertEquals(ReaderService.ApplyOutcome.APPROVED_IMMEDIATELY, ketQua);
        verify(profileRepository).save(any(ReaderProfile.class));
    }

    @Test
    @DisplayName("Người ngoài vẫn phải qua duyệt — đây là sàn có tiền thật")
    void nguoiNgoaiVanPhaiDuyet() {
        nguoiDung.setRole(UserRole.USER);

        var ketQua = service.apply(userId, yeuCau());

        assertEquals(ReaderService.ApplyOutcome.SUBMITTED, ketQua);
        verify(profileRepository, never()).save(any(ReaderProfile.class));
        assertEquals(UserRole.USER, nguoiDung.getRole(),
                "Chưa duyệt thì chưa được nâng vai trò");
    }

    // ---------- Chặn đúng câu hỏi ----------

    @Test
    @DisplayName("Đã có hồ sơ rồi thì không nộp nữa — chặn theo HỒ SƠ, không theo vai trò")
    void daCoHoSoThiKhongNopNua() {
        nguoiDung.setRole(UserRole.STAFF);
        lenient().when(profileRepository.existsByUserId(userId)).thenReturn(true);

        assertThrows(AlreadyReaderException.class, () -> service.apply(userId, yeuCau()));
    }

    @Test
    @DisplayName("Nhân viên CHƯA có hồ sơ thì nộp được — chính là lối cụt cũ")
    void nhanVienChuaCoHoSoThiNopDuoc() {
        nguoiDung.setRole(UserRole.STAFF);
        lenient().when(profileRepository.existsByUserId(userId)).thenReturn(false);

        // Trước đây chặn bằng "vai trò có phải READER không", mà nhân viên
        // được cất lên từ trang quản lý chưa hề có hồ sơ — họ mắc kẹt vĩnh viễn.
        assertFalse(profileRepository.existsByUserId(userId));
        assertEquals(ReaderService.ApplyOutcome.APPROVED_IMMEDIATELY,
                service.apply(userId, yeuCau()));
    }

    // ---------- Duyệt đơn người ngoài ----------

    @Test
    @DisplayName("Quản trị duyệt đơn người ngoài thì họ thành STAFF, không phải vai trò riêng")
    void duyetDonNguoiNgoaiThiThanhStaff() {
        UUID reviewerId = UUID.randomUUID();
        User reviewer = new User();
        reviewer.setId(reviewerId);
        reviewer.setRole(UserRole.ADMIN);

        ReaderApplication don = ReaderApplication.builder()
                .id(UUID.randomUUID())
                .user(nguoiDung)
                .bio("xin chào")
                .experience(2)
                .specialties(new String[]{"Tarot"})
                .status(ReaderApplication.ApplicationStatus.PENDING)
                .build();

        lenient().when(applicationRepository.findById(don.getId())).thenReturn(Optional.of(don));
        lenient().when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));

        ReviewReaderRequest req = new ReviewReaderRequest();
        req.setAction("APPROVED");
        service.review(reviewerId, don.getId(), req);

        assertEquals(UserRole.STAFF, nguoiDung.getRole());
        verify(profileRepository).save(any(ReaderProfile.class));
    }

    private static ApplyReaderRequest yeuCau() {
        ApplyReaderRequest r = new ApplyReaderRequest();
        r.setBio("Tôi xem Tarot 3 năm");
        r.setExperience(3);
        r.setSpecialties(new String[]{"Tình cảm"});
        return r;
    }

    private static String thieu(List<String> truoc, List<String> sau) {
        return truoc.stream().filter(p -> !sau.contains(p)).toList().toString();
    }
}
