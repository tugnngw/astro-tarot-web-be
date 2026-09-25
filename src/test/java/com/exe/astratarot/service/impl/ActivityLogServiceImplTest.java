package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.ActivityLog;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.UserRole;
import com.exe.astratarot.repository.ActivityLogRepository;
import com.exe.astratarot.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nhật ký thao tác quản trị. Lớp này trước đây phủ 3,4%.
 *
 * <p>Điểm quan trọng nhất là một quyết định dễ bị "sửa" nhầm: {@code record()}
 * <b>nuốt mọi ngoại lệ</b>. Ghi nhật ký hỏng không được kéo theo thao tác
 * chính — đổi vai trò thành công rồi mà rollback chỉ vì không ghi được log là
 * làm hỏng đúng thứ người dùng vừa yêu cầu. Bộ kiểm chốt lại để không ai đổi nó
 * thành ném ra.
 *
 * <p>Điểm thứ hai: thao tác do <b>hệ thống</b> thực hiện (webhook PayOS, tác vụ
 * nền) không có người thực hiện. Nhật ký phải ghi được chúng, và hiển thị là
 * "Hệ thống" chứ không phải một dòng trống.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActivityLogServiceImplTest {

    @Mock private ActivityLogRepository activityLogRepository;
    @Mock private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ActivityLogServiceImpl service;
    private User admin;

    @BeforeEach
    void setUp() {
        service = new ActivityLogServiceImpl(activityLogRepository, userRepository, objectMapper);

        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setFullName("Quản trị viên");
        admin.setRole(UserRole.ADMIN);

        lenient().when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        lenient().when(activityLogRepository.save(any(ActivityLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private ActivityLog batDong() {
        ArgumentCaptor<ActivityLog> bat = ArgumentCaptor.forClass(ActivityLog.class);
        verify(activityLogRepository).save(bat.capture());
        return bat.getValue();
    }

    // =====================================================================

    @Test
    @DisplayName("Ghi một dòng nhật ký đầy đủ")
    void ghiMotDong() {
        UUID doiTuong = UUID.randomUUID();

        service.record(admin.getId(), "USER_UPDATE", "USER", doiTuong,
                Map.of("role", Map.of("from", "USER", "to", "STAFF")));

        ActivityLog dong = batDong();
        assertAll(
                () -> assertEquals(admin, dong.getUser()),
                () -> assertEquals("USER_UPDATE", dong.getAction()),
                () -> assertEquals("USER", dong.getEntityType()),
                () -> assertEquals(doiTuong, dong.getEntityId()),
                () -> assertTrue(dong.getChanges().contains("STAFF")));
    }

    @Test
    @DisplayName("Thao tác của HỆ THỐNG ghi được, người thực hiện để trống")
    void thaoTacCuaHeThong() {
        service.record(null, "PAYMENT_CONFIRM", "PAYMENT", UUID.randomUUID(), Map.of());

        // Webhook PayOS và tác vụ nền không có người thực hiện. Bắt buộc phải
        // có actorId thì những thao tác ấy biến mất khỏi nhật ký — đúng những
        // thao tác khó truy nhất.
        assertNull(batDong().getUser());
    }

    @Test
    @DisplayName("ID người thực hiện lạ thì vẫn ghi, chỉ là không gắn ai")
    void idNguoiThucHienLa() {
        UUID la = UUID.randomUUID();
        when(userRepository.findById(la)).thenReturn(Optional.empty());

        service.record(la, "X", "Y", UUID.randomUUID(), null);

        assertNull(batDong().getUser());
    }

    @Test
    @DisplayName("Không có thay đổi nào thì lưu null, không lưu chuỗi {}")
    void khongCoThayDoi() {
        service.record(admin.getId(), "X", "Y", UUID.randomUUID(), Map.of());
        assertNull(batDong().getChanges());
    }

    @Test
    @DisplayName("Thay đổi null cũng lưu null")
    void thayDoiNull() {
        service.record(admin.getId(), "X", "Y", UUID.randomUUID(), null);
        assertNull(batDong().getChanges());
    }

    @Test
    @DisplayName("Ghi nhật ký LỖI thì nuốt, không kéo theo thao tác chính")
    void ghiLoiThiNuot() {
        when(activityLogRepository.save(any(ActivityLog.class)))
                .thenThrow(new RuntimeException("mất kết nối DB"));

        // Đổi vai trò thành công rồi mà rollback chỉ vì không ghi được log là
        // làm hỏng đúng thứ người dùng vừa yêu cầu.
        assertDoesNotThrow(() -> service.record(admin.getId(), "X", "Y", UUID.randomUUID(), null));
    }

    @Test
    @DisplayName("Dữ liệu thay đổi không ghi được thành JSON cũng không nổ")
    void duLieuKhongGhiDuocJson() {
        // Một object tự tham chiếu thì Jackson ném ra; nhật ký vẫn phải im lặng.
        Map<String, Object> vong = new java.util.HashMap<>();
        vong.put("tu-tham-chieu", vong);

        assertDoesNotThrow(() -> service.record(admin.getId(), "X", "Y", UUID.randomUUID(), vong));
    }

    @Test
    @DisplayName("Không lọc thì truyền CHUỖI RỖNG xuống, không truyền null")
    void khongLocThiChuoiRong() {
        when(activityLogRepository.search(anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, null, PageRequest.of(0, 20));
        service.list("  USER_UPDATE  ", "  USER  ", PageRequest.of(0, 20));

        verify(activityLogRepository).search(
                org.mockito.ArgumentMatchers.eq(""), org.mockito.ArgumentMatchers.eq(""), any());
        verify(activityLogRepository).search(
                org.mockito.ArgumentMatchers.eq("USER_UPDATE"),
                org.mockito.ArgumentMatchers.eq("USER"), any());
    }

    @Test
    @DisplayName("Dòng nhật ký của hệ thống hiển thị là 'Hệ thống'")
    void hienThiHeThong() {
        ActivityLog dong = ActivityLog.builder()
                .id(UUID.randomUUID())
                .user(null)
                .action("PAYMENT_CONFIRM")
                .entityType("PAYMENT")
                .build();
        when(activityLogRepository.search(anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(dong)));

        var kq = service.list(null, null, PageRequest.of(0, 20)).getContent().get(0);

        // Một dòng trống ở cột "Người thực hiện" trông như dữ liệu hỏng, và
        // người đọc nhật ký sẽ đi tìm nguyên nhân không tồn tại.
        assertAll(
                () -> assertEquals("Hệ thống", kq.getActorName()),
                () -> assertNull(kq.getActorId()),
                () -> assertNull(kq.getActorRole()));
    }

    @Test
    @DisplayName("Dòng nhật ký của người thật mang đủ tên và vai trò")
    void hienThiNguoiThat() {
        ActivityLog dong = ActivityLog.builder()
                .id(UUID.randomUUID())
                .user(admin)
                .action("USER_DELETE")
                .entityType("USER")
                .changes("{\"email\":\"x@y.z\"}")
                .build();
        when(activityLogRepository.search(anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(dong)));

        var kq = service.list(null, null, PageRequest.of(0, 20)).getContent().get(0);

        assertAll(
                () -> assertEquals("Quản trị viên", kq.getActorName()),
                () -> assertEquals(admin.getId(), kq.getActorId()),
                () -> assertEquals("ADMIN", kq.getActorRole()),
                () -> assertEquals("{\"email\":\"x@y.z\"}", kq.getChanges()));
    }
}
