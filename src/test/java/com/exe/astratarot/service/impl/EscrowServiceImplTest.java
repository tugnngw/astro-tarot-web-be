package com.exe.astratarot.service.impl;

import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.EscrowAccount;
import com.exe.astratarot.domain.entity.EscrowTransaction;
import com.exe.astratarot.domain.entity.EscrowTransaction.Kind;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.repository.EscrowAccountRepository;
import com.exe.astratarot.repository.EscrowTransactionRepository;
import com.exe.astratarot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscrowServiceImplTest {

    @Mock
    private EscrowAccountRepository escrowAccountRepository;

    @Mock
    private EscrowTransactionRepository escrowTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EscrowServiceImpl escrowService;

    private UUID readerUserId;
    private UUID bookingId;
    private User readerUser;
    private ReaderProfile readerProfile;
    private Booking booking;
    private EscrowAccount escrowAccount;

    @BeforeEach
    void setUp() {
        readerUserId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        readerUser = User.builder().id(readerUserId).username("reader").build();
        readerProfile = ReaderProfile.builder().id(UUID.randomUUID()).user(readerUser).build();

        booking = Booking.builder()
                .id(bookingId)
                .readerProfile(readerProfile)
                .totalAmount(100000L)
                .build();

        escrowAccount = EscrowAccount.builder()
                .id(UUID.randomUUID())
                .user(readerUser)
                .balance(0L)
                .pendingBalance(100000L)
                .totalEarned(0L)
                .build();
    }

    @Test
    @DisplayName("First call to releaseForBooking processes release and updates balance")
    void releaseForBooking_FirstCall_ProcessesRelease() {
        when(escrowTransactionRepository.existsByBookingIdAndKind(bookingId, Kind.RELEASE)).thenReturn(false);
        when(escrowAccountRepository.findByUserId(readerUserId)).thenReturn(Optional.of(escrowAccount));

        escrowService.releaseForBooking(booking);

        // 100,000 gross - 15% platform fee (15,000) = 85,000 net
        assertEquals(0L, escrowAccount.getPendingBalance());
        assertEquals(85000L, escrowAccount.getBalance());
        assertEquals(85000L, escrowAccount.getTotalEarned());

        verify(escrowAccountRepository).save(escrowAccount);
        verify(escrowTransactionRepository).save(any(EscrowTransaction.class));
    }

    @Test
    @DisplayName("First call to refundForBooking processes refund and updates pending balance")
    void refundForBooking_FirstCall_ProcessesRefund() {
        when(escrowTransactionRepository.existsByBookingIdAndKind(bookingId, Kind.REFUND)).thenReturn(false);
        when(escrowAccountRepository.findByUserId(readerUserId)).thenReturn(Optional.of(escrowAccount));

        escrowService.refundForBooking(booking);

        assertEquals(0L, escrowAccount.getPendingBalance());
        verify(escrowAccountRepository).save(escrowAccount);
        verify(escrowTransactionRepository).save(any(EscrowTransaction.class));
    }

    @Test
    @DisplayName("Second call to refundForBooking with existing REFUND transaction is ignored idempotently")
    void refundForBooking_SecondCall_IdempotentSkip() {
        when(escrowTransactionRepository.existsByBookingIdAndKind(bookingId, Kind.REFUND)).thenReturn(true);

        escrowService.refundForBooking(booking);

        assertEquals(100000L, escrowAccount.getPendingBalance());
        verify(escrowAccountRepository, never()).findByUserId(any());
        verify(escrowAccountRepository, never()).save(any());
        verify(escrowTransactionRepository, never()).save(any());
    }
}
