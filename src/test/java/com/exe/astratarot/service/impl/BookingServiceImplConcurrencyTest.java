package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.dto.booking.BookingResponse;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.repository.BookingRepository;
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
                .startTime(Instant.now().minusSeconds(3600))
                .endTime(Instant.now().minusSeconds(1800))
                .totalAmount(100000L)
                .build();
    }

    @Test
    @DisplayName("complete() calls bookingRepository.findByIdForUpdate() to acquire pessimistic lock")
    void complete_UsesFindByIdForUpdate() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.complete(readerUserId, bookingId);

        assertEquals(BookingStatus.COMPLETED.name(), response.getStatus());
        verify(bookingRepository).findByIdForUpdate(bookingId);
        verify(escrowService).releaseForBooking(booking);
    }

    @Test
    @DisplayName("cancel() calls bookingRepository.findByIdForUpdate() to acquire pessimistic lock")
    void cancel_UsesFindByIdForUpdate() {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.cancel(customerId, bookingId, "Need to cancel");

        assertEquals(BookingStatus.CANCELLED.name(), response.getStatus());
        verify(bookingRepository).findByIdForUpdate(bookingId);
    }

    @Test
    @DisplayName("If status is already COMPLETED during concurrent execution, cancel() throws IllegalArgumentException")
    void cancel_AlreadyCompleted_ThrowsException() {
        booking.setStatus(BookingStatus.COMPLETED);

        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThrows(IllegalArgumentException.class, () -> bookingService.cancel(customerId, bookingId, "Cancel"));
    }
}
