package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.Notification;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.event.NotificationPushedEvent;
import com.exe.astratarot.exception.ResourceNotFoundException;
import com.exe.astratarot.repository.NotificationRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Thông báo. Lớp này trước đây phủ 1,9%.
 *
 * <p>Điểm đáng kiểm nhất là một quyết định thiết kế dễ bị hiểu nhầm là lỗi:
 * {@code push()} <b>nuốt mọi ngoại lệ</b>. Đó là cố ý. Thông báo là việc phụ đi
 * kèm một việc chính — đặt lịch, thanh toán, huỷ. Để một lỗi khi ghi thông báo
 * kéo đổ cả giao dịch thanh toán là đổi một việc nhỏ hỏng lấy một việc lớn
 * hỏng. Bộ kiểm chốt lại hành vi này để không ai "sửa" nó thành ném ra.
 *
 * <p>Thứ hai là <b>quyền sở hữu</b>: thông báo của người này không được để
 * người khác đọc hay ghim.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationServiceImpl service;
    private User nguoiNhan;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, objectMapper, eventPublisher);

        nguoiNhan = new User();
        nguoiNhan.setId(UUID.randomUUID());
        nguoiNhan.setFullName("Khách");

        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getId() == null) {
                n.setId(UUID.randomUUID());
            }
            return n;
        });
    }

    private Notification thongBao(User cua) {
        Notification n = Notification.builder()
                .id(UUID.randomUUID())
                .user(cua)
                .type("BOOKING_CREATED")
                .title("Có lịch hẹn mới")
                .message("Nội dung")
                .read(false)
                .pinned(false)
                .build();
        lenient().when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));
        return n;
    }

    // =====================================================================

    @Test
    @DisplayName("Đẩy thông báo: lưu, đếm chưa đọc, và phát sự kiện cho realtime")
    void dayThongBao() {
        when(notificationRepository.countByUserIdAndReadFalse(nguoiNhan.getId())).thenReturn(3L);

        service.push(nguoiNhan, "BOOKING_CREATED", "Có lịch hẹn mới", "Nội dung",
                Map.of("bookingId", "abc"));

        ArgumentCaptor<NotificationPushedEvent> bat =
                ArgumentCaptor.forClass(NotificationPushedEvent.class);
        verify(eventPublisher).publishEvent(bat.capture());
        assertAll(
                () -> assertEquals(nguoiNhan.getId(), bat.getValue().userId()),
                () -> assertEquals("BOOKING_CREATED", bat.getValue().type()),
                // Số chưa đọc đi kèm sự kiện để chấm đỏ trên chuông cập nhật
                // ngay, không phải chờ lần tải trang sau.
                () -> assertEquals(3L, bat.getValue().unreadCount()));
    }

    @Test
    @DisplayName("Metadata được ghi thành JSON")
    void metadataThanhJson() {
        service.push(nguoiNhan, "X", "T", "M", Map.of("bookingId", "abc-123"));

        ArgumentCaptor<Notification> bat = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(bat.capture());
        assertTrue(bat.getValue().getMetadata().contains("abc-123"));
    }

    @Test
    @DisplayName("Metadata rỗng hoặc null thì lưu null, không lưu chuỗi {}")
    void metadataRong() {
        service.push(nguoiNhan, "X", "T", "M", Map.of());
        service.push(nguoiNhan, "X", "T", "M", null);

        ArgumentCaptor<Notification> bat = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(2)).save(bat.capture());
        // Giao diện kiểm "có metadata không" để quyết định có cho bấm vào thông
        // báo hay không; chuỗi "{}" là có, mà bấm vào thì chẳng đi đâu cả.
        bat.getAllValues().forEach(n -> assertNull(n.getMetadata()));
    }

    @Test
    @DisplayName("Người nhận null thì bỏ qua lặng lẽ, không nổ")
    void nguoiNhanNull() {
        service.push(null, "X", "T", "M", Map.of());

        // Nhiều luồng gọi push cho "bên kia" của một giao dịch, và bên kia có
        // thể không tồn tại (tài khoản đã xoá). Nổ ở đây là kéo đổ cả giao dịch.
        verify(notificationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Ghi thông báo LỖI thì nuốt, không kéo đổ việc chính")
    void ghiLoiThiNuot() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("mất kết nối DB"));

        // Cố ý nuốt: thông báo là việc phụ đi kèm một việc chính — thanh toán,
        // đặt lịch, huỷ. Để lỗi ghi thông báo kéo đổ giao dịch thanh toán là
        // đổi một việc nhỏ hỏng lấy một việc lớn hỏng.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> service.push(nguoiNhan, "X", "T", "M", Map.of()));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("Danh sách: đã ghim lên trước, kèm đủ trường")
    void danhSach() {
        Notification ghim = thongBao(nguoiNhan);
        ghim.setPinned(true);
        ghim.setMetadata("{\"bookingId\":\"x\"}");
        when(notificationRepository.findByUserIdOrderByPinnedDescCreatedAtDesc(
                org.mockito.ArgumentMatchers.eq(nguoiNhan.getId()), any()))
                .thenReturn(new PageImpl<>(List.of(ghim)));

        var trang = service.list(nguoiNhan.getId(), PageRequest.of(0, 20));
        var tb = trang.getContent().get(0);

        assertAll(
                () -> assertEquals("Có lịch hẹn mới", tb.getTitle()),
                () -> assertEquals("BOOKING_CREATED", tb.getType()),
                () -> assertTrue(tb.getPinned()),
                () -> assertFalse(tb.getRead()),
                () -> assertEquals("{\"bookingId\":\"x\"}", tb.getMetadata()));
    }

    @Test
    @DisplayName("Đếm chưa đọc")
    void demChuaDoc() {
        when(notificationRepository.countByUserIdAndReadFalse(nguoiNhan.getId())).thenReturn(7L);

        assertEquals(7L, service.unreadCount(nguoiNhan.getId()));
    }

    @Test
    @DisplayName("Đánh dấu đã đọc")
    void danhDauDaDoc() {
        Notification n = thongBao(nguoiNhan);

        service.markRead(nguoiNhan.getId(), n.getId());

        assertTrue(n.getRead());
    }

    @Test
    @DisplayName("KHÔNG đọc được thông báo của người khác")
    void thongBaoCuaNguoiKhac() {
        Notification n = thongBao(nguoiNhan);
        UUID nguoiLa = UUID.randomUUID();

        // Thông báo mang tên người khác, số tiền, nội dung buổi xem. Lọt sang
        // người lạ là rò dữ liệu cá nhân.
        assertAll(
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.markRead(nguoiLa, n.getId())),
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.setPinned(nguoiLa, n.getId(), true)));
        assertFalse(n.getRead());
    }

    @Test
    @DisplayName("Thông báo không tồn tại thì báo đúng loại lỗi")
    void thongBaoKhongTonTai() {
        UUID la = UUID.randomUUID();
        when(notificationRepository.findById(la)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markRead(nguoiNhan.getId(), la));
    }

    @Test
    @DisplayName("Ghim và bỏ ghim")
    void ghimVaBoGhim() {
        Notification n = thongBao(nguoiNhan);

        service.setPinned(nguoiNhan.getId(), n.getId(), true);
        assertTrue(n.getPinned());

        service.setPinned(nguoiNhan.getId(), n.getId(), false);
        assertFalse(n.getPinned());
    }

    @Test
    @DisplayName("Đọc hết và xoá hết đã đọc trả về số dòng thật")
    void docHetVaXoaHet() {
        when(notificationRepository.markAllRead(nguoiNhan.getId())).thenReturn(5);
        when(notificationRepository.deleteAllRead(nguoiNhan.getId())).thenReturn(4);

        assertAll(
                () -> assertEquals(5, service.markAllRead(nguoiNhan.getId())),
                () -> assertEquals(4, service.deleteAllRead(nguoiNhan.getId())));
    }

    @Test
    @DisplayName("Xoá theo danh sách rỗng thì trả 0, KHÔNG gọi xuống DB")
    void xoaDanhSachRong() {
        assertAll(
                () -> assertEquals(0, service.deleteByIds(nguoiNhan.getId(), List.of())),
                () -> assertEquals(0, service.deleteByIds(nguoiNhan.getId(), null)));

        // "DELETE ... WHERE id IN ()" là lỗi cú pháp ở Postgres, và Hibernate
        // không phải lúc nào cũng chặn trước.
        verify(notificationRepository, never()).deleteByIds(any(), any());
    }

    @Test
    @DisplayName("Xoá theo danh sách có chọn thì truyền xuống cả userId")
    void xoaDanhSachCoChon() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(notificationRepository.deleteByIds(nguoiNhan.getId(), ids)).thenReturn(2);

        // userId phải đi kèm trong câu lệnh xoá, nếu không thì gửi lên id của
        // người khác là xoá được thông báo của họ.
        assertEquals(2, service.deleteByIds(nguoiNhan.getId(), ids));
    }
}
