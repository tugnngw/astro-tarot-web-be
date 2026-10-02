package com.exe.astratarot.service;

import com.exe.astratarot.config.PayOsConfig.PayOsClient;
import com.exe.astratarot.config.PayOsProperties;
import com.exe.astratarot.domain.dto.payment.PaymentInstructionResponse;
import com.exe.astratarot.domain.dto.wallet.UserWalletResponse;
import com.exe.astratarot.domain.entity.Booking;
import com.exe.astratarot.domain.entity.PaymentTransaction;
import com.exe.astratarot.domain.entity.ReaderProfile;
import com.exe.astratarot.domain.entity.User;
import com.exe.astratarot.domain.entity.UserWallet;
import com.exe.astratarot.domain.entity.WalletTransaction;
import com.exe.astratarot.domain.enums.BookingStatus;
import com.exe.astratarot.domain.enums.PaymentStatus;
import com.exe.astratarot.domain.enums.TransactionStatus;
import com.exe.astratarot.domain.enums.WalletTransactionType;
import com.exe.astratarot.repository.BookingRepository;
import com.exe.astratarot.repository.PaymentTransactionRepository;
import com.exe.astratarot.repository.UserRepository;
import com.exe.astratarot.repository.UserWalletRepository;
import com.exe.astratarot.repository.WalletTransactionRepository;
import com.exe.astratarot.service.impl.WalletServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private UserWalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository walletTxRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private EscrowService escrowService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PayOsClient payOsClient;
    @Mock
    private PayOsProperties payOsProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WalletServiceImpl walletService;

    private User testUser;
    private UserWallet testWallet;

    @BeforeEach
    void setUp() {
        walletService = new WalletServiceImpl(
                walletRepository,
                walletTxRepository,
                userRepository,
                bookingRepository,
                paymentTransactionRepository,
                escrowService,
                notificationService,
                payOsClient,
                payOsProperties,
                objectMapper
        );

        testUser = User.builder()
                .id(UUID.randomUUID())
                .username("testuser")
                .fullName("Test User")
                .email("test@example.com")
                .build();

        testWallet = UserWallet.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .balance(50000L)
                .build();
    }

    @Test
    @DisplayName("getOrCreateWallet - Trả về ví hiện có của người dùng")
    void getOrCreateWallet_ExistingWallet_ReturnsWallet() {
        when(walletRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testWallet));

        UserWallet wallet = walletService.getOrCreateWallet(testUser.getId());

        assertThat(wallet).isNotNull();
        assertThat(wallet.getBalance()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("getWalletResponse - Trả về DTO ví thành công")
    void getWalletResponse_ReturnsDto() {
        when(walletRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(testWallet));

        UserWalletResponse response = walletService.getWalletResponse(testUser.getId());

        assertThat(response).isNotNull();
        assertThat(response.getBalance()).isEqualTo(50000L);
        assertThat(response.getUserId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("credit - Cộng tiền vào ví thành công và ghi nhận transaction")
    void credit_ValidAmount_IncreasesBalanceAndSavesTx() {
        when(walletRepository.findByUserIdWithLock(testUser.getId())).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(UserWallet.class))).thenAnswer(i -> i.getArgument(0));
        when(walletTxRepository.save(any(WalletTransaction.class))).thenAnswer(i -> i.getArgument(0));

        WalletTransaction tx = walletService.credit(
                testUser, 100000L, WalletTransactionType.TOPUP,
                "ORDER123", "Nạp tiền vào ví", "PAYOS");

        assertThat(tx).isNotNull();
        assertThat(tx.getAmount()).isEqualTo(100000L);
        assertThat(tx.getBalanceBefore()).isEqualTo(50000L);
        assertThat(tx.getBalanceAfter()).isEqualTo(150000L);
        assertThat(testWallet.getBalance()).isEqualTo(150000L);
        assertThat(tx.getType()).isEqualTo(WalletTransactionType.TOPUP);
    }

    @Test
    @DisplayName("credit - Số tiền âm hoặc 0 sẽ ném ngoại lệ")
    void credit_InvalidAmount_ThrowsException() {
        assertThatThrownBy(() -> walletService.credit(
                testUser, 0L, WalletTransactionType.TOPUP, "REF1", "Test", "WALLET"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("debit - Trừ tiền thành công khi số dư đủ")
    void debit_SufficientBalance_DecreasesBalance() {
        when(walletRepository.findByUserIdWithLock(testUser.getId())).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(UserWallet.class))).thenAnswer(i -> i.getArgument(0));
        when(walletTxRepository.save(any(WalletTransaction.class))).thenAnswer(i -> i.getArgument(0));

        WalletTransaction tx = walletService.debit(
                testUser, 30000L, WalletTransactionType.AI_SUBSCRIPTION, "PLAN1", "Mua gói AI");

        assertThat(tx).isNotNull();
        assertThat(tx.getAmount()).isEqualTo(30000L);
        assertThat(tx.getBalanceBefore()).isEqualTo(50000L);
        assertThat(tx.getBalanceAfter()).isEqualTo(20000L);
        assertThat(testWallet.getBalance()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("debit - Ném ngoại lệ khi số dư ví không đủ")
    void debit_InsufficientBalance_ThrowsException() {
        when(walletRepository.findByUserIdWithLock(testUser.getId())).thenReturn(Optional.of(testWallet));

        assertThatThrownBy(() -> walletService.debit(
                testUser, 99000L, WalletTransactionType.AI_SUBSCRIPTION, "PLAN1", "Mua gói AI"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("payBookingWithWallet - Thanh toán lịch hẹn bằng ví thành công")
    void payBookingWithWallet_Success() {
        User readerUser = User.builder().id(UUID.randomUUID()).fullName("Reader Name").build();
        ReaderProfile readerProfile = ReaderProfile.builder().id(UUID.randomUUID()).user(readerUser).build();

        Booking booking = Booking.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .readerProfile(readerProfile)
                .totalAmount(40000L)
                .status(BookingStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.UNPAID)
                .build();

        when(bookingRepository.findByIdWithParties(booking.getId())).thenReturn(Optional.of(booking));
        when(walletRepository.findByUserIdWithLock(testUser.getId())).thenReturn(Optional.of(testWallet));
        when(walletRepository.save(any(UserWallet.class))).thenAnswer(i -> i.getArgument(0));
        when(walletTxRepository.save(any(WalletTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        walletService.payBookingWithWallet(testUser.getId(), booking.getId());

        assertThat(testWallet.getBalance()).isEqualTo(10000L);
        assertThat(booking.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        verify(escrowService).holdForBooking(booking, 40000L);
    }
}
