package com.exe.astratarot.config;

import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.domain.enums.PaymentPhase;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.service.BookingService;
import com.exe.astratarot.service.PaymentService;
import com.exe.astratarot.service.BookingService.ActorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingPaymentSchedulerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingPaymentScheduler scheduler;

    @Test
    @DisplayName("không còn job quét hạn để tự huỷ buổi chỉ vì chưa trả nốt")
    void khongConQuetHan() {
        // Phần còn lại được nhắc khi Reader đánh dấu xong, không huỷ trước buổi.
        verifyNoInteractions(bookingRepository, paymentService, bookingService);
    }

    @Test
    @DisplayName("handleOverdueBooking calls bookingService.cancel with SYSTEM actor")
    void handleOverdueBooking_CallsCancelWithSystemActor() {
        UUID bookingId = UUID.randomUUID();

        when(bookingService.cancel(eq(null), eq(bookingId), any(String.class), eq(ActorType.SYSTEM))).thenReturn(null);

        scheduler.handleOverdueBooking(bookingId);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(bookingService).cancel(eq(null), eq(bookingId), reasonCaptor.capture(), eq(ActorType.SYSTEM));
        assertThat(reasonCaptor.getValue()).contains("Quá hạn");
    }
}
