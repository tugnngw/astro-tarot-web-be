package com.exe.astratarot.service.impl;

import com.exe.astratarot.config.PayOsConfig.PayOsClient;
import com.exe.astratarot.domain.dto.payment.PaymentTransactionResponse;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.PaymentTransaction;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentPhase;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.repository.PaymentTransactionRepository;
import com.exe.astratarot.service.ActivityLogService;
import com.exe.astratarot.service.EscrowService;
import com.exe.astratarot.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.payos.PayOS;
import vn.payos.model.webhooks.WebhookData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private EscrowService escrowService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ActivityLogService activityLogService;

    @Mock
    private PayOsClient payOsClient;

    @Mock
    private PayOS payOS;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private UUID customerId;
    private UUID readerUserId;
    private UUID bookingId;
    private UUID transactionId;
    private User customer;
    private User readerUser;
    private ReaderProfile readerProfile;
    private Booking booking;
    private PaymentTransaction transaction;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        readerUserId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        transactionId = UUID.randomUUID();

        customer = User.builder().id(customerId).username("customer").fullName("Customer").build();
        readerUser = User.builder().id(readerUserId).username("reader").fullName("Reader").build();
        readerProfile = ReaderProfile.builder().id(UUID.randomUUID()).user(readerUser).build();

        booking = Booking.builder()
                .id(bookingId)
                .user(customer)
                .readerProfile(readerProfile)
                .status(BookingStatus.CANCELLED)
                .paymentStatus(PaymentStatus.UNPAID)
                .totalAmount(100000L)
                .build();

        transaction = PaymentTransaction.builder()
                .id(transactionId)
                .booking(booking)
                .user(customer)
                .amount(100000L)
                .status(TransactionStatus.PENDING)
                .externalTransactionId("123456")
                .build();
    }

    @Test
    @DisplayName("handlePayOsWebhook confirms payment and holds escrow")
    void handlePayOsWebhook_CancelledBooking_TriggersImmediateRefund() {
        WebhookData webhookData = mock(WebhookData.class);
        when(webhookData.getOrderCode()).thenReturn(123456L);
        when(webhookData.getCode()).thenReturn("00");
        when(webhookData.getAmount()).thenReturn(100000L);

        when(payOsClient.enabled()).thenReturn(true);
        when(payOsClient.sdk()).thenReturn(payOS);

        vn.payos.service.blocking.webhooks.WebhooksService webhooksService = mock(vn.payos.service.blocking.webhooks.WebhooksService.class);
        when(payOS.webhooks()).thenReturn(webhooksService);
        when(webhooksService.verify(any())).thenReturn(webhookData);

        when(transactionRepository.findByExternalTransactionId("123456")).thenReturn(Optional.of(transaction));

        paymentService.handlePayOsWebhook("rawBody");

        assertEquals(PaymentStatus.PAID, booking.getPaymentStatus());
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        verify(escrowService).holdForBooking(booking, 100000L);
    }

    @Test
    @DisplayName("confirm on CANCELLED booking sets payment to PAID and holds escrow")
    void confirm_CancelledBooking_PreventsEscrowFreeze() {
        UUID adminId = UUID.randomUUID();

        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        PaymentTransactionResponse response = paymentService.confirm(adminId, transactionId);

        assertEquals(PaymentStatus.PAID, booking.getPaymentStatus());
        verify(escrowService).holdForBooking(booking, 100000L);
        verify(escrowService, never()).refundForBooking(any(), anyLong());
        verify(activityLogService).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("confirm with DEPOSIT phase sets booking to DEPOSIT_PAID")
    void confirm_DepositPhase_SetsDepositPaid() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        transaction.setPhase(PaymentPhase.DEPOSIT);
        transaction.setAmount(50000L);
        booking.setPaymentStatus(PaymentStatus.UNPAID);

        paymentService.confirm(null, transactionId);

        assertEquals(PaymentStatus.DEPOSIT_PAID, booking.getPaymentStatus());
        verify(escrowService).holdForBooking(booking, 50000L);
        verify(escrowService, never()).releaseForBooking(any());
    }

    @Test
    @DisplayName("confirm with FULL phase sets booking to PAID and holds escrow")
    void confirm_FullPhase_SetsPaidAndReleases() {
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(transaction));

        transaction.setPhase(PaymentPhase.FULL);
        transaction.setAmount(100000L);
        booking.setPaymentStatus(PaymentStatus.DEPOSIT_PAID);

        paymentService.confirm(null, transactionId);

        assertEquals(PaymentStatus.PAID, booking.getPaymentStatus());
        verify(escrowService).holdForBooking(booking, 100000L);
        verify(escrowService, never()).releaseForBooking(any());
    }
}

