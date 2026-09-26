package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.booking.BookingResponse;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.service.BookingService.ActorType;
import com.exe.astratarot.repository.NotificationRepository;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplConcurrencyTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private EscrowService escrowService;

    @Mock
    private com.exe.astratarot.service.PaymentService paymentService;

    @Mock
    private NotificationService notificationService;

    /**
     * Không phải mock thừa: BookingResponse mang cờ {@code chatOpen}, và
     * BookingServiceImpl hỏi BookingChatService để tính cờ đó mỗi lần dựng DTO.
     *
     * <p>Thiếu khai báo ở đây thì @InjectMocks để nguyên trường null, và MỌI
     * test nào đi tới bước dựng DTO đều ném NullPointerException — đúng thứ đã
     * làm CI đỏ. Lỗi khó đoán vì câu báo nói về chat trong một bộ test tên là
     * "Concurrency", chẳng liên quan gì tới khoá bi quan đang được kiểm.
     */
    @Mock
    private com.exe.astratarot.service.BookingChatService bookingChatService;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private UUID readerUserId;
    private UUID customerId;
    private UUID bookingId;
    private User readerUser;
    private User customerUser;
    private ReaderProfile readerProfile;
    private Booking booking;

    @BeforeEach
    void setUp() {
        readerUserId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        readerUser = User.builder().id(readerUserId).username("reader").fullName("Reader Name").build();
        customerUser = User.builder().id(customerId).username("customer").fullName("Customer Name").build();
        readerProfile = ReaderProfile.builder().id(UUID.randomUUID()).user(readerUser).build();

        booking = Booking.builder()
                .id(bookingId)
                .user(customerUser)
                .readerProfile(readerProfile)
                .status(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .depositAmount(50000L)
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now().minusSeconds(1800))
                .totalAmount(100000L)
                .build();
    }

    @Test
    @DisplayName("complete() calls bookingRepository.findByIdForUpdate() to acquire pessimistic lock")
    void complete_UsesFindByIdForUpdate() {
        booking.setStartTime(Instant.now().minusSeconds(7300)); // Past start time (12+ hours ago)
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        when(bookingChatService.chatOpen(booking)).thenReturn(true);

        BookingResponse response = bookingService.complete(readerUserId, bookingId);

        assertEquals(BookingStatus.COMPLETED.name(), response.getStatus());
        assertEquals(Boolean.TRUE, response.getChatOpen());
        verify(bookingRepository).findByIdForUpdate(bookingId);
        verify(escrowService).releaseForBooking(booking);
    }

    @Test
    @DisplayName("cancel() calls bookingRepository.findByIdForUpdate() to acquire pessimistic lock")
    void cancel_UsesFindByIdForUpdate() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        when(bookingChatService.chatOpen(booking)).thenReturn(false);

        BookingResponse response = bookingService.cancel(customerId, bookingId, "Need to cancel", ActorType.USER);

        assertEquals(BookingStatus.CANCELLED.name(), response.getStatus());
        assertEquals(Boolean.FALSE, response.getChatOpen());
        verify(bookingRepository).findByIdForUpdate(bookingId);
    }

    @Test
    @DisplayName("If status is already COMPLETED during concurrent execution, cancel() throws IllegalArgumentException")
    void cancel_AlreadyCompleted_ThrowsException() {
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(IllegalArgumentException.class, () -> bookingService.cancel(customerId, bookingId, "Cancel", ActorType.USER));
    }

    @Test
    @DisplayName("cancel with PAID and early (≥12h before start) refunds full amount")
    void cancel_DepositPaid_WithinDeadline_RefundsDeposit() {
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setDepositAmount(50000L);
        booking.setTotalAmount(100000L);
        booking.setForfeitedAmount(50000L);
        booking.setStartTime(Instant.now().plusSeconds(43201)); // 12h+1s in future → early cancel
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        bookingService.cancel(customerId, bookingId, "Cancel", ActorType.USER);

        // PAID status → getAmountPaid() returns totalAmount = 100000, refund full amount
        verify(paymentService).refund(booking, 100000L);
        verify(paymentService, never()).forfeitDeposit(booking);
    }

    @Test
    @DisplayName("cancel with DEPOSIT_PAID and past T-12h forfeits deposit")
    void cancel_DepositPaid_PastDeadline_ForfeitsDeposit() {
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);
        booking.setDepositAmount(50000L);
        booking.setForfeitedAmount(50000L);
        booking.setPaymentDeadline(Instant.now().minusSeconds(3600)); // past deadline
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        bookingService.cancel(customerId, bookingId, "Cancel", ActorType.USER);

        verify(paymentService).forfeitDeposit(booking);
        verify(paymentService).refund(booking, 0L);
    }

    @Test
    @DisplayName("quá hạn trả nốt: job hệ thống huỷ và mất cọc dù không có người bấm")
    void cancel_QuaHan_HeThongMatCoc() {
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);
        booking.setDepositAmount(50000L);
        booking.setStartTime(Instant.now().plusSeconds(3600));
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingChatService.chatOpen(booking)).thenReturn(false);

        bookingService.cancel(null, bookingId, "Quá hạn thanh toán. Tự động hủy.", ActorType.SYSTEM);

        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        verify(paymentService).forfeitDeposit(booking);
        verify(paymentService, never()).refund(any(), anyLong());
    }

    @Test
    @DisplayName("Reader huỷ sát giờ thì hoàn hết, không lấy cọc của khách")
    void cancel_ReaderHuyMuon_HoanHet() {
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);
        booking.setDepositAmount(50000L);
        booking.setStartTime(Instant.now().plusSeconds(3600));
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(bookingChatService.chatOpen(booking)).thenReturn(false);

        bookingService.cancel(readerUserId, bookingId, "Bận", ActorType.USER);

        verify(paymentService).refund(booking, 50000L);
        verify(paymentService, never()).forfeitDeposit(booking);
    }
}
