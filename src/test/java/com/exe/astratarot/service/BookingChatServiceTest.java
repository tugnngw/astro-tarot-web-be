package com.exe.astratarot.service;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.BookingMessage;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        lenient().when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        lenient().when(userRepository.findById(readerUserId)).thenReturn(Optional.of(readerUser));

        // Hai stub dưới đây mô phỏng ĐÚNG chỗ Hibernate khác nhau, vì chính
        // khoảng khác ấy là lỗi đã gặp trên production.
        //
        // save(): id là @GeneratedValue(UUID) nên sinh được trong bộ nhớ, câu
        // INSERT bị hoãn tới cuối giao dịch, và @CreationTimestamp chưa gán —
        // createdAt còn null. Trả về nguyên vật thể, không đụng gì.
        lenient().when(messageRepository.save(any(BookingMessage.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // saveAndFlush(): INSERT chạy ngay, nên createdAt có giá trị.
        lenient().when(messageRepository.saveAndFlush(any(BookingMessage.class)))
                .thenAnswer(inv -> {
                    BookingMessage m = inv.getArgument(0);
                    m.setCreatedAt(Instant.now());
                    return m;
                });
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
    @DisplayName("MỚI ĐẶT CỌC đã mở, không đợi trả đủ")
    void datCocLaMo() {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);

        // Từ khi có đặt cọc 50%, một người vừa chuyển tiền thật vẫn ở
        // DEPOSIT_PAID cho tới sát giờ hẹn. Đòi PAID nghĩa là suốt quãng ấy
        // họ không hỏi Reader được câu nào — đúng quãng cần hỏi nhất.
        assertTrue(service.chatOpen(booking));
        assertDoesNotThrow(() -> service.send(bookingId, customerId, "xin chào"));
    }

    @Test
    @DisplayName("Reader CHƯA nhận lịch nhưng đã có tiền thì vẫn mở")
    void chuaNhanLichNhungDaCoTienThiVanMo() {
        booking.setStatus(BookingStatus.PENDING);
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);

        // Reader im lâu thì nhắn được một câu chính là thứ gỡ tình huống đó,
        // chứ không phải ngồi chờ rồi huỷ.
        assertTrue(service.chatOpen(booking));
    }

    @Test
    @DisplayName("Đã huỷ thì đóng dù đã đặt cọc")
    void daHuyThiDongDuDaDatCoc() {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);

        // Tiền đã vào không mở lại một buổi đã huỷ. Huỷ xong còn nhắn được là
        // một đường vòng để làm phiền nhau sau khi quan hệ đã chấm dứt.
        assertFalse(service.chatOpen(booking));
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

    // ---------- Tin vừa gửi phải dùng được ngay ----------

    @Test
    @DisplayName("Tin vừa gửi đã có thời gian, không chờ tải lại trang")
    void tinVuaGuiPhaiCoThoiGian() {
        var dto = service.send(bookingId, customerId, "xin chào");

        // Gói này đẩy thẳng sang trình duyệt hai bên. Thiếu createdAt thì giao
        // diện gọi new Date(null) và vẽ ra mốc 1970 — trên production hiện
        // "08:00 01-01", rồi tự lành khi F5 vì lúc đó đọc lại từ bảng. Lỗi tự
        // lành là lỗi khó thấy nhất, nên chặn ngay ở đây.
        assertNotNull(dto.getCreatedAt(),
                "Tin nhắn đẩy đi mà không có thời gian. Rất có thể save() đã "
                        + "thay chỗ saveAndFlush(): INSERT bị hoãn nên "
                        + "@CreationTimestamp chưa kịp gán.");
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
