package com.exe.astratarot.service;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.repository.BookingMessageRepository;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * Hai thứ bộ test này giữ, và chỉ hai thứ đó.
 *
 * <p><b>Ai đọc được hội thoại.</b> Đây là chỗ hỏng thì tệ nhất: lọt là người
 * lạ đọc được buổi tư vấn riêng tư của người khác. Kiểm tra cả hai chiều —
 * đúng người thì vào được, sai người thì bị chặn.
 *
 * <p><b>Khi nào được gửi.</b> Không phải luật kỹ thuật mà là luật doanh thu:
 * chưa trả tiền thì chưa nhắn. Ai đó sau này "nới cho tiện" sẽ làm bộ test
 * này đỏ, và đó là mục đích.
 */
@ExtendWith(MockitoExtension.class)
class BookingChatServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingMessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private BookingChatService service;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID readerUserId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();

    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new BookingChatService(
                bookingRepository, messageRepository, userRepository, messagingTemplate);
        ReflectionTestUtils.setField(service, "graceDays", 7L);

        User customer = new User();
        customer.setId(customerId);
        customer.setFullName("Khách");

        User readerUser = new User();
        readerUser.setId(readerUserId);
        readerUser.setFullName("Reader");

        ReaderProfile profile = new ReaderProfile();
        profile.setUser(readerUser);

        booking = new Booking();
        booking.setId(bookingId);
        booking.setUser(customer);
        booking.setReaderProfile(profile);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setEndTime(Instant.now().minus(Duration.ofHours(1)));

        lenient().when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
    }

    // ---------- Ai được vào ----------

    @Test
    @DisplayName("Khách và Reader đều vào được, và mỗi người thấy đúng phía bên kia")
    void haiNguoiTrongCuocDeuVaoDuoc() {
        var asCustomer = service.participants(bookingId, customerId);
        assertEquals(readerUserId, asCustomer.otherUserId());

        var asReader = service.participants(bookingId, readerUserId);
        assertEquals(customerId, asReader.otherUserId());
    }

    @Test
    @DisplayName("Người thứ ba bị chặn")
    void nguoiLaBiChan() {
        assertThrows(AccessDeniedException.class,
                () -> service.participants(bookingId, strangerId));
    }

    @Test
    @DisplayName("Người lạ không gửi được tin, dù hội thoại đang mở")
    void nguoiLaKhongGuiDuoc() {
        assertThrows(AccessDeniedException.class,
                () -> service.send(bookingId, strangerId, "xin chào"));
    }

    @Test
    @DisplayName("Người lạ không chen được vào cuộc gọi")
    void nguoiLaKhongChenDuocVaoCuocGoi() {
        assertThrows(AccessDeniedException.class,
                () -> service.relaySignal(bookingId, strangerId, null));
    }

    // ---------- Khi nào được gửi ----------

    @Test
    @DisplayName("Chưa trả tiền thì đóng")
    void chuaTraTienThiDong() {
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        assertFalse(service.chatOpen(booking));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.send(bookingId, customerId, "xin chào"));
        assertTrue(e.getMessage().contains("thanh toán"),
                "Câu báo lỗi phải nói rõ vì sao, người dùng mới biết làm gì tiếp: " + e.getMessage());
    }

    @Test
    @DisplayName("Đã huỷ thì đóng")
    void daHuyThiDong() {
        booking.setStatus(BookingStatus.CANCELLED);
        assertFalse(service.chatOpen(booking));
    }

    @Test
    @DisplayName("Mới đặt mà chưa trả tiền thì đóng")
    void dangChoThanhToanThiDong() {
        booking.setStatus(BookingStatus.PENDING);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        assertFalse(service.chatOpen(booking));
    }

    @Test
    @DisplayName("Đã xong và còn trong hạn ân hạn thì vẫn mở")
    void conTrongHanAnHanThiVanMo() {
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setEndTime(Instant.now().minus(Duration.ofDays(3)));
        assertTrue(service.chatOpen(booking));
    }

    @Test
    @DisplayName("Quá hạn ân hạn thì đóng")
    void quaHanAnHanThiDong() {
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setEndTime(Instant.now().minus(Duration.ofDays(8)));
        assertFalse(service.chatOpen(booking));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.send(bookingId, customerId, "xin chào"));
        assertTrue(e.getMessage().contains("đóng"), e.getMessage());
    }

    @Test
    @DisplayName("Hội thoại đóng vẫn ĐỌC LẠI được lịch sử")
    void dongRoiVanDocLaiDuoc() {
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setEndTime(Instant.now().minus(Duration.ofDays(30)));

        // Người dùng đã trả tiền cho buổi này; đóng hội thoại là cấm gửi thêm,
        // không phải tịch thu thứ họ đã mua.
        assertDoesNotThrow(() -> service.participants(bookingId, customerId));
    }
}
